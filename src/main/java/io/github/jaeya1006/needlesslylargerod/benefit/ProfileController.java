package io.github.jaeya1006.needlesslylargerod.benefit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import io.github.jaeya1006.needlesslylargerod.auth.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    public static final List<String> REGIONS = List.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
    public static final List<String> STATUSES = List.of("학생", "직장인", "구직자", "자영업자", "주부", "기타");

    private final UserProfileRepository repository;

    public record ProfileRequest(
            @NotNull @Min(1900) @Max(2100) Integer birthYear,
            @NotNull @Pattern(regexp = "서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주") String region,
            @NotNull @Pattern(regexp = "학생|직장인|구직자|자영업자|주부|기타") String status) {
    }

    @GetMapping
    public UserProfile get(@AuthenticationPrincipal Jwt jwt) {
        return repository.findById(CurrentMember.id(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "아직 조건을 입력하지 않았어요"));
    }

    @PutMapping
    public UserProfile save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest req) {
        if (req.birthYear() > Year.now().getValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "출생연도가 올해보다 늦을 수 없어요");
        }
        long me = CurrentMember.id(jwt);
        UserProfile p = repository.findById(me).orElseGet(() -> new UserProfile(me, null, null, null));
        p.update(req.birthYear(), req.region(), req.status());
        return repository.save(p);
    }
}
