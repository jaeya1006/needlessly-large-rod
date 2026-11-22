package io.github.jaeya1006.needlesslylargerod.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:authflow;DB_CLOSE_DELAY=-1", "JWT_SECRET=test-secret"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder encoder;

    static String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    static Cookie cookie(MockHttpServletResponse res, String name) {
        return Arrays.stream(res.getCookies()).filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    MockHttpServletResponse signup(String email) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body(email, "password123")))
                .andExpect(status().isCreated()).andReturn().getResponse();
    }

    @Test
    void signupSetsHttpOnlyCookiesAndMeWorks() throws Exception {
        MockHttpServletResponse res = signup("Flow@Example.com");
        Cookie access = cookie(res, TokenService.ACCESS_COOKIE);
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(cookie(res, TokenService.REFRESH_COOKIE).getPath()).isEqualTo("/api/auth");
        assertThat(res.getHeaders("Set-Cookie")).anyMatch(h -> h.contains("SameSite=Strict"));

        mvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("flow@example.com")); // 소문자로 저장

        // 같은 이메일(대소문자만 다름) 다시 가입 → 409
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body("flow@example.com", "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmailTheSameWay() throws Exception {
        signup("login@example.com");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("login@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("nobody@example.com", "password123")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("login@example.com", "password123")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body("bad-email", "password123")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body("short@example.com", "1234567")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotatesAndReuseRevokesEverything() throws Exception {
        Cookie first = cookie(signup("rotate@example.com"), TokenService.REFRESH_COOKIE);

        MockHttpServletResponse rotated = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        Cookie second = cookie(rotated, TokenService.REFRESH_COOKIE);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // 곧바로 같은 토큰이 또 오면 다른 탭의 동시 갱신 → 409, 쿠키를 지우지 않고 새 토큰도 살아 있다
        MockHttpServletResponse grace = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isConflict()).andReturn().getResponse();
        assertThat(grace.getHeaders("Set-Cookie")).isEmpty();

        // 유예 시간이 지난 뒤 옛 토큰이 또 오면 탈취로 보고 새 토큰까지 모두 무효화
        int aged = jdbc.update("UPDATE refresh_token SET rotated_at = ? WHERE rotated_at IS NOT NULL",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));
        assertThat(aged).isPositive();
        mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTooLongPasswordInBytes() throws Exception {
        // 25글자지만 한글이라 75바이트 → BCrypt 한도(72바이트) 초과
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body("long@example.com", "가".repeat(25))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body("long@example.com", "가".repeat(24))))
                .andExpect(status().isCreated());
    }

    @Test
    void blocksCrossSiteWrites() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Origin", "https://evil.example")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").header("Sec-Fetch-Site", "cross-site").contentType(MediaType.APPLICATION_JSON)
                .content(body("x@example.com", "password123"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Origin", "http://localhost")).andExpect(status().isNoContent());
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        signup("issuer@example.com");
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("someone-else").subject("1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").cookie(new Cookie(TokenService.ACCESS_COOKIE, token))).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Cookie refresh = cookie(signup("logout@example.com"), TokenService.REFRESH_COOKIE);
        mvc.perform(post("/api/auth/logout").cookie(refresh)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void apiNeedsLoginButPagesAndAuthEndpointsIgnoreBadCookie() throws Exception {
        Cookie broken = new Cookie(TokenService.ACCESS_COOKIE, "not-a-jwt");
        mvc.perform(get("/api/expenses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/expenses").cookie(broken)).andExpect(status().isUnauthorized());
        mvc.perform(get("/").cookie(broken)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").cookie(broken).contentType(MediaType.APPLICATION_JSON)
                .content(body("nobody2@example.com", "password123"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(broken)).andExpect(status().isUnauthorized()); // 401이지만 쿠키 때문이 아니라 리프레시 토큰이 없어서
    }

    @Test
    void membersSeeOnlyTheirOwnExpenses() throws Exception {
        Cookie a = cookie(signup("a@example.com"), TokenService.ACCESS_COOKIE);
        Cookie b = cookie(signup("b@example.com"), TokenService.ACCESS_COOKIE);
        String created = mvc.perform(post("/api/expenses").cookie(a).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":7000,\"category\":\"카페\",\"spentAt\":\"2023-05-01\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/expenses").param("month", "2023-05").cookie(a)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/expenses").param("month", "2023-05").cookie(b)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/expenses/" + id).cookie(b).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"amount\":1,\"category\":\"카페\",\"spentAt\":\"2023-05-01\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/expenses/" + id).cookie(b)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/expenses/" + id).cookie(a)).andExpect(status().isNoContent());
    }
}
