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

    @Test
    void createListDelete() throws Exception {
        String body = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"커피\",\"amount\":4500,\"category\":\"카페\",\"spentAt\":\"2026-11-22\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].amount").value(4500));

        mvc.perform(delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/expenses")).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    @Test
    void rejectsInvalidInput() throws Exception {
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"amount\":0,\"category\":\"카페\",\"spentAt\":null}"))
                .andExpect(status().isBadRequest());
    }
}
