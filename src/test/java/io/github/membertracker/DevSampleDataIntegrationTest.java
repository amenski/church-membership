package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The dev profile on a fresh in-memory H2: application-dev.properties activates the dev Liquibase context, so the
 * sample users, members, payments and messages (002.sample-data.sql) are loaded, and the later migrations that act on
 * them (the USER to MEMBER role change, the person backfill) have run over them.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:devsampledata;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.change-log=classpath:db/h2-master.xml"
    })
@ActiveProfiles("dev")
class DevSampleDataIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void theSampleDataIsPresent() {
        assertThat(count("SELECT COUNT(*) FROM users WHERE email IN "
            + "('admin@membertracker.com', 'testuser@membertracker.com')")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM member")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM payment")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM communication")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM message_delivery")).isPositive();
    }

    @Test
    void theMigrationsAfterTheSampleDataRanOverIt() {
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'USER'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM person")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE id = 'sample-data'")).isEqualTo(1);
    }
}
