package io.github.jaeya1006.needlesslylargerod.auth;

import io.github.jaeya1006.needlesslylargerod.benefit.UserProfileRepository;
import io.github.jaeya1006.needlesslylargerod.expense.ExpenseRepository;
import io.github.jaeya1006.needlesslylargerod.expense.WatchedCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴: 그 회원의 데이터를 한 트랜잭션에서 모두 지운다(중간에 실패하면 하나도 안 지워짐). */
@Service
@RequiredArgsConstructor
public class AccountDeletion {

    private final ExpenseRepository expenses;
    private final WatchedCategoryRepository watched;
    private final UserProfileRepository profiles;
    private final RefreshTokenRepository refreshTokens;
    private final MemberRepository members;

    @Transactional
    public void delete(long memberId) {
        expenses.deleteByOwner(memberId);
        watched.deleteByOwner(memberId);
        profiles.findById(memberId).ifPresent(profiles::delete);
        refreshTokens.deleteByMember(memberId);
        members.deleteById(memberId);
    }
}
