package io.github.jaeya1006.needlesslylargerod.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 아직 안 쓴 토큰만 한 번에 무효화한다. 바뀐 행이 1이면 이 요청이 토큰을 '차지'한 것.
     * 동시에 두 요청이 와도 DB가 한쪽만 1을 돌려주므로 둘 다 새 토큰을 받는 일이 없다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true, t.rotatedAt = :now where t.tokenHash = :hash and t.revoked = false")
    int rotate(@Param("hash") String hash, @Param("now") Instant now);

    /** 로그아웃: 교체가 아니라 그냥 무효화(유예 없음) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.tokenHash = :hash and t.revoked = false")
    int revoke(@Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.memberId = :memberId and t.revoked = false")
    int revokeAll(@Param("memberId") Long memberId);
}
