package io.github.jaeya1006.needlesslylargerod.benefit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 정부 혜택 목록을 가져와 메모리에 하루 동안 둔다.
 * shortcut: 서버 메모리 캐시라 재시작하면 다시 받는다. 여러 서버로 늘리면 DB나 Redis로 옮긴다.
 */
@Component
public class GovBenefitClient {

    private static final String LIST_URL = "https://api.odcloud.kr/api/gov24/v3/serviceList";
    private static final int PER_PAGE = 1000;
    private static final int MAX_PAGES = 30; // 혹시 totalCount가 이상해도 무한히 돌지 않게
    private static final Duration TTL = Duration.ofHours(24);

    private final RestClient http = RestClient.create();
    private final String apiKey;

    private List<GovService> cache = List.of();
    private Instant fetchedAt = Instant.EPOCH;

    public GovBenefitClient(@Value("${DATA_GO_KR_API_KEY:}") String apiKey) {
        this.apiKey = apiKey.strip();
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }

    public synchronized List<GovService> all() {
        if (Instant.now().isBefore(fetchedAt.plus(TTL)) && !cache.isEmpty()) return cache;
        List<GovService> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            GovService.Page body = http.get().uri(pageUri(page)).retrieve().body(GovService.Page.class);
            if (body == null || body.data() == null || body.data().isEmpty()) break;
            result.addAll(body.data());
            if (body.totalCount() != null && result.size() >= body.totalCount()) break;
        }
        cache = List.copyOf(result);
        fetchedAt = Instant.now();
        return cache;
    }

    // 키에 +, /, = 가 들어 있어서 직접 인코딩한 URI를 넘긴다(RestClient가 다시 인코딩하지 않게).
    URI pageUri(int page) {
        return URI.create(LIST_URL + "?page=" + page + "&perPage=" + PER_PAGE + "&returnType=JSON&serviceKey="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
    }
}
