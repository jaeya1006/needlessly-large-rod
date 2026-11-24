package io.github.jaeya1006.needlesslylargerod.benefit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import io.github.jaeya1006.needlesslylargerod.auth.Member;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 설정 한 줄(id = 회원 id): 혜택 추천 조건(출생연도·시도·상태)과 큰 지출 기준 금액.
 * 회원 탈퇴 때 이 줄이 통째로 지워진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    private Long id;

    // DB 외래 키(id → member.id)만 만들려는 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", insertable = false, updatable = false)
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    private Member member;

    private Integer birthYear;

    private String region; // 시·도, 예: 서울

    private String status; // 학생, 직장인, 구직자 ...

    private Long bigExpenseThreshold; // 이 금액 이상이면 '큰 지출'. null이면 기본값

    public static final long DEFAULT_BIG_EXPENSE = 50_000;

    public long bigExpenseThreshold() {
        return bigExpenseThreshold == null ? DEFAULT_BIG_EXPENSE : bigExpenseThreshold;
    }

    public void changeBigExpenseThreshold(long amount) {
        this.bigExpenseThreshold = amount;
    }

    public UserProfile(Long memberId, Integer birthYear, String region, String status) {
        this.id = memberId;
        this.birthYear = birthYear;
        this.region = region;
        this.status = status;
    }

    public void update(Integer birthYear, String region, String status) {
        this.birthYear = birthYear;
        this.region = region;
        this.status = status;
    }
}
