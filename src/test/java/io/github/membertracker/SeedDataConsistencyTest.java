package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryChannel;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Runs the real schema and sample-data scripts on in-memory H2 (MySQL mode) and checks that every
 * enum-typed value in the seed data is a constant the code can read.
 */
class SeedDataConsistencyTest {

    private static Connection connection;

    @BeforeAll
    static void loadScripts() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:seedconsistency;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/sql/001.schema-creation.sql"),
            new ClassPathResource("db/sql/002.sample-data.sql"));
        populator.populate(connection);
    }

    @AfterAll
    static void close() throws SQLException {
        connection.close();
    }

    private static List<String> distinct(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static <E extends Enum<E>> void assertAllConstantsOf(List<String> values, Class<E> type) {
        List<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        assertThat(values).as("seed values for %s", type.getSimpleName()).isNotEmpty().isSubsetOf(names);
        values.forEach(v -> Enum.valueOf(type, v));
    }

    @Test
    void paymentMethodsAreKnown() throws SQLException {
        assertAllConstantsOf(distinct("SELECT DISTINCT payment_method FROM payment"), PaymentMethod.class);
    }

    @Test
    void communicationTypesAreKnown() throws SQLException {
        assertAllConstantsOf(distinct("SELECT DISTINCT type FROM communication"), CommunicationType.class);
    }

    @Test
    void deliveryStatusesAreKnown() throws SQLException {
        assertAllConstantsOf(distinct("SELECT DISTINCT status FROM message_delivery"), DeliveryStatus.class);
    }

    @Test
    void deliveryChannelsAreKnown() throws SQLException {
        assertAllConstantsOf(distinct("SELECT DISTINCT channel FROM message_delivery"), DeliveryChannel.class);
    }

    @Test
    void userRolesAreKnownOnceMigration004Ran() throws SQLException {
        Function<String, String> migration004 = role -> "USER".equals(role) ? "MEMBER" : role;
        List<String> roles = distinct("SELECT DISTINCT role FROM users").stream().map(migration004).distinct().toList();
        assertAllConstantsOf(roles, UserRole.class);
    }

    @Test
    void paymentPeriodsAreYearMonthText() throws SQLException {
        List<String> periods = distinct("SELECT DISTINCT period FROM payment");
        assertThat(periods).isNotEmpty().allMatch(p -> p.matches("\\d{4}-\\d{2}"));
    }
}
