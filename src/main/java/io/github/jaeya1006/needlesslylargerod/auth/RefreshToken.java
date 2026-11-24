package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 리프레시 토큰. 원문은 쿠키에만 있고 DB에는 SHA-256 해시만 둔다. 한 번 쓰면 무효화하고 새로 준다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // DB 외래 키(member_id → member.id)만 만들려는 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private Member member;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private boolean revoked;

    private Instant rotatedAt; // 갱신으로 교체된 시각. 로그아웃·탈취 무효화는 비워 둔다(유예 대상 아님)

    public RefreshToken(Long memberId, String tokenHash, Instant expiresAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
