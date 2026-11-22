package io.github.jaeya1006.needlesslylargerod.benefit;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Claude가 후보 혜택 중에서 사용자 조건에 맞는 것을 고른다. 후보에 없는 혜택은 버린다. */
@Component
public class BenefitRecommender {

    static final String MODEL = "claude-opus-5-5";
    static final int MAX_PER_CATEGORY = 3;

    private static final String SYSTEM = """
            너는 한국 정부 혜택 안내 도우미다. 사용자가 지출 분류별로 받을 수 있을 만한 정부 혜택을 고른다.
            규칙:
            - 반드시 주어진 후보 목록 안에서만 고른다. 목록에 없는 정책은 아무리 잘 알아도 고르지 않는다.
            - 사용자의 나이, 사는 시·도, 상태로 보아 지원대상·선정기준에 맞을 가능성이 있는 것만 고른다.
              확실히 대상이 아닌 것(나이 범위 밖, 다른 지역 전용, 특정 직업 전용 등)은 고르지 않는다.
            - 분류마다 최대 3개, 맞는 것이 없으면 그 분류는 고르지 않는다.
            - reason은 한국어 한 문장으로, 왜 이 사용자에게 맞는지와 핵심 혜택을 쓴다. 후보 설명에 없는 내용은 지어내지 않는다.
            """;

    record Pick(
            @JsonPropertyDescription("후보가 속한 지출 분류 이름") String category,
            @JsonPropertyDescription("후보 목록에 있는 서비스ID 그대로") String serviceId,
            @JsonPropertyDescription("이 사용자에게 맞는 이유와 핵심 혜택, 한국어 한 문장") String reason) {
    }

    record Picks(List<Pick> picks) {
    }

    public record Recommendation(String category, String serviceId, String name, String reason, String url, String agency) {
    }

    private final AnthropicClient client;

    public BenefitRecommender(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.client = apiKey.isBlank() ? null : AnthropicOkHttpClient.builder().apiKey(apiKey.strip()).build();
    }

    public boolean hasKey() {
        return client != null;
    }

    public List<Recommendation> recommend(int age, String region, String status, Map<String, List<GovService>> candidates) {
        StructuredMessageCreateParams<Picks> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                .system(SYSTEM)
                .outputConfig(Picks.class)
                .addUserMessage(prompt(age, region, status, candidates))
                .build();
        StructuredMessage<Picks> response = client.messages().create(params);
        // 거절이면 추천 없음으로 처리. shortcut: 서버 측 fallbacks(다른 모델로 재시도)는 아직 안 씀, 거절이 잦으면 추가
        if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) return List.of();
        List<Pick> picks = response.content().stream()
                .flatMap(block -> block.text().stream())
                .flatMap(typed -> typed.text().picks().stream())
                .toList();
        return keepKnown(picks, candidates);
    }

    /** AI가 고른 것 중 실제 후보에 있는 것만, 분류마다 최대 3개. 이름·링크는 공공데이터 원본으로 채운다. */
    static List<Recommendation> keepKnown(List<Pick> picks, Map<String, List<GovService>> candidates) {
        List<Recommendation> out = new ArrayList<>();
        for (Pick p : picks) {
            if (p == null || p.category() == null || p.serviceId() == null) continue;
            GovService s = candidates.getOrDefault(p.category(), List.of()).stream()
                    .filter(c -> p.serviceId().equals(c.id())).findFirst().orElse(null);
            if (s == null) continue;
            long already = out.stream().filter(r -> r.category().equals(p.category())).count();
            boolean duplicate = out.stream().anyMatch(r -> r.category().equals(p.category()) && r.serviceId().equals(s.id()));
            if (already >= MAX_PER_CATEGORY || duplicate) continue;
            out.add(new Recommendation(p.category(), s.id(), s.name(), p.reason(), s.url(), s.agency()));
        }
        return out;
    }

    static String prompt(int age, String region, String status, Map<String, List<GovService>> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("사용자: 올해 나이 ").append(age).append("세(만 나이와 1살 차이 날 수 있음), 사는 곳 ")
                .append(region).append(", 상태 ").append(status).append("\n\n");
        candidates.forEach((category, list) -> {
            sb.append("## 지출 분류: ").append(category).append("\n");
            for (GovService s : list) {
                sb.append("- 서비스ID: ").append(s.id())
                        .append("\n  서비스명: ").append(s.name())
                        .append("\n  소관기관: ").append(Objects.toString(s.agency(), ""))
                        .append("\n  요약: ").append(Objects.toString(s.summary(), ""))
                        .append("\n  지원대상: ").append(Objects.toString(s.target(), ""))
                        .append("\n  선정기준: ").append(Objects.toString(s.criteria(), ""))
                        .append("\n  지원내용: ").append(Objects.toString(s.content(), ""))
                        .append("\n");
            }
            sb.append("\n");
        });
        return sb.toString();
    }
}
