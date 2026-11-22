package io.github.jaeya1006.needlesslylargerod.benefit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void savesProfile() throws Exception {
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":2001,\"region\":\"서울\",\"status\":\"직장인\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/profile"))
                .andExpect(jsonPath("$.birthYear").value(2001))
                .andExpect(jsonPath("$.region").value("서울"))
                .andExpect(jsonPath("$.status").value("직장인"));

        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":2001,\"region\":\"서울시\",\"status\":\"직장인\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":1800,\"region\":\"서울\",\"status\":\"직장인\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":" + (java.time.Year.now().getValue() + 1) + ",\"region\":\"서울\",\"status\":\"직장인\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tellsWhichKeysAreMissing() throws Exception {
        mvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_KEYS"))
                .andExpect(jsonPath("$.missing", contains("DATA_GO_KR_API_KEY", "ANTHROPIC_API_KEY")));
    }
}
