package io.github.jaeya1006.needlesslylargerod.benefit;

import io.github.jaeya1006.needlesslylargerod.auth.CurrentMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** 회원 설정: 지금은 큰 지출 기준 금액만. 값은 회원별 설정 줄(UserProfile)에 둔다. */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserProfileRepository repository;

    public record Settings(long bigExpenseThreshold) {
    }

    public record SettingsRequest(@NotNull @Min(1_000) @Max(100_000_000) Long bigExpenseThreshold) {
    }

    @GetMapping
    public Settings get(@AuthenticationPrincipal Jwt jwt) {
        long threshold = repository.findById(CurrentMember.id(jwt))
                .map(UserProfile::bigExpenseThreshold).orElse(UserProfile.DEFAULT_BIG_EXPENSE);
        return new Settings(threshold);
    }

    @PutMapping
    public Settings save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsRequest req) {
        long me = CurrentMember.id(jwt);
        UserProfile p = repository.findById(me).orElseGet(() -> new UserProfile(me, null, null, null));
        p.changeBigExpenseThreshold(req.bigExpenseThreshold());
        repository.save(p);
        return new Settings(p.bigExpenseThreshold());
    }
}
