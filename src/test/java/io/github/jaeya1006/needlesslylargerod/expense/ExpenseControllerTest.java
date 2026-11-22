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
class ExpenseControllerTest {

    @Autowired
    MockMvc mvc;

    /** 로그인한 회원 101 으로 요청 */
    static final RequestPostProcessor ME = jwt().jwt(j -> j.subject("101"));

    ResultActions perform(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(ME));
    }

    private String create(String json) throws Exception {
        String body = perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void createListDelete() throws Exception {
        String id = create("{\"type\":\"EXPENSE\",\"title\":\"커피\",\"amount\":4500,\"category\":\"카페\",\"memo\":\" \",\"spentAt\":\"2026-11-22\"}");

        perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].amount").value(4500))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].type").value("EXPENSE"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].memo").value((Object) null));

        perform(delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        perform(get("/api/expenses")).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    @Test
    void savesSubcategory() throws Exception {
        String taxi = create("{\"type\":\"EXPENSE\",\"amount\":15000,\"category\":\"교통\",\"subcategory\":\"택시\",\"spentAt\":\"2024-03-01\"}");
        String plain = create("{\"type\":\"EXPENSE\",\"amount\":3000,\"category\":\"카페\",\"subcategory\":\" \",\"spentAt\":\"2024-03-01\"}");

        perform(get("/api/expenses").param("month", "2024-03"))
                .andExpect(jsonPath("$[?(@.id == " + taxi + ")].subcategory").value("택시"))
                .andExpect(jsonPath("$[?(@.id == " + plain + ")].subcategory").value((Object) null));
    }

    @Test
    void updatesEntry() throws Exception {
        String id = create("{\"type\":\"EXPENSE\",\"amount\":20,\"category\":\"교통\",\"subcategory\":\"지하철\",\"spentAt\":\"2024-04-01\"}");

        perform(put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"title\":\"출근\",\"amount\":1550,\"category\":\"교통\",\"subcategory\":\"버스\",\"memo\":\"\",\"spentAt\":\"2024-04-02\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1550))
                .andExpect(jsonPath("$.title").value("출근"))
                .andExpect(jsonPath("$.subcategory").value("버스"))
                .andExpect(jsonPath("$.memo").value((Object) null))
                .andExpect(jsonPath("$.spentAt").value("2024-04-02"));

        perform(put("/api/expenses/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":1,\"category\":\"기타\",\"spentAt\":\"2024-04-02\"}"))
                .andExpect(status().isNotFound());
        perform(put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":0,\"category\":\"기타\",\"spentAt\":\"2024-04-02\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersByMonth() throws Exception {
        String oct = create("{\"type\":\"INCOME\",\"amount\":3000000,\"category\":\"월급\",\"spentAt\":\"2025-10-31\"}");
        String nov = create("{\"type\":\"EXPENSE\",\"amount\":1000,\"category\":\"간식\",\"spentAt\":\"2025-11-01\"}");

        perform(get("/api/expenses").param("month", "2025-10"))
                .andExpect(jsonPath("$[?(@.id == " + oct + ")].title").value((Object) null))
                .andExpect(jsonPath("$[?(@.id == " + nov + ")]").isEmpty());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":0,\"category\":\"카페\",\"spentAt\":\"2026-11-22\"}"))
                .andExpect(status().isBadRequest());
        perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"category\":\"카페\",\"spentAt\":\"2026-11-22\"}"))
                .andExpect(status().isBadRequest());
    }
}
