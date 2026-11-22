package io.github.jaeya1006.needlesslylargerod.benefit;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BenefitCandidatesTest {

    static GovService svc(String id, String name, String agency, long views) {
        return new GovService(id, name, null, null, null, null, "https://example.go.kr/" + id, agency, views);
    }

    @Test
    void picksByKeywordRegionAndViews() {
        List<GovService> all = List.of(
                svc("1", "K-패스 대중교통비 환급", "국토교통부", 500),
                svc("2", "청년 교통비 지원", "부산광역시", 900),       // 다른 지역 → 제외
                svc("3", "서울 청년 대중교통 지원", "서울특별시 강남구", 100),
                svc("4", "농식품 바우처", "농림축산식품부", 999),      // 교통 아님 → 제외
                svc("5", "충북 버스 요금 지원", "충청북도", 50));      // 충청북도 = 충북 → 제외

        assertThat(BenefitCandidates.pick(all, "교통", "서울", 10))
                .extracting(GovService::id).containsExactly("1", "3");
        assertThat(BenefitCandidates.pick(all, "교통", "서울", 1)).extracting(GovService::id).containsExactly("1");
        assertThat(BenefitCandidates.pick(all, "술", "서울", 10)).isEmpty();
        assertThat(BenefitCandidates.supports("쇼핑")).isFalse();
    }

    @Test
    void keepsOnlyCandidatesTheAiWasGiven() {
        Map<String, List<GovService>> candidates = Map.of("교통", List.of(
                svc("1", "K-패스", "국토교통부", 1), svc("2", "A", "국토교통부", 1),
                svc("3", "B", "국토교통부", 1), svc("4", "C", "국토교통부", 1)));
        List<BenefitRecommender.Pick> picks = List.of(
                new BenefitRecommender.Pick("교통", "1", "청년이라 환급"),
                new BenefitRecommender.Pick("교통", "1", "중복"),
                new BenefitRecommender.Pick("교통", "999", "목록에 없는 정책"),
                new BenefitRecommender.Pick("식비", "2", "분류가 다름"),
                new BenefitRecommender.Pick("교통", "2", "a"),
                new BenefitRecommender.Pick("교통", "3", "b"),
                new BenefitRecommender.Pick("교통", "4", "분류당 3개 초과"));

        List<BenefitRecommender.Recommendation> out = BenefitRecommender.keepKnown(picks, candidates);

        assertThat(out).extracting(BenefitRecommender.Recommendation::serviceId).containsExactly("1", "2", "3");
        assertThat(out.get(0).name()).isEqualTo("K-패스");
        assertThat(out.get(0).url()).isEqualTo("https://example.go.kr/1");
    }

    @Test
    void parsesKoreanFieldNames() throws Exception {
        String json = """
                {"currentCount":1,"data":[{"서비스ID":"A1","서비스명":"K-패스","소관기관명":"국토교통부",
                "상세조회URL":"https://www.gov.kr/x","조회수":12,"모르는필드":"무시"}],"page":1,"perPage":1,"totalCount":1}
                """;
        GovService.Page page = JsonMapper.builder().build().readValue(json, GovService.Page.class);
        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.data().getFirst().name()).isEqualTo("K-패스");
        assertThat(page.data().getFirst().views()).isEqualTo(12L);
    }

    @Test
    void encodesApiKeyInUri() {
        assertThat(new GovBenefitClient("a+b/c=").pageUri(2).toString())
                .contains("page=2").endsWith("serviceKey=a%2Bb%2Fc%3D");
    }
}
