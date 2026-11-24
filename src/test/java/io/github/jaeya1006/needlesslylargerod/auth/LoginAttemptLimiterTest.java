package io.github.jaeya1006.needlesslylargerod.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    @Test
    void unlocksAfterWindowAndLimitsWholeIp() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        limiter.clock = Clock.fixed(t0, ZoneOffset.UTC);

        for (int i = 0; i < 5; i++) limiter.begin("a@x.com", "1.1.1.1"); // 실패로 남김
        assertThatThrownBy(() -> limiter.begin("a@x.com", "1.1.1.1")).isInstanceOf(ResponseStatusException.class);

        limiter.clock = Clock.fixed(t0.plus(LoginAttemptLimiter.WINDOW).plusSeconds(1), ZoneOffset.UTC);
        assertThatCode(() -> limiter.begin("a@x.com", "1.1.1.1")).doesNotThrowAnyException();

        // 여러 이메일을 돌려 가며 30번 틀리면 IP 전체가 막힘
        for (int i = 0; i < LoginAttemptLimiter.MAX_PER_IP; i++) limiter.begin("user" + i + "@x.com", "2.2.2.2");
        assertThatThrownBy(() -> limiter.begin("new@x.com", "2.2.2.2")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void successDoesNotCountAsFailure() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        for (int i = 0; i < 10; i++) limiter.succeeded(limiter.begin("ok@x.com", "3.3.3.3"));
        assertThatCode(() -> limiter.begin("ok@x.com", "3.3.3.3")).doesNotThrowAnyException();
    }

    @Test
    void burstOfConcurrentAttemptsCannotPassTheLimit() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    limiter.begin("burst@x.com", "4.4.4.4");
                    admitted.incrementAndGet(); // 비밀번호 검사까지 간 요청
                } catch (ResponseStatusException ignored) {
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(admitted.get()).isEqualTo(LoginAttemptLimiter.MAX_PER_ACCOUNT_IP);
    }
}
