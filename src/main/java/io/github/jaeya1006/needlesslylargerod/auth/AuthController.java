package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {


    public record Credentials(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8, max = 64) String password) {
    }

    public record Me(long id, String email) {
    }

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final DataClaimer claimer;

    // 없는 이메일로 로그인해도 비밀번호 검사 시간이 같게(가입 여부를 시간으로 알아내지 못하게)
    private volatile String dummyHash;

    /** BCrypt는 72바이트까지만 받는다. 한글은 한 글자에 3바이트라 글자 수 검사만으로는 부족하다. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 너무 길어요(한글은 24자까지)");
        }
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Me signup(@Valid @RequestBody Credentials c, HttpServletRequest req, HttpServletResponse res) {
        checkPasswordBytes(c.password());
        String email = normalize(c.email());
        if (members.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입한 이메일이에요");
        Member m;
        try {
            m = members.saveAndFlush(new Member(email, passwordEncoder.encode(c.password())));
        } catch (DataIntegrityViolationException e) { // 동시에 같은 이메일로 가입한 경우
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입한 이메일이에요");
        }
        claimer.claimIfFirst(m.getId());
        tokens.issue(m, req, res);
        return new Me(m.getId(), m.getEmail());
    }

    @PostMapping("/login")
    public Me login(@Valid @RequestBody Credentials c, HttpServletRequest req, HttpServletResponse res) {
        checkPasswordBytes(c.password());
        Member m = members.findByEmail(normalize(c.email())).orElse(null);
        if (dummyHash == null) dummyHash = passwordEncoder.encode("not-a-real-password");
        boolean ok = passwordEncoder.matches(c.password(), m == null ? dummyHash : m.getPasswordHash());
        if (m == null || !ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요");
        tokens.issue(m, req, res);
        return new Me(m.getId(), m.getEmail());
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh(@CookieValue(name = TokenService.REFRESH_COOKIE, required = false) String refresh,
                      HttpServletRequest req, HttpServletResponse res) {
        switch (tokens.rotate(refresh, req, res)) {
            case OK -> { }
            // 다른 탭이 방금 갱신해서 브라우저에 새 쿠키가 있다. 쿠키를 지우면 그 탭까지 로그아웃되니 그대로 둔다.
            case GRACE -> throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 탭에서 방금 로그인을 갱신했어요");
            case INVALID -> {
                tokens.clearCookies(req, res);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요");
            }
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = TokenService.REFRESH_COOKIE, required = false) String refresh,
                       HttpServletRequest req, HttpServletResponse res) {
        tokens.revoke(refresh);
        tokens.clearCookies(req, res);
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return new Me(CurrentMember.id(jwt), jwt.getClaimAsString("email"));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
