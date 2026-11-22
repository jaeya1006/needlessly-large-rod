package io.github.jaeya1006.needlesslylargerod.expense;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class WatchedCategoryControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void replacesWholeList() throws Exception {
        mvc.perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON)
                        .content("[\"카페\",\"교통\",\"카페\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("교통", "카페")));

        mvc.perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[\"술\"]"))
                .andExpect(jsonPath("$", contains("술")));
        mvc.perform(get("/api/watched-categories")).andExpect(jsonPath("$", contains("술")));

        mvc.perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mvc.perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[\" \"]"))
                .andExpect(status().is4xxClientError());
    }
}
