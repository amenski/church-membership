package io.github.membertracker;

import io.github.membertracker.domain.enumeration.MemberStatus;
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
 * Members: Ann (MEMBER, 3 behind), Ben (MEMBER, 1 behind), Cy (MEMBER, paid up), Dee (INACTIVE, 5 behind), Eve
 * (DECEASED, 2 behind), Fay (TRANSFERRED, 4 behind), Gus (ARCHIVED, 6 behind). Only the first three count for dues;
 * Gus is in no number at all and the total is everyone else (six).
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

    private Member member(String name, MemberStatus status, int missed) {
        Member m = new Member(name, name.toLowerCase() + "@example.com", "+1234567890");
        m.setStatus(status);
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
        ann = member("Ann", MemberStatus.MEMBER, 3);
        ben = member("Ben", MemberStatus.MEMBER, 1);
        cy = member("Cy", MemberStatus.MEMBER, 0);
        member("Dee", MemberStatus.INACTIVE, 5);
        member("Eve", MemberStatus.DECEASED, 2);
        member("Fay", MemberStatus.TRANSFERRED, 4);
        member("Gus", MemberStatus.ARCHIVED, 6);

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
    void statsAreExactAndLeaveOutNonDuesMembersArchivedOnesAndOtherPeriods() throws Exception {
        String stats = fetch("/api/dashboard/stats");

        assertThat((Integer) JsonPath.read(stats, "$.totalMembers")).isEqualTo(6);
        assertThat((Integer) JsonPath.read(stats, "$.activeMembers")).isEqualTo(3);
        // Ann and Ben; Dee, Eve and Fay are behind but not dues-paying and Gus is archived
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
        assertThat(memberRepository.countNotArchived()).isEqualTo(6);
        assertThat(memberRepository.countDuesPaying()).isEqualTo(3);
        assertThat(memberRepository.countDuesPayingWithMissedAtLeast(2)).isEqualTo(1);
        assertThat(memberRepository.findDuesPayingWithMissedAtLeastOrderByMissedDesc(1))
                .extracting(Member::getId).containsExactly(ann.getId(), ben.getId());
        assertThat(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(1))
                .extracting(Member::getName).containsExactly("Ann", "Ben");
        assertThat(memberRepository.findAll()).extracting(Member::getName)
                .containsExactly("Ann", "Ben", "Cy", "Dee", "Eve", "Fay");
        assertThat(memberRepository.findByStatus(MemberStatus.ARCHIVED)).extracting(Member::getName).containsExactly("Gus");
        assertThat(paymentRepository.sumAmountByPeriod(YearMonth.now().minusMonths(1))).isEqualTo(900.0);
        assertThat(paymentRepository.sumAmountByPeriod(YearMonth.now().plusMonths(1))).isEqualTo(0.0);
        assertThat(communicationRepository.findRecent(3)).extracting(Communication::getTitle)
                .containsExactly("News 0", "News 1", "News 2");
    }
}
