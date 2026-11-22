package io.github.jaeya1006.needlesslylargerod.benefit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 정부 혜택 목록을 가져와 메모리에 하루 동안 둔다.
 * 갱신은 한 번에 한 요청만 하고, 그동안 다른 요청은 이전 목록을 쓴다. 다 못 받으면 이전 목록을 유지한다.
 * shortcut: 서버 메모리 캐시라 재시작하면 다시 받는다. 여러 서버로 늘리면 DB나 Redis로 옮긴다.
 */
@Component
public class GovBenefitClient {

    private static final String LIST_URL = "https://api.odcloud.kr/api/gov24/v3/serviceList";
    static final int PER_PAGE = 1000;
    static final int MAX_PAGES = 30; // totalCount가 이상해도 무한히 돌지 않게
    private static final Duration TTL = Duration.ofHours(24);
    static final Duration REFRESH_DEADLINE = Duration.ofSeconds(90);

    record Snapshot(List<GovService> services, Instant fetchedAt) {
        boolean freshAt(Instant now) {
            return !services.isEmpty() && now.isBefore(fetchedAt.plus(TTL));
        }
    }

    private final RestClient http;
    private final String apiKey;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile Snapshot snapshot = new Snapshot(List.of(), Instant.EPOCH);

    public GovBenefitClient(@Value("${DATA_GO_KR_API_KEY:}") String apiKey) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.apiKey = apiKey.strip();
    }

    public boolean hasKey() {
        return !apiKey.isEmpty();
    }

    public List<GovService> all() {
        Snapshot before = snapshot;
        if (before.freshAt(Instant.now())) return before.services();
        boolean hasOld = !before.services().isEmpty();
        if (!lock(hasOld)) {
            if (hasOld) return before.services(); // 다른 요청이 갱신 중 → 이전 목록
            throw new IllegalStateException("혜택 목록을 받는 중이에요. 잠시 뒤 다시 시도해 주세요");
        }
        try {
            Snapshot current = snapshot; // 기다리는 동안 다른 요청이 갱신했을 수 있다
            if (current.freshAt(Instant.now())) return current.services();
            try {
                List<GovService> fetched = fetchAll();
                snapshot = new Snapshot(fetched, Instant.now());
                return fetched;
            } catch (RuntimeException e) {
                if (!current.services().isEmpty()) return current.services(); // 실패하면 이전 목록 유지
                throw e;
            }
        } finally {
            refreshLock.unlock();
        }
    }

    // 이전 목록이 있으면 기다리지 않고, 없으면 갱신 시간만큼 기다린다
    private boolean lock(boolean hasOld) {
        try {
            return hasOld ? refreshLock.tryLock() : refreshLock.tryLock(REFRESH_DEADLINE.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 끝까지 다 받았을 때만 목록을 돌려준다. 비었거나 일부만 받았거나 시간이 넘으면 예외. */
    List<GovService> fetchAll() {
        Instant deadline = Instant.now().plus(REFRESH_DEADLINE);
        List<GovService> result = new ArrayList<>();
        Integer total = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            if (Instant.now().isAfter(deadline)) throw new IllegalStateException("혜택 목록을 시간 안에 다 받지 못했어요");
            GovService.Page body = fetchPage(page);
            if (body != null && body.totalCount() != null) total = body.totalCount();
            List<GovService> data = body == null || body.data() == null ? List.of() : body.data();
            if (data.isEmpty()) break;
            result.addAll(data);
            if (total != null && result.size() >= total) break;
        }
        if (result.isEmpty()) throw new IllegalStateException("혜택 목록이 비어 있어요");
        if (total != null && result.size() < total)
            throw new IllegalStateException("혜택 목록을 일부만 받았어요 (" + result.size() + "/" + total + ")");
        if (total == null && result.size() >= MAX_PAGES * PER_PAGE)
            throw new IllegalStateException("혜택 목록을 끝까지 받지 못했어요");
        return List.copyOf(result);
    }

    GovService.Page fetchPage(int page) {
        return http.get().uri(pageUri(page)).retrieve().body(GovService.Page.class);
    }

    // 키에 +, /, = 가 들어 있어서 직접 인코딩한 URI를 넘긴다(RestClient가 다시 인코딩하지 않게).
    URI pageUri(int page) {
        return URI.create(LIST_URL + "?page=" + page + "&perPage=" + PER_PAGE + "&returnType=JSON&serviceKey="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
    }
}
