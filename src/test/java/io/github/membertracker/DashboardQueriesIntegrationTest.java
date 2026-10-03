package io.github.membertracker;

import com.jayway.jsonpath.JsonPath;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.repository.CommunicationJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.PaymentJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard numbers and lists come from queries; checked on in-memory H2 with the real endpoints.
 * Members: Ann (active, 3 behind), Ben (active, 1 behind), Cy (active, paid up), Dee (inactive, 5 behind).
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboardqueries;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@AutoConfigureMockMvc
class DashboardQueriesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CommunicationRepository communicationRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private CommunicationJpaRepository communicationJpaRepository;

    private Member ann;
    private Member ben;
    private Member cy;

    private Member member(String name, boolean active, int missed) {
        Member m = new Member(name, name.toLowerCase() + "@example.com", "+1234567890");
        m.setActive(active);
        m.setConsecutiveMonthsMissed(missed);
        return memberRepository.save(m);
    }

    private void payment(Member m, YearMonth period, double amount, int daysAgo) {
        Payment p = new Payment(m, period, amount, PaymentMethod.CASH);
        p.setPaymentDate(LocalDate.now().minusDays(daysAgo));
        paymentRepository.save(p);
    }

    @BeforeEach
    void createData() {
        ann = member("Ann", true, 3);
        ben = member("Ben", true, 1);
        cy = member("Cy", true, 0);
        member("Dee", false, 5);

        YearMonth now = YearMonth.now();
        // Three payments for this month (50.5 in total), nine for last month (900.0)
        payment(cy, now, 30.0, 0);
        payment(ann, now, 15.5, 1);
        payment(ben, now, 5.0, 2);
        for (int i = 0; i < 9; i++) {
            payment(cy, now.minusMonths(1), 100.0, 3 + i);
        }

        for (int i = 0; i < 7; i++) {
            Communication c = new Communication();
            c.setTitle("News " + i);
            c.setMessageContent("Body");
            c.setCreatedDate(LocalDateTime.now().minusDays(i));
            communicationRepository.save(c);
        }
    }

    @AfterEach
    void cleanUp() {
        paymentJpaRepository.deleteAll();
        communicationJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private String fetch(String url) throws Exception {
        return mockMvc.perform(get(url).with(user("volunteer@example.com").roles("VOLUNTEER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void statsAreExactAndLeaveOutInactiveMembersAndOtherPeriods() throws Exception {
        String stats = fetch("/api/dashboard/stats");

        assertThat((Integer) JsonPath.read(stats, "$.totalMembers")).isEqualTo(4);
        assertThat((Integer) JsonPath.read(stats, "$.activeMembers")).isEqualTo(3);
        // Ann and Ben; Dee is behind but inactive
        assertThat((Integer) JsonPath.read(stats, "$.overdueMembers")).isEqualTo(2);
        assertThat(JsonPath.<Double>read(stats, "$.monthlyRevenue")).isEqualTo(50.5);
    }

    @Test
    void revenueIsAlwaysADoubleEvenWithNoPayments() throws Exception {
        paymentJpaRepository.deleteAll();

        String stats = fetch("/api/dashboard/stats");

        assertThat(stats).contains("\"monthlyRevenue\":0.0");
    }

    @Test
    void overdueListHoldsActiveMembersOnlyLongestBehindFirst() throws Exception {
        String overdue = fetch("/api/dashboard/overdue-members");

        assertThat(JsonPath.<List<String>>read(overdue, "$[*].name")).containsExactly("Ann", "Ben");
        assertThat(JsonPath.<List<Integer>>read(overdue, "$[*].consecutiveMonthsMissed")).containsExactly(3, 1);
    }

    @Test
    void recentPaymentsAreLimitedToTenNewestFirst() throws Exception {
        String recent = fetch("/api/dashboard/recent-payments");

        List<String> dates = JsonPath.read(recent, "$[*].paymentDate");
        assertThat(dates).hasSize(10).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(dates.get(0)).isEqualTo(LocalDate.now().toString());
    }

    @Test
    void recentActivitiesTakeFivePaymentsAndFiveCommunicationsAtMostTenInTotal() throws Exception {
        String activities = fetch("/api/dashboard/recent-activities");

        List<String> ids = JsonPath.read(activities, "$[*].id");
        assertThat(ids).hasSize(10);
        assertThat(ids.stream().filter(id -> id.startsWith("payment_"))).hasSize(5);
        assertThat(ids.stream().filter(id -> id.startsWith("comm_"))).hasSize(5);
    }

    @Test
    void repositoryQueriesAgreeWithTheEndpoints() {
        assertThat(memberRepository.countAll()).isEqualTo(4);
        assertThat(memberRepository.countByActive(false)).isEqualTo(1);
        assertThat(memberRepository.countActiveWithMissedAtLeast(2)).isEqualTo(1);
        assertThat(memberRepository.findActiveWithMissedAtLeastOrderByMissedDesc(1))
                .extracting(Member::getId).containsExactly(ann.getId(), ben.getId());
        assertThat(paymentRepository.sumAmountByPeriod(YearMonth.now().minusMonths(1))).isEqualTo(900.0);
        assertThat(paymentRepository.sumAmountByPeriod(YearMonth.now().plusMonths(1))).isEqualTo(0.0);
        assertThat(communicationRepository.findRecent(3)).extracting(Communication::getTitle)
                .containsExactly("News 0", "News 1", "News 2");
    }
}
