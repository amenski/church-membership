package io.github.membertracker;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.PaymentJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/me/dues returns only the member whose email is the signed-in user's, and never guesses. */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:mydues;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@AutoConfigureMockMvc
class MyDuesIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;

    private Member alice;
    private Member bob;

    @BeforeEach
    void create() {
        alice = memberRepository.save(new Member("Alice Alem", "alice@example.com", "+390612345601"));
        bob = memberRepository.save(new Member("Bob Bekele", "bob@example.com", "+390612345602"));
        paymentRepository.save(new Payment(alice, YearMonth.now(), 25.0, PaymentMethod.CASH));
        paymentRepository.save(new Payment(bob, YearMonth.now(), 99.0, PaymentMethod.CHECK));
    }

    @AfterEach
    void cleanUp() {
        paymentJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    @Test
    void aUserGetsOnlyTheirOwnMemberAndPayments() throws Exception {
        mockMvc.perform(get("/api/me/dues").with(user("alice@example.com").roles("MEMBER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId").value(alice.getId()))
            .andExpect(jsonPath("$.name").value("Alice Alem"))
            .andExpect(jsonPath("$.paidMonths[0]").value(YearMonth.now().toString()))
            .andExpect(jsonPath("$.payments.length()").value(1))
            .andExpect(jsonPath("$.payments[0].amount").value(25.0))
            .andExpect(jsonPath("$.payments[0].receiptNumber").value(org.hamcrest.Matchers.matchesPattern("R-\\d{6}")))
            .andExpect(content().string(not(containsString("Bob"))))
            .andExpect(content().string(not(containsString("bob@example.com"))));
    }

    @Test
    void theEmailMatchIgnoresCaseAndSpaces() throws Exception {
        mockMvc.perform(get("/api/me/dues").with(user(" ALICE@Example.com ").roles("MEMBER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId").value(alice.getId()));
    }

    @Test
    void noMatchingMemberIsAnEmpty404() throws Exception {
        mockMvc.perform(get("/api/me/dues").with(user("nobody@example.com").roles("MEMBER")))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    @Test
    void twoMembersSharingTheEmailIsA404ForBothAndExposesNothing() throws Exception {
        memberRepository.save(new Member("Alice Husband", "alice@example.com", "+390612345603"));

        mockMvc.perform(get("/api/me/dues").with(user("alice@example.com").roles("MEMBER")))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    @Test
    void anArchivedMemberIsA404() throws Exception {
        alice.archive(java.time.LocalDateTime.now());
        memberRepository.save(alice);

        mockMvc.perform(get("/api/me/dues").with(user("alice@example.com").roles("MEMBER")))
            .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(
            memberRepository.findById(alice.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.ARCHIVED);
    }

    @Test
    void anAnonymousRequestIsA401() throws Exception {
        mockMvc.perform(get("/api/me/dues")).andExpect(status().isUnauthorized());
    }

    @Test
    void aStaffUserWithAMatchingMemberGetsOnlyTheirOwn() throws Exception {
        mockMvc.perform(get("/api/me/dues").with(user("bob@example.com").roles("STAFF")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId").value(bob.getId()))
            .andExpect(jsonPath("$.payments.length()").value(1))
            .andExpect(content().string(not(containsString("Alice"))));
    }
}
