package io.github.jaeya1006.needlesslylargerod.benefit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovBenefitClientTest {

    /** 페이지 번호 → 응답을 미리 정해 둔 가짜 클라이언트(네트워크 안 씀) */
    static GovBenefitClient fake(Map<Integer, GovService.Page> pages) {
        return new GovBenefitClient("test-key") {
            @Override
            GovService.Page fetchPage(int page) {
                return pages.get(page);
            }
        };
    }

    static List<GovService> services(int from, int count) {
        return IntStream.range(from, from + count)
                .mapToObj(i -> new GovService(String.valueOf(i), "서비스" + i, null, null, null, null, null, null, 0L))
                .toList();
    }

    @Test
    void readsAllPagesUntilTotalCount() {
        GovBenefitClient c = fake(Map.of(
                1, new GovService.Page(services(0, GovBenefitClient.PER_PAGE), 1500),
                2, new GovService.Page(services(1000, 500), 1500)));
        assertThat(c.all()).hasSize(1500);
    }

    @Test
    void rejectsPartialList() {
        // 1페이지는 1,500건이라는데 2페이지가 비어 있으면 일부만 받은 것
        GovBenefitClient c = fake(Map.of(1, new GovService.Page(services(0, GovBenefitClient.PER_PAGE), 1500)));
        assertThatThrownBy(c::all).isInstanceOf(IllegalStateException.class).hasMessageContaining("일부만");
    }

    @Test
    void rejectsEmptyList() {
        assertThatThrownBy(fake(Map.of())::all).isInstanceOf(IllegalStateException.class).hasMessageContaining("비어");
    }
}
