package io.github.jaeya1006.needlesslylargerod.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비밀번호를 계속 바꿔 가며 시도하는 공격을 막는다.
 * - 같은 계정 + 같은 IP: 15분에 5번 실패하면 막음 (IP를 섞어서, 남이 내 계정을 일부러 잠그기 어렵게)
 * - 같은 IP: 15분에 30번 실패하면 막음 (여러 이메일을 돌려 가며 시도하는 경우)
 * shortcut: 서버 메모리에 둔다. 재시작하면 초기화되고, 서버가 여러 대면 Redis 같은 공유 저장소로 옮긴다.
 * shortcut: IP는 request.getRemoteAddr(). 프록시 뒤에 두면 X-Forwarded-For를 믿을 수 있게 설정해야 한다.
 */
@Component
public class LoginAttemptLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_PER_ACCOUNT_IP = 5;
    static final int MAX_PER_IP = 30;
    private static final int CLEANUP_EVERY = 500;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private int calls;
    Clock clock = Clock.systemUTC(); // 테스트에서 시간을 바꿀 수 있게

    /** 막혀 있으면 429 */
    public void check(String account, String ip) {
        Instant now = clock.instant();
        if (count(accountKey(account, ip), now) >= MAX_PER_ACCOUNT_IP || count(ipKey(ip), now) >= MAX_PER_IP) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "시도가 너무 많아요. 15분 뒤에 다시 해 주세요");
        }
    }

    public void failed(String account, String ip) {
        Instant now = clock.instant();
        add(accountKey(account, ip), now);
        add(ipKey(ip), now);
        if (++calls % CLEANUP_EVERY == 0) cleanup(now);
    }

    /** 성공하면 그 계정+IP 기록은 지운다(IP 전체 기록은 남긴다) */
    public void succeeded(String account, String ip) {
        failures.remove(accountKey(account, ip));
    }

    private static String accountKey(String account, String ip) {
        return "acct:" + account + "|" + ip;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private int count(String key, Instant now) {
        Deque<Instant> q = failures.get(key);
        if (q == null) return 0;
        synchronized (q) {
            prune(q, now);
            return q.size();
        }
    }

    private void add(String key, Instant now) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            q.addLast(now);
        }
    }

    private static void prune(Deque<Instant> q, Instant now) {
        while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(WINDOW))) q.pollFirst();
    }

    // 오래된 기록만 남은 키를 지워 메모리가 계속 늘지 않게
    private void cleanup(Instant now) {
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }
}
