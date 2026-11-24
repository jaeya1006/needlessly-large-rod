package io.github.jaeya1006.needlesslylargerod.benefit;

import io.github.jaeya1006.needlesslylargerod.TestMembers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

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

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void member() {
        TestMembers.ensure(jdbc, 104);
    }

    /** 로그인한 회원 104 으로 요청 */
    static final RequestPostProcessor ME = jwt().jwt(j -> j.subject("104"));

    ResultActions perform(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(ME));
    }

    @Test
    void skipsFetchingWhenOnlyUnsupportedCategoriesAreWatched() throws Exception {
        perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":2001,\"region\":\"서울\",\"status\":\"직장인\"}")).andExpect(status().isOk());
        perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[\"술\"]"))
                .andExpect(status().isOk());

        perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.unsupported", contains("술")))
                .andExpect(jsonPath("$.items", empty()));
    }
}
