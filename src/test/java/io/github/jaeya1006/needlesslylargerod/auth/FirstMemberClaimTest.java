package io.github.jaeya1006.needlesslylargerod.auth;

import io.github.jaeya1006.needlesslylargerod.benefit.UserProfile;
import io.github.jaeya1006.needlesslylargerod.benefit.UserProfileRepository;
import io.github.jaeya1006.needlesslylargerod.expense.Expense;
import io.github.jaeya1006.needlesslylargerod.expense.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 빈 DB에서 시작해야 '첫 회원'이 확실하다
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:firstmember;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class FirstMemberClaimTest {

    @Autowired MockMvc mvc;
    @Autowired ExpenseRepository expenses;
    @Autowired UserProfileRepository profiles;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;

    @Test
    void firstMemberGetsLegacyDataSecondDoesNot() throws Exception {
        // 로그인 기능 전 데이터 흉내: 주인 없는 지출, 예전 체크 테이블, 예전 조건(id=1)
        expenses.save(new Expense(null, Expense.Type.EXPENSE, "옛날 지출", 1000, "식비", null, null, LocalDate.of(2025, 1, 1)));
        jdbc.execute("CREATE TABLE watched_category (category VARCHAR(30) PRIMARY KEY)");
        jdbc.update("INSERT INTO watched_category VALUES ('교통'), ('카페')");
        // 예전 조건 줄(id=1)은 회원이 생기기 전 데이터라 외래 키를 잠깐 끄고 넣는다(실제 DB의 옛 행 흉내)
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbc.update("INSERT INTO user_profile (id, birth_year, region, status) VALUES (1, 2001, '서울', '학생')");
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"first@example.com\",\"password\":\"password123\"}")).andExpect(status().isCreated());
        long first = members.findByEmail("first@example.com").orElseThrow().getId();

        assertThat(expenses.findByOwnerIdOrderBySpentAtDescIdDesc(first)).extracting(Expense::getTitle).containsExactly("옛날 지출");
        assertThat(jdbc.queryForList("SELECT category FROM member_watched_category WHERE owner_id = ? ORDER BY category", String.class, first))
                .containsExactly("교통", "카페");
        assertThat(profiles.findById(first)).map(UserProfile::getRegion).contains("서울");

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"second@example.com\",\"password\":\"password123\"}")).andExpect(status().isCreated());
        long second = members.findByEmail("second@example.com").orElseThrow().getId();
        assertThat(expenses.findByOwnerIdOrderBySpentAtDescIdDesc(second)).isEmpty();
    }
}
