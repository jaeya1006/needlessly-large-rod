package io.github.jaeya1006.needlesslylargerod.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 비밀번호를 계속 바꿔 가며 시도하는 공격을 막는다.
 * - 같은 계정 + 같은 IP: 15분에 5번 실패하면 막음 (IP를 섞어서, 남이 내 계정을 일부러 잠그기 어렵게)
 * - 같은 IP: 15분에 30번 실패하면 막음 (여러 이메일을 돌려 가며 시도하는 경우)
 * 비밀번호 검사(BCrypt) 전에 begin()으로 '실패 1회'를 미리 잡아 두고, 맞으면 succeeded()로 되돌린다.
 * 그래서 동시에 수십 개를 보내도 한도를 넘는 요청은 검사까지 가지 못한다.
 * shortcut: 서버 메모리에 둔다. 재시작하면 초기화되고, 서버가 여러 대면 Redis 같은 공유 저장소로 옮긴다.
 * shortcut: IP는 request.getRemoteAddr(). 프록시 뒤에 두면 X-Forwarded-For를 믿을 수 있게 설정해야 한다.
 */
@Component
public class LoginAttemptLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_PER_ACCOUNT_IP = 5;
    static final int MAX_PER_IP = 30;
    private static final int CLEANUP_EVERY = 500;

    /** begin()이 잡아 둔 시도. 성공하면 succeeded()에 돌려준다. */
    public record Attempt(String account, String ip, Instant at) {
    }

    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private int calls;
    Clock clock = Clock.systemUTC(); // 테스트에서 시간을 바꿀 수 있게

    /** 한도를 넘었으면 429. 아니면 이 시도를 실패로 미리 기록한다(검사와 기록 사이에 다른 요청이 끼지 못하게). */
    public synchronized Attempt begin(String account, String ip) {
        Instant now = clock.instant();
        if (++calls % CLEANUP_EVERY == 0) cleanup(now);
        Deque<Instant> acct = queue(accountKey(account, ip), now);
        Deque<Instant> byIp = queue(ipKey(ip), now);
        if (acct.size() >= MAX_PER_ACCOUNT_IP || byIp.size() >= MAX_PER_IP) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "시도가 너무 많아요. 15분 뒤에 다시 해 주세요");
        }
        acct.addLast(now);
        byIp.addLast(now);
        return new Attempt(account, ip, now);
    }

    /** 맞았으면: 그 계정+IP 기록은 지우고, IP 기록에서는 이번 시도 1회만 뺀다 */
    public synchronized void succeeded(Attempt a) {
        failures.remove(accountKey(a.account(), a.ip()));
        Deque<Instant> byIp = failures.get(ipKey(a.ip()));
        if (byIp != null) byIp.removeFirstOccurrence(a.at());
    }

    private static String accountKey(String account, String ip) {
        return "acct:" + account + "|" + ip;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private Deque<Instant> queue(String key, Instant now) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(q, now);
        return q;
    }

    private static void prune(Deque<Instant> q, Instant now) {
        while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(WINDOW))) q.pollFirst();
    }

    // 오래된 기록만 남은 키를 지워 메모리가 계속 늘지 않게
    private void cleanup(Instant now) {
        failures.values().forEach(q -> prune(q, now));
        failures.values().removeIf(Deque::isEmpty);
    }
}
