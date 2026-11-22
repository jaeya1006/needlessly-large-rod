package io.github.jaeya1006.needlesslylargerod.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** 액세스 토큰의 subject = 회원 id */
public final class CurrentMember {

    private CurrentMember() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
