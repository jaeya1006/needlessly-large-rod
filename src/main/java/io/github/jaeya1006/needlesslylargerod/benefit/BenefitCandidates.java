package io.github.jaeya1006.needlesslylargerod.benefit;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** 지출 분류별로 관련 있어 보이는 정부 혜택 후보를 고른다. AI는 이 후보 안에서만 추천한다. */
public final class BenefitCandidates {

    private BenefitCandidates() {
    }

    /** 분류 → 혜택 이름·설명에서 찾을 낱말. 여기 없는 분류(술, 쇼핑 등)는 추천하지 않는다. */
    static final Map<String, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry("교통", List.of("교통", "대중교통", "K-패스", "케이패스", "버스", "지하철", "철도")),
            Map.entry("식비", List.of("식비", "식품", "급식", "먹거리", "농식품")),
            Map.entry("통신", List.of("통신", "통신비", "요금감면")),
            Map.entry("의료", List.of("의료", "의료비", "진료", "건강검진", "약제")),
            Map.entry("공과금", List.of("에너지", "전기요금", "가스요금", "난방", "수도요금")),
            Map.entry("교육", List.of("교육", "학자금", "장학", "평생교육", "훈련")),
            Map.entry("문화", List.of("문화", "문화누리", "공연", "관람", "여가")),
            Map.entry("운동", List.of("체육", "스포츠", "운동")),
            Map.entry("생활", List.of("생활", "주거", "월세", "생계")),
            Map.entry("경조사", List.of("출산", "결혼", "장례", "임신")));

    /** 시·도 짧은 이름 → 기관명에 나올 수 있는 이름들 */
    static final Map<String, List<String>> REGION_NAMES = Map.ofEntries(
            Map.entry("서울", List.of("서울")), Map.entry("부산", List.of("부산")),
            Map.entry("대구", List.of("대구")), Map.entry("인천", List.of("인천")),
            Map.entry("광주", List.of("광주광역시")), Map.entry("대전", List.of("대전")),
            Map.entry("울산", List.of("울산")), Map.entry("세종", List.of("세종")),
            Map.entry("경기", List.of("경기")), Map.entry("강원", List.of("강원")),
            Map.entry("충북", List.of("충청북도", "충북")), Map.entry("충남", List.of("충청남도", "충남")),
            Map.entry("전북", List.of("전라북도", "전북")), Map.entry("전남", List.of("전라남도", "전남")),
            Map.entry("경북", List.of("경상북도", "경북")), Map.entry("경남", List.of("경상남도", "경남")),
            Map.entry("제주", List.of("제주")));

    public static boolean supports(String category) {
        return KEYWORDS.containsKey(category);
    }

    /** 낱말이 맞고, 다른 시·도 기관이 아닌 혜택을 조회수 순으로 limit개 */
    public static List<GovService> pick(List<GovService> all, String category, String region, int limit) {
        List<String> words = KEYWORDS.getOrDefault(category, List.of());
        if (words.isEmpty()) return List.of();
        return all.stream()
                .filter(s -> s.id() != null && s.name() != null)
                .filter(s -> words.stream().anyMatch(w -> text(s).contains(w)))
                .filter(s -> !fromOtherRegion(s.agency(), region))
                .sorted(Comparator.comparing((GovService s) -> s.views() == null ? 0 : s.views()).reversed())
                .limit(limit)
                .toList();
    }

    private static String text(GovService s) {
        return String.join(" ", Stream.of(s.name(), s.summary(), s.content()).filter(Objects::nonNull).toList());
    }

    static boolean fromOtherRegion(String agency, String region) {
        if (agency == null) return false;
        return REGION_NAMES.entrySet().stream()
                .filter(e -> !e.getKey().equals(region))
                .anyMatch(e -> e.getValue().stream().anyMatch(agency::startsWith));
    }
}
