package io.github.jaeya1006.needlesslylargerod.expense;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ExpenseControllerTest {

    @Autowired
    MockMvc mvc;

    private String create(String json) throws Exception {
        String body = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":(\\d+).*", "$1");
    }

    @Test
    void createListDelete() throws Exception {
        String id = create("{\"type\":\"EXPENSE\",\"title\":\"커피\",\"amount\":4500,\"category\":\"카페\",\"memo\":\" \",\"spentAt\":\"2026-11-22\"}");

        mvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].amount").value(4500))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].type").value("EXPENSE"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].memo").value((Object) null));

        mvc.perform(delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/expenses")).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    @Test
    void filtersByMonth() throws Exception {
        String oct = create("{\"type\":\"INCOME\",\"amount\":3000000,\"category\":\"월급\",\"spentAt\":\"2025-10-31\"}");
        String nov = create("{\"type\":\"EXPENSE\",\"amount\":1000,\"category\":\"간식\",\"spentAt\":\"2025-11-01\"}");

        mvc.perform(get("/api/expenses").param("month", "2025-10"))
                .andExpect(jsonPath("$[?(@.id == " + oct + ")].title").value((Object) null))
                .andExpect(jsonPath("$[?(@.id == " + nov + ")]").isEmpty());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":0,\"category\":\"카페\",\"spentAt\":\"2026-11-22\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"category\":\"카페\",\"spentAt\":\"2026-11-22\"}"))
                .andExpect(status().isBadRequest());
    }
}
