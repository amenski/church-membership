package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Runs the real migration files on in-memory H2 (MySQL mode) through 014 and proves the contract step: the legacy
 * name, email, phone and active columns and the email lookup index are gone, the seed members and their persons are
 * untouched, and a member still saves with only the columns that remain. The "--rollback" lines are plain SQL comments
 * to the populator; the MySQL-only rollback (UPDATE ... JOIN) is checked on a copy of the demo database (plan section 6).
 */
class MemberLegacyColumnsMigrationTest {

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:memberlegacycolumns;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        String[] files = {"001.schema-creation.sql", "002.sample-data.sql", "005.add-member-last-missed-count-month.sql",
            "009.make-member-email-optional.sql", "010.add-member-status.sql", "011.payment-delivery-fk-restrict.sql",
            "012.create-person-household.sql", "013.backfill-person-from-member.sql",
            "014.drop-legacy-member-columns.sql"};
        ClassPathResource[] resources = new ClassPathResource[files.length];
        for (int i = 0; i < files.length; i++) {
            resources[i] = new ClassPathResource("db/sql/" + files[i]);
        }
        new ResourceDatabasePopulator(resources).populate(connection);
    }

    @AfterAll
    static void close() throws SQLException {
        connection.close();
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
    void theLegacyColumnsAreGoneAndTheOthersRemain() throws SQLException {
        List<String> columns = strings("SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_name) = 'member'");

        assertThat(columns).doesNotContain("name", "email", "phone", "active");
        assertThat(columns).contains("id", "person_id", "status", "join_date", "consecutive_months_missed");
    }

    @Test
    void theEmailLookupIndexIsGoneAndTheOtherIndexesRemain() throws SQLException {
        List<String> indexes = strings("SELECT LOWER(index_name) FROM information_schema.indexes WHERE LOWER(table_name) = 'member'");

        assertThat(indexes).doesNotContain("idx_member_email_lookup", "idx_member_email");
        assertThat(indexes).contains("idx_member_status", "idx_member_person");
    }

    @Test
    void theSeedMembersKeepTheirPersonsAndValues() throws SQLException {
        assertThat(strings("SELECT CAST(COUNT(*) AS VARCHAR(5)) FROM member m JOIN person p ON p.id = m.person_id WHERE m.id <= 10"))
            .containsExactly("10");
        assertThat(strings("SELECT status FROM member WHERE id IN (7, 9) ORDER BY id")).containsExactly("INACTIVE", "INACTIVE");
        assertThat(strings("SELECT name FROM person WHERE id = 1")).hasSize(1);
    }

    @Test
    void aMemberSavesWithOnlyTheRemainingColumns() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO person (name) VALUES ('After contract')");
            st.executeUpdate("INSERT INTO member (join_date, person_id) "
                + "SELECT DATE '2026-01-01', id FROM person WHERE name = 'After contract'");
        }
        assertThat(strings("SELECT status FROM member m JOIN person p ON p.id = m.person_id WHERE p.name = 'After contract'"))
            .containsExactly("MEMBER");
    }
}
