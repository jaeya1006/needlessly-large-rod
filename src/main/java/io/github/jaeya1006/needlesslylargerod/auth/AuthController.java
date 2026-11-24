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

    public record PasswordChange(
            @NotBlank @Size(max = 64) String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword) {
    }

    public record PasswordConfirm(@NotBlank @Size(max = 64) String password) {
    }

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final DataClaimer claimer;
    private final LoginAttemptLimiter limiter;
    private final AccountDeletion deletion;

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
        String email = normalize(c.email());
        String ip = req.getRemoteAddr();
        LoginAttemptLimiter.Attempt attempt = limiter.begin(email, ip); // 넘치면 429, 아니면 실패 1회로 미리 잡아 둔다
        Member m = members.findByEmail(email).orElse(null);
        if (dummyHash == null) dummyHash = passwordEncoder.encode("not-a-real-password");
        boolean ok = passwordEncoder.matches(c.password(), m == null ? dummyHash : m.getPasswordHash());
        if (m == null || !ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요");
        limiter.succeeded(attempt);
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

    /** 비밀번호 변경: 다른 기기의 로그인은 끊고 지금 기기는 새 토큰으로 유지 */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChange body,
                               HttpServletRequest req, HttpServletResponse res) {
        checkPasswordBytes(body.newPassword());
        checkPasswordBytes(body.currentPassword());
        long id = CurrentMember.id(jwt);
        LoginAttemptLimiter.Attempt attempt = limiter.begin("member:" + id, req.getRemoteAddr());
        if (!deletion.changePassword(id, body.currentPassword(), body.newPassword(), req, res)) throw wrongPassword();
        limiter.succeeded(attempt);
    }

    /** 회원 탈퇴: 비밀번호를 다시 확인하고 그 회원의 모든 데이터를 지운다 */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordConfirm body,
                              HttpServletRequest req, HttpServletResponse res) {
        checkPasswordBytes(body.password());
        long id = CurrentMember.id(jwt);
        LoginAttemptLimiter.Attempt attempt = limiter.begin("member:" + id, req.getRemoteAddr());
        if (!deletion.delete(id, body.password())) throw wrongPassword();
        limiter.succeeded(attempt);
        tokens.clearCookies(req, res);
    }

    // 로그인은 유지한 채 비밀번호만 틀림 → 403 (401이면 화면이 로그인 만료로 오해한다)
    private static ResponseStatusException wrongPassword() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 비밀번호가 맞지 않아요");
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return new Me(CurrentMember.id(jwt), jwt.getClaimAsString("email"));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
