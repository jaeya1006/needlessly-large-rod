package io.github.jaeya1006.needlesslylargerod.benefit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 가짜 키: 술만 체크했으면 공공데이터·Claude를 부르기 전에 끝나야 한다(부르면 외부 호출로 실패)
@SpringBootTest(properties = {"DATA_GO_KR_API_KEY=dummy", "ANTHROPIC_API_KEY=dummy"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class UnsupportedOnlyRecommendationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void skipsFetchingWhenOnlyUnsupportedCategoriesAreWatched() throws Exception {
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":2001,\"region\":\"서울\",\"status\":\"직장인\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[\"술\"]"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.unsupported", contains("술")))
                .andExpect(jsonPath("$.items", empty()));
    }
}
