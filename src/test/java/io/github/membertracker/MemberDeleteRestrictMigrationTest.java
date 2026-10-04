package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Runs the real migration files on in-memory H2 (MySQL mode): 001, 002 (seed data with payments and deliveries), 005,
 * 009, 010 and 011, then proves the database refuses to delete a member who has payments or deliveries. 003 (MySQL
 * multi-column ADD), 004 and 006 (users only) are skipped.
 */
class MemberDeleteRestrictMigrationTest {

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
        connection = DriverManager.getConnection("jdbc:h2:mem:memberdeleterestrict;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        run("001.schema-creation.sql", "002.sample-data.sql", "005.add-member-last-missed-count-month.sql",
            "009.make-member-email-optional.sql", "010.add-member-status.sql", "011.payment-delivery-fk-restrict.sql");
    }

    @AfterAll
    static void close() throws SQLException {
        connection.close();
    }

    private static long count(String sql) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long firstMember(String where) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM member WHERE " + where + " ORDER BY id LIMIT 1")) {
            assertThat(rs.next()).as(where).isTrue();
            return rs.getLong(1);
        }
    }

    @Test
    void aRawDeleteOfAMemberWithPaymentsFailsAndKeepsEverything() throws SQLException {
        long id = firstMember("id IN (SELECT member_id FROM payment)");
        long payments = count("SELECT COUNT(*) FROM payment");

        assertThatThrownBy(() -> {
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM member WHERE id = " + id);
            }
        }).isInstanceOf(SQLException.class);

        assertThat(count("SELECT COUNT(*) FROM member WHERE id = " + id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payment")).isEqualTo(payments);
    }

    @Test
    void aRawDeleteOfAMemberWithOnlyDeliveriesAlsoFails() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO member (name, join_date) VALUES ('Only deliveries', CURRENT_DATE)");
        }
        long id = firstMember("name = 'Only deliveries'");
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO message_delivery (recipient_id, communication_id, status, channel) "
                + "SELECT " + id + ", MIN(id), 'SENT', 'EMAIL' FROM communication");
        }

        assertThatThrownBy(() -> {
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM member WHERE id = " + id);
            }
        }).isInstanceOf(SQLException.class);
        assertThat(count("SELECT COUNT(*) FROM message_delivery WHERE recipient_id = " + id)).isEqualTo(1);
    }

    @Test
    void aMemberWithNoPaymentsAndNoDeliveriesCanStillBeDeleted() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO member (name, join_date) VALUES ('Typo', CURRENT_DATE)");
        }
        long id = firstMember("name = 'Typo'");

        try (Statement st = connection.createStatement()) {
            assertThat(st.executeUpdate("DELETE FROM member WHERE id = " + id)).isEqualTo(1);
        }
        assertThat(count("SELECT COUNT(*) FROM member WHERE id = " + id)).isZero();
    }
}
