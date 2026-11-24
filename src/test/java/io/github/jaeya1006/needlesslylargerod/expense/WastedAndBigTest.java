package io.github.jaeya1006.needlesslylargerod.expense;

import io.github.jaeya1006.needlesslylargerod.TestMembers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void members() {
        TestMembers.ensure(jdbc, 301, 302, 303, 304); // 외래 키 때문에 가짜 로그인 회원 줄이 있어야 한다
    }

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
        // 설정만 저장해도 혜택 추천 조건은 비어 있는 상태
        perform(get("/api/profile"), ME).andExpect(jsonPath("$.birthYear").isEmpty());
        // 회원마다 따로
        perform(get("/api/settings"), OTHER).andExpect(jsonPath("$.bigExpenseThreshold").value(50000));
    }

    @Test
    void savingBenefitProfileKeepsThreshold() throws Exception {
        RequestPostProcessor who = jwt().jwt(j -> j.subject("304"));
        perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"bigExpenseThreshold\":70000}"), who);
        perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthYear\":2000,\"region\":\"서울\",\"status\":\"학생\"}"), who).andExpect(status().isOk());
        perform(get("/api/settings"), who).andExpect(jsonPath("$.bigExpenseThreshold").value(70000));
    }

    @Test
    void legacyRowWithoutWastedReadsAsFalse() throws Exception {
        String id = create("{\"type\":\"EXPENSE\",\"amount\":5000,\"category\":\"카페\",\"spentAt\":\"2022-04-01\",\"wasted\":true}");
        jdbc.update("UPDATE expense SET wasted = NULL WHERE id = ?", Long.parseLong(id)); // 기능 전에 저장된 행 흉내
        perform(get("/api/expenses").param("month", "2022-04"), ME).andExpect(jsonPath("$[0].wasted").value(false));
    }
}
