package io.github.jaeya1006.needlesslylargerod.expense;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class WastedAndBigTest {

    @Autowired
    MockMvc mvc;

    static final RequestPostProcessor ME = jwt().jwt(j -> j.subject("301"));
    static final RequestPostProcessor OTHER = jwt().jwt(j -> j.subject("302"));

    ResultActions perform(MockHttpServletRequestBuilder req, RequestPostProcessor who) throws Exception {
        return mvc.perform(req.with(who));
    }

    String create(String json) throws Exception {
        return perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json), ME)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void wastedFlagCanBeSetOnCreateToggledAndKeptOnEdit() throws Exception {
        String id = create("{\"type\":\"EXPENSE\",\"amount\":89000,\"category\":\"쇼핑\",\"spentAt\":\"2022-02-01\",\"wasted\":true}");
        perform(get("/api/expenses").param("month", "2022-02"), ME).andExpect(jsonPath("$[0].wasted").value(true));

        // 수정할 때 wasted를 안 보내면 그대로
        perform(put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"amount\":90000,\"category\":\"쇼핑\",\"spentAt\":\"2022-02-01\"}"), ME)
                .andExpect(jsonPath("$.wasted").value(true));

        perform(patch("/api/expenses/" + id + "/wasted").contentType(MediaType.APPLICATION_JSON).content("{\"wasted\":false}"), ME)
                .andExpect(status().isOk()).andExpect(jsonPath("$.wasted").value(false));
        perform(patch("/api/expenses/" + id + "/wasted").contentType(MediaType.APPLICATION_JSON).content("{\"wasted\":true}"), OTHER)
                .andExpect(status().isNotFound());
        perform(patch("/api/expenses/" + id + "/wasted").contentType(MediaType.APPLICATION_JSON).content("{}"), ME)
                .andExpect(status().isBadRequest());

        // 안 보내고 새로 적으면 false
        create("{\"type\":\"EXPENSE\",\"amount\":1000,\"category\":\"간식\",\"spentAt\":\"2022-03-01\"}");
        perform(get("/api/expenses").param("month", "2022-03"), ME).andExpect(jsonPath("$[0].wasted").value(false));
    }

    @Test
    void bigExpenseThresholdDefaultsAndSaves() throws Exception {
        perform(get("/api/settings"), jwt().jwt(j -> j.subject("303"))).andExpect(jsonPath("$.bigExpenseThreshold").value(50000));
        perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"bigExpenseThreshold\":100000}"), ME)
                .andExpect(jsonPath("$.bigExpenseThreshold").value(100000));
        perform(get("/api/settings"), ME).andExpect(jsonPath("$.bigExpenseThreshold").value(100000));
        perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"bigExpenseThreshold\":10}"), ME)
                .andExpect(status().isBadRequest());
        // 설정만 저장해도 혜택 추천 조건은 비어 있는 상태(추천은 NO_PROFILE)
        perform(get("/api/profile"), ME).andExpect(jsonPath("$.birthYear").isEmpty());
    }
}
