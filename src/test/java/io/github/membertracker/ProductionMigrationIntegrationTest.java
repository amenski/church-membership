package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The default (production) configuration on a fresh in-memory H2: Liquibase applies every schema and data migration
 * but not the sample data, which is tagged with the dev context (002.sample-data.sql). The changelog is the real files
 * in the real order (db/h2-master.xml swaps only 003, whose multi-column ALTER H2 rejects). Nothing here sets
 * spring.liquibase.contexts, so this proves the value in application.properties.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:productionmigration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.change-log=classpath:db/h2-master.xml"
    })
class ProductionMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @Test
    void everyMigrationIsAppliedExceptTheSampleData() {
        List<String> applied = jdbc.queryForList("SELECT id FROM DATABASECHANGELOG ORDER BY orderexecuted", String.class);

        assertThat(applied).contains("schema-creation", "migrate-user-role-to-member", "person-backfill",
            "member-drop-active").doesNotContain("sample-data");
    }

    @Test
    void thereAreNoSampleRowsAndNoUsers() {
        for (String table : List.of("users", "member", "person", "payment", "communication", "message_delivery",
            "activity_log")) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void theSchemaIsCompleteAndEmptyTablesAreUsable() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(table_name) = 'member' "
                + "AND LOWER(column_name) IN ('name', 'email', 'phone', 'active')", Long.class)).isZero();
    }
}
