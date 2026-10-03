package io.github.membertracker;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.PaymentJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payment.period column is VARCHAR(7) holding YYYY-MM text (see 001.schema-creation.sql). H2 builds the
 * column from the entity, so these tests read and write the column as text through JDBC to catch a missing converter.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:paymentperiod;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
class PaymentPeriodPersistenceTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        paymentJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    @Test
    void periodColumnIsText() {
        String type = jdbc.queryForObject(
            "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'payment' AND LOWER(COLUMN_NAME) = 'period'",
            String.class);
        assertThat(type).isEqualTo("CHARACTER VARYING");
    }

    @Test
    void readsATextRowAndStoresNewPeriodsAsText() {
        Member member = memberRepository.save(new Member("Period Test", "period@example.com", "+1234567890"));
        jdbc.update("INSERT INTO payment (member_id, period, payment_date, amount, payment_method) "
            + "VALUES (?, '2023-01', '2023-01-15', 25.0, 'CASH')", member.getId());

        List<Payment> all = paymentRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getPeriod()).isEqualTo(YearMonth.of(2023, 1));

        paymentRepository.save(new Payment(member, YearMonth.of(2026, 10), 50.0, PaymentMethod.CASH));

        List<String> raw = jdbc.queryForList("SELECT period FROM payment ORDER BY id", String.class);
        assertThat(raw).containsExactly("2023-01", "2026-10");
        assertThat(paymentRepository.existsByMemberAndPeriod(member, YearMonth.of(2026, 10))).isTrue();
        assertThat(paymentRepository.existsByMemberAndPeriod(member, YearMonth.of(2026, 11))).isFalse();
    }
}
