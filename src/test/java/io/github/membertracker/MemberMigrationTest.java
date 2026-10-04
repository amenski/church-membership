package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Runs the real migration files on in-memory H2 (MySQL mode): 001 and 002 for the schema and sample data,
 * then the member migrations. 003 (MySQL multi-column ADD), 004 and 006 (users only) are skipped. The
 * "--rollback" lines are plain SQL comments to the populator, so they are not executed here.
 * H2 passing is not MySQL passing: every migration is also run on MySQL (docs/person-membership-plan.md, section 6).
 */
class MemberMigrationTest {

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:membermigration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(
            new ClassPathResource("db/sql/001.schema-creation.sql"),
            new ClassPathResource("db/sql/002.sample-data.sql"),
            new ClassPathResource("db/sql/005.add-member-last-missed-count-month.sql"),
            new ClassPathResource("db/sql/009.make-member-email-optional.sql")).populate(connection);
    }

    @AfterAll
    static void close() throws SQLException {
        connection.close();
    }

    private static long count(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void insertMember(String name, String email) throws SQLException {
        try (var ps = connection.prepareStatement("INSERT INTO member (name, email, join_date) VALUES (?, ?, CURRENT_DATE)")) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.executeUpdate();
        }
    }

    @Test
    void theTenSampleMembersAreUntouched() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM member WHERE id <= 10 AND email IS NOT NULL")).isEqualTo(10);
    }

    @Test
    void twoMembersCanShareAnEmailAndOneCanHaveNone() throws SQLException {
        insertMember("Parent", "family@example.com");
        insertMember("Spouse", "family@example.com");
        insertMember("Child", null);

        assertThat(count("SELECT COUNT(*) FROM member WHERE email = 'family@example.com'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM member WHERE email IS NULL AND name = 'Child'")).isEqualTo(1);
    }

    @Test
    void theUniqueIndexIsGoneAndTheLookupIndexExists() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'MEMBER' AND INDEX_NAME = 'IDX_MEMBER_EMAIL'"))
            .isZero();
        assertThat(count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'MEMBER' AND INDEX_NAME = 'IDX_MEMBER_EMAIL_LOOKUP'"))
            .isPositive();
    }
}
