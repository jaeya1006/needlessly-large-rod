package io.github.jaeya1006.needlesslylargerod.auth;

import io.github.jaeya1006.needlesslylargerod.benefit.UserProfile;
import io.github.jaeya1006.needlesslylargerod.benefit.UserProfileRepository;
import io.github.jaeya1006.needlesslylargerod.expense.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 기능 전에 쌓인 데이터(주인 없음)를 처음 가입한 회원에게 넘긴다.
 * - 지출: owner_id가 빈 행
 * - 체크한 분류: 예전 watched_category 테이블 → member_watched_category
 * - 혜택 조건: 예전 한 줄(id=1) → 그 회원 id
 */
@Component
@RequiredArgsConstructor
public class DataClaimer {

    private static final Logger log = LoggerFactory.getLogger(DataClaimer.class);
    static final long LEGACY_PROFILE_ID = 1L;

    private final MemberRepository members;
    private final ExpenseRepository expenses;
    private final UserProfileRepository profiles;
    private final JdbcTemplate jdbc;

    @Transactional
    public void claimIfFirst(long memberId) {
        if (members.count() != 1) return;
        int moved = expenses.claimOwnerless(memberId);
        int watched = copyLegacyWatched(memberId);
        boolean profile = moveLegacyProfile(memberId);
        log.info("첫 회원 {}에게 기존 데이터를 넘겼습니다: 지출 {}건, 체크한 분류 {}개, 혜택 조건 {}", memberId, moved, watched, profile);
    }

    private int copyLegacyWatched(long memberId) {
        try {
            return jdbc.update("INSERT INTO member_watched_category (owner_id, category) SELECT ?, category FROM watched_category", memberId);
        } catch (DataAccessException e) {
            return 0; // 예전 테이블이 없으면(새로 만든 DB) 넘길 것도 없다
        }
    }

    private boolean moveLegacyProfile(long memberId) {
        if (memberId == LEGACY_PROFILE_ID) return profiles.existsById(memberId); // id가 같으면 이미 그 회원 것
        UserProfile old = profiles.findById(LEGACY_PROFILE_ID).orElse(null);
        if (old == null || profiles.existsById(memberId)) return false;
        profiles.save(new UserProfile(memberId, old.getBirthYear(), old.getRegion(), old.getStatus()));
        profiles.delete(old);
        return true;
    }
}
