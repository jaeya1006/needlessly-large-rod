package io.github.jaeya1006.needlesslylargerod.benefit;

import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// .env에 실제 키를 넣어도 테스트가 외부 API를 부르지 않게 키를 비운다
@SpringBootTest(properties = {"DATA_GO_KR_API_KEY=", "ANTHROPIC_API_KEY="})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ProfileAndRecommendationTest {

    @Autowired
    MockMvc mvc;

    /** 로그인한 회원 103 으로 요청 */
    static final RequestPostProcessor ME = jwt().jwt(j -> j.subject("103"));

    ResultActions perform(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(ME));
    }

    @Test
    void savesProfile() throws Exception {
        perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":2001,\"region\":\"서울\",\"status\":\"직장인\"}"))
                .andExpect(status().isOk());
        perform(get("/api/profile"))
                .andExpect(jsonPath("$.birthYear").value(2001))
                .andExpect(jsonPath("$.region").value("서울"))
                .andExpect(jsonPath("$.status").value("직장인"));

        perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":2001,\"region\":\"서울시\",\"status\":\"직장인\"}"))
                .andExpect(status().isBadRequest());
        perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":1800,\"region\":\"서울\",\"status\":\"직장인\"}"))
                .andExpect(status().isBadRequest());
        perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":" + (java.time.Year.now().getValue() + 1) + ",\"region\":\"서울\",\"status\":\"직장인\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tellsWhichKeysAreMissing() throws Exception {
        perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_KEYS"))
                .andExpect(jsonPath("$.missing", contains("DATA_GO_KR_API_KEY", "ANTHROPIC_API_KEY")));
    }
}
