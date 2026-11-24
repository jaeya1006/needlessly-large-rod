package io.github.jaeya1006.needlesslylargerod.auth;

import io.github.jaeya1006.needlesslylargerod.benefit.UserProfileRepository;
import io.github.jaeya1006.needlesslylargerod.expense.ExpenseRepository;
import io.github.jaeya1006.needlesslylargerod.expense.WatchedCategoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 비밀번호 변경과 회원 탈퇴. 둘 다 회원 줄을 잠근 한 트랜잭션에서 비밀번호를 확인하고 처리한다.
 * - 동시에 두 번 바꿔도 두 번째는 바뀐 비밀번호로 다시 확인돼서 실패한다.
 * - 로그인·토큰 갱신도 같은 줄을 잠그므로, 바꾸는 도중에 옛 비밀번호로 새 토큰이 나오지 않는다.
 * - 탈퇴 뒤에 늦게 도착한 쓰기 요청은 DB 외래 키 때문에 저장되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletion {

    private final ExpenseRepository expenses;
    private final WatchedCategoryRepository watched;
    private final UserProfileRepository profiles;
    private final RefreshTokenRepository refreshTokens;
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    /** 비밀번호가 맞으면 true(변경 + 다른 기기 끊기 + 이 기기 새 토큰), 틀리면 false */
    @Transactional
    public boolean changePassword(long memberId, String current, String next, HttpServletRequest req, HttpServletResponse res) {
        Member m = lock(memberId);
        if (!passwordEncoder.matches(current, m.getPasswordHash())) return false;
        m.changePassword(passwordEncoder.encode(next));
        tokens.revokeAllLocked(memberId);
        tokens.issueLocked(m, req, res);
        return true;
    }

    /** 비밀번호가 맞으면 true(모든 데이터 삭제), 틀리면 false */
    @Transactional
    public boolean delete(long memberId, String password) {
        Member m = lock(memberId);
        if (!passwordEncoder.matches(password, m.getPasswordHash())) return false;
        expenses.deleteByOwner(memberId);
        watched.deleteByOwner(memberId);
        profiles.findById(memberId).ifPresent(profiles::delete);
        refreshTokens.deleteByMember(memberId);
        members.delete(m);
        return true;
    }

    private Member lock(long memberId) {
        return members.lockById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"));
    }
}
