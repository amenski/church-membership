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

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** The overdue-counter month must survive both member mappers (MemberDbRepository and PaymentDbRepository). */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:membercounter;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
class MemberCounterPersistenceTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;

    @AfterEach
    void cleanUp() {
        paymentJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    @Test
    void lastMissedCountMonthRoundTripsAndSurvivesASavedPayment() {
        Member member = new Member("Counter Test", "counter@example.com", "+1234567890");
        member.setLastMissedCountMonth(YearMonth.of(2026, 9));
        member.setConsecutiveMonthsMissed(1);

        Member saved = memberRepository.save(member);
        Member reloaded = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(reloaded.getConsecutiveMonthsMissed()).isEqualTo(1);

        paymentRepository.save(new Payment(reloaded, YearMonth.of(2026, 9), 25.0, PaymentMethod.CASH));

        Member afterPayment = memberRepository.findById(saved.getId()).orElseThrow();
        assertThat(afterPayment.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(afterPayment.getConsecutiveMonthsMissed()).isEqualTo(1);
        Payment loadedPayment = paymentRepository.findAll().get(0);
        assertThat(loadedPayment.getMember().getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void neverCountedMemberKeepsANullMonth() {
        Member saved = memberRepository.save(new Member("Fresh", "fresh@example.com", "+1234567890"));

        assertThat(memberRepository.findById(saved.getId()).orElseThrow().getLastMissedCountMonth()).isNull();
    }
}
