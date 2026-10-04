package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Runs the real migration files on in-memory H2 (MySQL mode) up to 012, adds members the seed does not have (no email,
 * a shared email, an archived one), then runs 013 and proves the backfill: one person per member with the same id and
 * the same values, every member linked, the constraints in place. The legacy columns still exist at this point (they are dropped by 014).
 */
class PersonBackfillMigrationTest {

    private static final String[] BEFORE = {"001.schema-creation.sql", "002.sample-data.sql",
        "005.add-member-last-missed-count-month.sql", "009.make-member-email-optional.sql", "010.add-member-status.sql",
        "011.payment-delivery-fk-restrict.sql", "012.create-person-household.sql"};

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
        connection = DriverManager.getConnection("jdbc:h2:mem:personbackfill;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        run(BEFORE);
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO member (name, email, phone, join_date) VALUES ('No Email', NULL, NULL, DATE '2026-01-01')");
            st.executeUpdate("INSERT INTO member (name, email, phone, join_date) VALUES ('Mother', 'family@example.org', '+390611', DATE '2026-01-02')");
            st.executeUpdate("INSERT INTO member (name, email, phone, join_date) VALUES ('Father', 'family@example.org', NULL, DATE '2026-01-03')");
            st.executeUpdate("INSERT INTO member (name, email, phone, join_date, status, active, archived_at) "
                + "VALUES ('Archived', 'old@example.org', NULL, DATE '2020-01-01', 'ARCHIVED', FALSE, TIMESTAMP '2026-02-01 10:00:00')");
        }
        run("013.backfill-person-from-member.sql");
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

    private static List<String> strings(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Test
    void everyMemberGetsOnePersonWithTheSameId() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM member")).isEqualTo(14);
        assertThat(count("SELECT COUNT(*) FROM person")).isEqualTo(14);
        assertThat(count("SELECT COUNT(*) FROM member WHERE person_id <> id OR person_id IS NULL")).isZero();
        assertThat(strings("SELECT CAST(id AS VARCHAR(5)) FROM person WHERE id BETWEEN 1 AND 10 ORDER BY id"))
            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    }

    @Test
    void valuesAreCopiedIncludingNullAndSharedEmailsAndTheArchivedMember() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM member m JOIN person p ON p.id = m.person_id "
            + "WHERE m.name = p.name AND m.created_at = p.created_at")).isEqualTo(14);
        assertThat(count("SELECT COUNT(*) FROM person WHERE email IS NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM person WHERE email = 'family@example.org'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM person WHERE name = 'Archived' AND email = 'old@example.org'")).isEqualTo(1);
        assertThat(strings("SELECT phone FROM person WHERE name = 'Mother'")).containsExactly("+390611");
    }

    @Test
    void personValuesEqualTheMemberValuesAfterTheBackfill() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM member m JOIN person p ON p.id = m.person_id "
            + "WHERE NOT (m.name = p.name AND (m.email = p.email OR (m.email IS NULL AND p.email IS NULL)) "
            + "AND (m.phone = p.phone OR (m.phone IS NULL AND p.phone IS NULL)))")).isZero();
    }

    @Test
    void theNextPersonIdContinuesAfterTheBackfill() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO person (name) VALUES ('Next person')");
        }
        assertThat(count("SELECT id FROM person WHERE name = 'Next person'")).isGreaterThan(14);
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("DELETE FROM person WHERE name = 'Next person'");
        }
    }

    @Test
    void personIdIsRequiredUniqueAndProtectedFromDeletingItsPerson() {
        assertThatThrownBy(() -> exec("INSERT INTO member (name, join_date) VALUES ('Linkless', DATE '2026-01-01')"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("INSERT INTO member (name, join_date, person_id) VALUES ('Twin', DATE '2026-01-01', 1)"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> exec("DELETE FROM person WHERE id = 1")).isInstanceOf(SQLException.class);
    }

    private static void exec(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    @Test
    void theSeedPaymentsAreUntouched() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM payment")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM payment x LEFT JOIN member m ON m.id = x.member_id WHERE m.id IS NULL")).isZero();
    }
}
