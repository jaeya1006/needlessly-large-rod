package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 회원. 비밀번호는 BCrypt 해시만 저장한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private Instant createdAt;

    // 액세스 토큰의 ver와 같아야 통과. 비밀번호를 바꾸면 올려서 이전 토큰을 바로 막는다(null = 0, 예전 행 대비)
    private Integer tokenVersion;

    public int tokenVersion() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.tokenVersion = tokenVersion() + 1;
    }

    public Member(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }
}
