package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Runs the real migration files on in-memory H2 (MySQL mode): 001, 002 (the seed data), 005 and 009, then a few
 * members are added the way an older version of the app would have, then 010 runs on top. 003 (MySQL multi-column
 * ADD), 004 and 006 (users only) are skipped. The "--rollback" lines are plain SQL comments to the populator.
 */
class MemberStatusMigrationTest {

    private static Connection connection;

    private static void run(String... files) throws SQLException {
        ClassPathResource[] resources = new ClassPathResource[files.length];
        for (int i = 0; i < files.length; i++) {
            resources[i] = new ClassPathResource("db/sql/" + files[i]);
        }
        new ResourceDatabasePopulator(resources).populate(connection);
    }

    @BeforeAll
    static void migrate() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:memberstatusmigration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        run("001.schema-creation.sql", "002.sample-data.sql", "005.add-member-last-missed-count-month.sql",
            "009.make-member-email-optional.sql");
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO member (name, email, join_date, active) VALUES ('Left', 'left@example.com', CURRENT_DATE, FALSE)");
            st.executeUpdate("INSERT INTO member (name, email, join_date, active) VALUES ('Unknown', NULL, CURRENT_DATE, NULL)");
        }
        run("010.add-member-status.sql");
    }

    @AfterAll
    static void close() throws SQLException {
        connection.close();
    }

    private static Map<Long, String> statuses() throws SQLException {
        Map<Long, String> result = new LinkedHashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, status FROM member ORDER BY id")) {
            while (rs.next()) {
                result.put(rs.getLong(1), rs.getString(2));
            }
        }
        return result;
    }

    private static long count(String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void inTheRealSeedDataMembers7And9AreInactiveAndTheOtherEightAreMembers() throws SQLException {
        Map<Long, String> seed = statuses();

        assertThat(count("SELECT COUNT(*) FROM member WHERE id <= 10")).isEqualTo(10);
        for (long id = 1; id <= 10; id++) {
            assertThat(seed.get(id)).as("member %d", id).isEqualTo(id == 7 || id == 9 ? "INACTIVE" : "MEMBER");
        }
    }

    @Test
    void everyRowWithActiveFalseOrUnknownBecomesInactive() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM member WHERE name = 'Left' AND status = 'INACTIVE'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM member WHERE name = 'Unknown' AND status = 'INACTIVE'")).isEqualTo(1);
    }

    @Test
    void statusAndActiveAgreeOnEveryRowAfterTheBackfill() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM member WHERE (status = 'MEMBER') <> COALESCE(active, FALSE)")).isZero();
    }

    @Test
    void aRowInsertedWithoutAStatusIsAMemberAndNotArchived() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO member (name, join_date) VALUES ('Default', CURRENT_DATE)");
        }

        assertThat(count("SELECT COUNT(*) FROM member WHERE name = 'Default' AND status = 'MEMBER' AND archived_at IS NULL"))
            .isEqualTo(1);
    }

    @Test
    void theStatusIndexExists() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'MEMBER' AND INDEX_NAME = 'IDX_MEMBER_STATUS'"))
            .isPositive();
    }
}
