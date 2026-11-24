package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Member> findFirstByOrderByIdAsc();

    /**
     * 회원 줄을 잠그고 읽는다(SELECT ... FOR UPDATE). 비밀번호 변경·탈퇴·토큰 발급이 같은 회원에 대해
     * 동시에 돌지 않게 한 줄로 세운다. 트랜잭션 안에서만 쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> lockById(@Param("id") Long id);
}
