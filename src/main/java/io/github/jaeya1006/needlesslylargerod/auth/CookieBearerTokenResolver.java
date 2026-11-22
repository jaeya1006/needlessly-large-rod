package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Arrays;

/**
 * 액세스 토큰을 HttpOnly 쿠키에서 꺼낸다(없으면 Authorization 헤더).
 * 로그인이 필요 없는 주소(화면 파일, 가입·로그인·갱신·로그아웃)에서는 꺼내지 않는다.
 * 만료된 쿠키가 남아 있어도 그 주소들이 401로 막히지 않게 하려는 것.
 */
class CookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/") || Arrays.asList(SecurityConfig.PUBLIC_API).contains(path)) return null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (TokenService.ACCESS_COOKIE.equals(c.getName()) && !c.getValue().isBlank()) return c.getValue();
            }
        }
        return header.resolve(request);
    }
}
