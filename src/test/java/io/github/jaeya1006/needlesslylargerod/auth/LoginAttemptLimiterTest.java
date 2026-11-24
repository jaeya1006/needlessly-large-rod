package io.github.jaeya1006.needlesslylargerod.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    @Test
    void unlocksAfterWindowAndLimitsWholeIp() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        limiter.clock = Clock.fixed(t0, ZoneOffset.UTC);

        for (int i = 0; i < 5; i++) limiter.failed("a@x.com", "1.1.1.1");
        assertThatThrownBy(() -> limiter.check("a@x.com", "1.1.1.1")).isInstanceOf(ResponseStatusException.class);

        limiter.clock = Clock.fixed(t0.plus(LoginAttemptLimiter.WINDOW).plusSeconds(1), ZoneOffset.UTC);
        assertThatCode(() -> limiter.check("a@x.com", "1.1.1.1")).doesNotThrowAnyException();

        // 여러 이메일을 돌려 가며 30번 틀리면 IP 전체가 막힘
        for (int i = 0; i < LoginAttemptLimiter.MAX_PER_IP; i++) limiter.failed("user" + i + "@x.com", "2.2.2.2");
        assertThatThrownBy(() -> limiter.check("new@x.com", "2.2.2.2")).isInstanceOf(ResponseStatusException.class);
    }
}
