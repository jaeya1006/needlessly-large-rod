package io.github.jaeya1006.needlesslylargerod;

import org.springframework.jdbc.core.JdbcTemplate;

/** jwt() 가짜 로그인으로 쓰는 회원 id를 실제 member 줄로 만들어 둔다(외래 키 때문에 필요) */
public final class TestMembers {

    private TestMembers() {
    }

    public static void ensure(JdbcTemplate jdbc, long... ids) {
        for (long id : ids) {
            jdbc.update("MERGE INTO member (id, email, password_hash) KEY (id) VALUES (?, ?, 'not-used')",
                    id, "test" + id + "@example.com");
        }
    }
}
