package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 쿠키로 로그인하므로, 다른 사이트에서 보낸 POST/PUT/PATCH/DELETE는 막는다(CSRF).
 * 브라우저가 붙이는 Origin 헤더(없으면 Sec-Fetch-Site)로 판단한다. 둘 다 없으면(curl 등) 통과.
 * shortcut: 프록시 뒤에 두면 요청 주소가 바뀔 수 있다. 배포할 때 허용 출처 목록을 설정으로 빼야 한다.
 */
class SameOriginFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!SAFE.contains(req.getMethod()) && crossSite(req)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "다른 사이트에서 보낸 요청은 받지 않아요");
            return;
        }
        chain.doFilter(req, res);
    }

    static boolean crossSite(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null) return !origin.equalsIgnoreCase(ownOrigin(req));
        return "cross-site".equalsIgnoreCase(req.getHeader("Sec-Fetch-Site"));
    }

    private static String ownOrigin(HttpServletRequest req) {
        int port = req.getServerPort();
        boolean defaultPort = ("http".equals(req.getScheme()) && port == 80) || ("https".equals(req.getScheme()) && port == 443);
        return req.getScheme() + "://" + req.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
