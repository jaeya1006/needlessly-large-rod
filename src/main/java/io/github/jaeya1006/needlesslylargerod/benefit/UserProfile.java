package io.github.jaeya1006.needlesslylargerod.benefit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 혜택 추천에 쓰는 사용자 조건. 로그인이 없어서 한 줄(id=1)만 쓴다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    private Integer birthYear;

    private String region; // 시·도, 예: 서울

    private String status; // 학생, 직장인, 구직자 ...

    public UserProfile(Integer birthYear, String region, String status) {
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
