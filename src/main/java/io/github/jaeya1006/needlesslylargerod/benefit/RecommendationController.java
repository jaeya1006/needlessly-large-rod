package io.github.jaeya1006.needlesslylargerod.benefit;

import io.github.jaeya1006.needlesslylargerod.expense.WatchedCategory;
import io.github.jaeya1006.needlesslylargerod.expense.WatchedCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 체크한 분류(항상 비교하는 분류)에 맞는 정부 혜택 추천 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    static final int CANDIDATES_PER_CATEGORY = 10;

    /**
     * status: OK, NO_KEYS(키 없음, missing에 이름), NO_PROFILE(조건 미입력), NO_WATCHED(체크한 분류 없음)
     * unsupported: 체크했지만 혜택을 찾지 않는 분류(술, 쇼핑 등)
     */
    public record Response(String status, List<String> missing, List<String> unsupported,
                           List<BenefitRecommender.Recommendation> items) {
        static Response of(String status) {
            return new Response(status, List.of(), List.of(), List.of());
        }
    }

    private final GovBenefitClient gov;
    private final BenefitRecommender recommender;
    private final UserProfileRepository profiles;
    private final WatchedCategoryRepository watched;

    @GetMapping
    public Response recommend() {
        List<String> missing = new ArrayList<>();
        if (!gov.hasKey()) missing.add("DATA_GO_KR_API_KEY");
        if (!recommender.hasKey()) missing.add("ANTHROPIC_API_KEY");
        if (!missing.isEmpty()) return new Response("NO_KEYS", missing, List.of(), List.of());

        UserProfile p = profiles.findById(UserProfile.ID).orElse(null);
        if (p == null || p.getBirthYear() == null) return Response.of("NO_PROFILE");
        List<String> categories = watched.findAll().stream().map(WatchedCategory::getCategory).sorted().toList();
        if (categories.isEmpty()) return Response.of("NO_WATCHED");

        List<String> unsupported = categories.stream().filter(c -> !BenefitCandidates.supports(c)).toList();
        Map<String, List<GovService>> candidates = new LinkedHashMap<>();
        try {
            List<GovService> all = gov.all();
            for (String c : categories) {
                List<GovService> picked = BenefitCandidates.pick(all, c, p.getRegion(), CANDIDATES_PER_CATEGORY);
                if (!picked.isEmpty()) candidates.put(c, picked);
            }
            if (candidates.isEmpty()) return new Response("OK", List.of(), unsupported, List.of());
            int age = Year.now().getValue() - p.getBirthYear();
            return new Response("OK", List.of(), unsupported,
                    recommender.recommend(age, p.getRegion(), p.getStatus(), candidates));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "혜택 정보를 가져오지 못했어요: " + e.getMessage(), e);
        }
    }
}
