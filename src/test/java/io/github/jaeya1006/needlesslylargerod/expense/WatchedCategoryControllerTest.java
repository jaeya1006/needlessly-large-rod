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

    /** 로그인한 회원 102 으로 요청 */
    static final RequestPostProcessor ME = jwt().jwt(j -> j.subject("102"));

    ResultActions perform(MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.with(ME));
    }

    @Test
    void replacesWholeList() throws Exception {
        perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON)
                        .content("[\"카페\",\"교통\",\"카페\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("교통", "카페")));

        perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[\"술\"]"))
                .andExpect(jsonPath("$", contains("술")));
        perform(get("/api/watched-categories")).andExpect(jsonPath("$", contains("술")));

        perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void rejectsBlankName() throws Exception {
        perform(put("/api/watched-categories").contentType(MediaType.APPLICATION_JSON).content("[\" \"]"))
                .andExpect(status().is4xxClientError());
    }
}
