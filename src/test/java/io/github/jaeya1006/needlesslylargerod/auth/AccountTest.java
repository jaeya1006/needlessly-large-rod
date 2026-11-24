package io.github.jaeya1006.needlesslylargerod.auth;

import io.github.jaeya1006.needlesslylargerod.expense.ExpenseRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static io.github.jaeya1006.needlesslylargerod.auth.AuthFlowTest.body;
import static io.github.jaeya1006.needlesslylargerod.auth.AuthFlowTest.cookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:account;DB_CLOSE_DELAY=-1", "JWT_SECRET=test-secret"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AccountTest {

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired ExpenseRepository expenses;

    static RequestPostProcessor from(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }

    MockHttpServletResponse signup(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body(email, password)))
                .andExpect(status().isCreated()).andReturn().getResponse();
    }

    @Test
    void changePasswordCutsOldSessionsKeepsThisOne() throws Exception {
        MockHttpServletResponse first = signup("pw@example.com", "old-password-1");
        Cookie oldAccess = cookie(first, TokenService.ACCESS_COOKIE);
        Cookie oldRefresh = cookie(first, TokenService.REFRESH_COOKIE);

        mvc.perform(put("/api/auth/password").cookie(oldAccess).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isForbidden());

        MockHttpServletResponse changed = mvc.perform(put("/api/auth/password").cookie(oldAccess).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password-1\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse();

        // 바꾸기 전 토큰은 만료 전이어도 막히고, 방금 받은 새 토큰은 된다
        mvc.perform(get("/api/auth/me").cookie(oldAccess)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(oldRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(cookie(changed, TokenService.ACCESS_COOKIE))).andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").with(from("10.1.0.1")).contentType(MediaType.APPLICATION_JSON).content(body("pw@example.com", "old-password-1")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(from("10.1.0.1")).contentType(MediaType.APPLICATION_JSON).content(body("pw@example.com", "new-password-1")))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccountRemovesEverything() throws Exception {
        Cookie access = cookie(signup("bye@example.com", "bye-password-1"), TokenService.ACCESS_COOKIE);
        mvc.perform(post("/api/expenses").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"amount\":1000,\"category\":\"카페\",\"spentAt\":\"2024-01-01\"}")).andExpect(status().isCreated());
        long id = members.findByEmail("bye@example.com").orElseThrow().getId();

        mvc.perform(delete("/api/auth/me").cookie(access).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"nope-nope\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/auth/me").cookie(access).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"bye-password-1\"}"))
                .andExpect(status().isNoContent());

        assertThat(members.findById(id)).isEmpty();
        assertThat(expenses.findByOwnerIdOrderBySpentAtDescIdDesc(id)).isEmpty();
        // 남아 있던 액세스 토큰으로는 아무것도 못 한다(없는 회원 이름으로 저장되는 일도 없음)
        mvc.perform(post("/api/expenses").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"amount\":1000,\"category\":\"카페\",\"spentAt\":\"2024-01-01\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(from("10.2.0.1")).contentType(MediaType.APPLICATION_JSON).content(body("bye@example.com", "bye-password-1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void locksAfterFiveFailuresFromSameIp() throws Exception {
        signup("lock@example.com", "right-password");
        for (int i = 0; i < LoginAttemptLimiter.MAX_PER_ACCOUNT_IP; i++) {
            mvc.perform(post("/api/auth/login").with(from("10.3.0.1")).contentType(MediaType.APPLICATION_JSON).content(body("lock@example.com", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        // 맞는 비밀번호여도 이 IP에서는 잠시 막힘
        mvc.perform(post("/api/auth/login").with(from("10.3.0.1")).contentType(MediaType.APPLICATION_JSON).content(body("lock@example.com", "right-password")))
                .andExpect(status().isTooManyRequests());
        // 다른 IP의 진짜 주인은 로그인 가능(남이 일부러 잠그기 어렵게)
        mvc.perform(post("/api/auth/login").with(from("10.3.0.2")).contentType(MediaType.APPLICATION_JSON).content(body("lock@example.com", "right-password")))
                .andExpect(status().isOk());
    }
}
