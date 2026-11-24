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

/** 혜택 추천에 쓰는 사용자 조건. id = 회원 id (회원마다 한 줄). */
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
