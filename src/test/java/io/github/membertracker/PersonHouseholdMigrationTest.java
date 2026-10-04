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
 * Runs the real migration files on in-memory H2 (MySQL mode): 001, 002, 005, 009, 010, 011 and 012, then proves the
 * person and household tables exist, are empty, and carry the foreign key and indexes the plan asks for.
 */
class PersonHouseholdMigrationTest {

    private static Connection connection;

    @BeforeAll
    static void migrate() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:personhousehold;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        String[] files = {"001.schema-creation.sql", "002.sample-data.sql", "005.add-member-last-missed-count-month.sql",
            "009.make-member-email-optional.sql", "010.add-member-status.sql", "011.payment-delivery-fk-restrict.sql",
            "012.create-person-household.sql"};
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
    void bothTablesExistAndAreEmpty() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM household")).isZero();
        assertThat(count("SELECT COUNT(*) FROM person")).isZero();
    }

    @Test
    void personHasTheIndexesAndHouseholdForeignKey() throws SQLException {
        assertThat(strings("SELECT DISTINCT UPPER(INDEX_NAME) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE UPPER(TABLE_NAME) = 'PERSON'")).contains("IDX_PERSON_EMAIL", "IDX_PERSON_HOUSEHOLD");
        assertThat(strings("SELECT UPPER(CONSTRAINT_NAME) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
            + "WHERE UPPER(TABLE_NAME) = 'PERSON' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"))
            .containsExactly("FK_PERSON_HOUSEHOLD");
    }

    @Test
    void emailIsNotUniqueAndDeletingAHouseholdKeepsItsPeople() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("INSERT INTO household (name) VALUES ('Tesfaye family')");
            st.executeUpdate("INSERT INTO person (name, email, household_id) "
                + "SELECT 'Mother', 'shared@example.org', MAX(id) FROM household");
            st.executeUpdate("INSERT INTO person (name, email, birth_date) VALUES ('Child', 'shared@example.org', DATE '2015-05-01')");
            st.executeUpdate("DELETE FROM household");
        }
        assertThat(count("SELECT COUNT(*) FROM person WHERE email = 'shared@example.org'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM person WHERE household_id IS NOT NULL")).isZero();
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("DELETE FROM person");
        }
    }
}
