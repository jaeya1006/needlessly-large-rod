package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** 액세스 토큰(JWT, 30분)과 리프레시 토큰(무작위 문자열, 14일)을 만들고 쿠키에 담는다. */
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final String REFRESH_PATH = "/api/auth"; // 리프레시 쿠키는 갱신·로그아웃 요청에만 실린다

    /** 이 시간 안에 이미 교체된 토큰이 또 오면 탈취가 아니라 다른 탭의 동시 갱신으로 본다 */
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 갱신 결과. GRACE: 다른 탭이 방금 갱신함 → 쿠키를 지우지 말고 다시 시도하게 */
    public enum RefreshResult { OK, GRACE, INVALID }

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final MemberRepository members;

    /** 로그인·가입 성공: 두 토큰을 새로 만들어 쿠키로 보낸다 */
    @Transactional
    public void issue(Member member, HttpServletRequest req, HttpServletResponse res) {
        String access = accessToken(member);
        String refresh = randomToken();
        refreshTokens.save(new RefreshToken(member.getId(), hash(refresh), Instant.now().plus(REFRESH_TTL)));
        setCookies(access, refresh, req, res);
    }

    /**
     * 리프레시 토큰을 쓰면 그 토큰은 무효화하고 새 토큰 두 개를 준다.
     * 이미 무효화된 토큰이 다시 오면 탈취로 보고 그 회원의 리프레시 토큰을 모두 무효화한다.
     */
    @Transactional
    public RefreshResult rotate(String raw, HttpServletRequest req, HttpServletResponse res) {
        if (raw == null || raw.isBlank()) return RefreshResult.INVALID;
        String hash = hash(raw);
        RefreshToken t = refreshTokens.findByTokenHash(hash).orElse(null);
        if (t == null) return RefreshResult.INVALID;
        Instant now = Instant.now();
        if (t.expiredAt(now)) return RefreshResult.INVALID;
        if (refreshTokens.rotate(hash, now) == 0) { // 이미 쓰인 토큰
            RefreshToken used = refreshTokens.findByTokenHash(hash).orElseThrow();
            boolean justRotated = used.getRotatedAt() != null && now.isBefore(used.getRotatedAt().plus(REUSE_GRACE));
            if (justRotated) return RefreshResult.GRACE;
            refreshTokens.revokeAll(t.getMemberId()); // 오래전에 쓴 토큰이 또 옴 → 탈취로 보고 전부 무효화
            return RefreshResult.INVALID;
        }
        Member member = members.findById(t.getMemberId()).orElse(null);
        if (member == null) return RefreshResult.INVALID;
        issue(member, req, res);
        return RefreshResult.OK;
    }

    /** 다른 기기의 로그인을 모두 끊는다(비밀번호 변경 후) */
    @Transactional
    public void revokeAll(long memberId) {
        refreshTokens.revokeAll(memberId);
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) return;
        refreshTokens.revoke(hash(raw));
    }

    public void clearCookies(HttpServletRequest req, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", Duration.ZERO, "Lax", req).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO, "Strict", req).toString());
    }

    String accessToken(Member member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.ISSUER)
                .subject(String.valueOf(member.getId()))
                .claim("email", member.getEmail())
                .claim(SecurityConfig.VERSION_CLAIM, member.tokenVersion())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TTL))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void setCookies(String access, String refresh, HttpServletRequest req, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, access, "/", ACCESS_TTL, "Lax", req).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, refresh, REFRESH_PATH, REFRESH_TTL, "Strict", req).toString());
    }

    // HTTPS로 들어오면 Secure를 붙인다(로컬 http에서도 동작하게)
    private static ResponseCookie cookie(String name, String value, String path, Duration maxAge, String sameSite, HttpServletRequest req) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(req.isSecure())
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        return HexFormat.of().formatHex(SecurityConfig.sha256(raw));
    }
}
