package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.PaymentJpaRepository;
import io.github.membertracker.usecase.SaveMemberUseCase;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * GET /api/payments/page, /paid-months and /summary on in-memory H2 with the real controller, use cases and
 * queries. Nine payments: Abebe Kebede (3), Berhane Tesfaye (2), Chaltu Abera (1), Dawit Kebede (1) and Gus Archived
 * (1, an ARCHIVED member), none of them for Nopay Nobody.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:paymentpaging;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true"
    })
@AutoConfigureMockMvc
class PaymentPagingIntegrationTest {

    private static final YearMonth NOW = YearMonth.now();

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private SaveMemberUseCase saveMember;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;

    private Member abebe;
    private Member berhane;
    private Member chaltu;
    private Member dawit;
    private Member gus;
    private Member nopay;
    private Payment chaltuPayment;

    @BeforeEach
    void seed() {
        abebe = saveMember.invoke("Abebe Kebede", "abebe@example.org", "+390611", null, null);
        berhane = saveMember.invoke("Berhane Tesfaye", "berhane@example.org", "+390622", null, null);
        chaltu = saveMember.invoke("Chaltu Abera", "chaltu@example.org", "+390633", null, null);
        dawit = saveMember.invoke("Dawit Kebede", "dawit@example.org", "+390644", null, null);
        gus = saveMember.invoke("Gus Archived", "gus@example.org", "+390655", null, null);
        nopay = saveMember.invoke("Nopay Nobody", "nopay@example.org", "+390666", null, null);
        gus.setStatus(MemberStatus.ARCHIVED);
        memberRepository.save(gus);

        // Paid-on days ago are all different, so "paymentDate desc" is: Abebe now, Berhane now, Gus now, ...
        pay(abebe, NOW, 10.0, PaymentMethod.CASH, 1);
        pay(berhane, NOW, 30.0, PaymentMethod.BANK_TRANSFER, 2);
        pay(gus, NOW, 7.0, PaymentMethod.CASH, 3);
        chaltuPayment = pay(chaltu, NOW.minusMonths(1), 20.0, PaymentMethod.CASH, 4);
        pay(abebe, NOW.minusMonths(1), 10.0, PaymentMethod.CASH, 5);
        pay(berhane, NOW.minusMonths(2), 30.0, PaymentMethod.CHECK, 6);
        pay(dawit, NOW.minusMonths(3), 5.0, PaymentMethod.CASH, 7);
        pay(abebe, NOW.minusMonths(12), 10.0, PaymentMethod.BANK_TRANSFER, 8);
        pay(berhane, NOW.minusMonths(14), 25.0, PaymentMethod.CASH, 9);
    }

    @AfterEach
    void cleanUp() {
        paymentJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private Payment pay(Member member, YearMonth period, double amount, PaymentMethod method, int daysAgo) {
        Payment payment = new Payment(member, period, amount, method);
        payment.setPaymentDate(LocalDate.now().minusDays(daysAgo));
        return paymentRepository.save(payment);
    }

    private static RequestPostProcessor as(String role) {
        return user(role.toLowerCase() + "@example.org").roles(role);
    }

    private String body(String url, String role) throws Exception {
        return mockMvc.perform(get(url).with(as(role))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String search(String text) throws Exception {
        return mockMvc.perform(get("/api/payments/page").param("search", text).param("size", "50").param("sort", "member,asc")
                .with(as("VOLUNTEER")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> memberNames(String json) {
        return JsonPath.read(json, "$.content[*].member.name");
    }

    private void assertBadRequest(String url, String field) throws Exception {
        mockMvc.perform(get(url).with(as("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").isNotEmpty());
    }

    @Test
    void pagingReturnsTheSliceAndTheTotals() throws Exception {
        String first = body("/api/payments/page?page=0&size=4", "VOLUNTEER");
        assertThat((Integer) JsonPath.read(first, "$.page")).isZero();
        assertThat((Integer) JsonPath.read(first, "$.size")).isEqualTo(4);
        assertThat(((Number) JsonPath.read(first, "$.totalElements")).longValue()).isEqualTo(9);
        assertThat((Integer) JsonPath.read(first, "$.totalPages")).isEqualTo(3);
        assertThat((List<?>) JsonPath.read(first, "$.content")).hasSize(4);

        String second = body("/api/payments/page?page=1&size=4", "VOLUNTEER");
        assertThat((Integer) JsonPath.read(second, "$.page")).isEqualTo(1);
        assertThat((List<?>) JsonPath.read(second, "$.content")).hasSize(4);

        String last = body("/api/payments/page?page=2&size=4", "VOLUNTEER");
        assertThat((List<?>) JsonPath.read(last, "$.content")).hasSize(1);
        assertThat(((Number) JsonPath.read(last, "$.totalElements")).longValue()).isEqualTo(9);

        String beyond = body("/api/payments/page?page=7&size=4", "VOLUNTEER");
        assertThat((List<?>) JsonPath.read(beyond, "$.content")).isEmpty();
        assertThat(((Number) JsonPath.read(beyond, "$.totalElements")).longValue()).isEqualTo(9);
        assertThat((Integer) JsonPath.read(beyond, "$.totalPages")).isEqualTo(3);
    }

    @Test
    void theDefaultsAreFirstPageOf25NewestPaidFirstAndAnItemHasTheListShape() throws Exception {
        String json = body("/api/payments/page", "VOLUNTEER");

        assertThat((Integer) JsonPath.read(json, "$.page")).isZero();
        assertThat((Integer) JsonPath.read(json, "$.size")).isEqualTo(25);
        assertThat((Integer) JsonPath.read(json, "$.totalPages")).isEqualTo(1);
        assertThat(memberNames(json)).containsExactly("Abebe Kebede", "Berhane Tesfaye", "Gus Archived", "Chaltu Abera",
            "Abebe Kebede", "Berhane Tesfaye", "Dawit Kebede", "Abebe Kebede", "Berhane Tesfaye");

        String list = body("/api/payments", "VOLUNTEER");
        assertThat((List<?>) JsonPath.read(list, "$")).as("the old list is still a plain array").hasSize(9);
        Object pageItem = JsonPath.read(json, "$.content[0]");
        long id = ((Number) JsonPath.read(json, "$.content[0].id")).longValue();
        List<Object> sameInList = JsonPath.read(list, "$[?(@.id == " + id + ")]");
        assertThat(pageItem).as("a page item is the list item").isEqualTo(sameInList.get(0));
    }

    @Test
    void sizeAndPageAreChecked() throws Exception {
        assertBadRequest("/api/payments/page?size=101", "size");
        assertBadRequest("/api/payments/page?size=0", "size");
        assertBadRequest("/api/payments/page?page=-1", "page");
        mockMvc.perform(get("/api/payments/page?size=abc").with(as("ADMIN"))).andExpect(status().isBadRequest());

        String capped = body("/api/payments/page?size=100", "VOLUNTEER");
        assertThat((Integer) JsonPath.read(capped, "$.size")).isEqualTo(100);
    }

    @Test
    void sortByAmountPeriodAndMemberWithTheNewestIdAsTheTieBreak() throws Exception {
        List<Number> amounts = JsonPath.read(body("/api/payments/page?sort=amount,asc", "VOLUNTEER"), "$.content[*].amount");
        assertThat(amounts).extracting(Number::doubleValue).containsExactly(5.0, 7.0, 10.0, 10.0, 10.0, 20.0, 25.0, 30.0, 30.0);

        String ties = body("/api/payments/page?sort=amount,desc", "VOLUNTEER");
        List<Number> ids = JsonPath.read(ties, "$.content[*].id");
        // The two 30.0 payments and the three 10.0 payments: higher id first inside each group.
        assertThat(ids.get(0).longValue()).isGreaterThan(ids.get(1).longValue());
        assertThat(ids.get(4).longValue()).isGreaterThan(ids.get(5).longValue());
        assertThat(ids.get(5).longValue()).isGreaterThan(ids.get(6).longValue());

        List<String> periods = JsonPath.read(body("/api/payments/page?sort=period,desc", "VOLUNTEER"), "$.content[*].period");
        assertThat(periods).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(periods.get(0)).isEqualTo(NOW.toString());

        assertThat(memberNames(body("/api/payments/page?sort=member,asc", "VOLUNTEER")))
            .containsExactly("Abebe Kebede", "Abebe Kebede", "Abebe Kebede", "Berhane Tesfaye", "Berhane Tesfaye",
                "Berhane Tesfaye", "Chaltu Abera", "Dawit Kebede", "Gus Archived");
        assertThat(memberNames(body("/api/payments/page?sort=member,desc&size=2", "VOLUNTEER")))
            .containsExactly("Gus Archived", "Dawit Kebede");
        // No direction means ascending.
        assertThat(memberNames(body("/api/payments/page?sort=member&size=1", "VOLUNTEER"))).containsExactly("Abebe Kebede");
    }

    @Test
    void anUnknownSortFieldOrDirectionIsRejected() throws Exception {
        assertBadRequest("/api/payments/page?sort=notes,asc", "sort");
        assertBadRequest("/api/payments/page?sort=amount,sideways", "sort");
        assertBadRequest("/api/payments/page?sort=amount,asc,extra", "sort");
    }

    @Test
    void searchMatchesPartOfTheMemberNameWhateverTheCaseAndSpaces() throws Exception {
        assertThat(memberNames(body("/api/payments/page?search=keb&sort=member,asc", "VOLUNTEER")))
            .containsExactly("Abebe Kebede", "Abebe Kebede", "Abebe Kebede", "Dawit Kebede");
        assertThat(memberNames(search("  CHALTU "))).containsExactly("Chaltu Abera");

        String none = body("/api/payments/page?search=zzz", "VOLUNTEER");
        assertThat((List<?>) JsonPath.read(none, "$.content")).isEmpty();
        assertThat(((Number) JsonPath.read(none, "$.totalElements")).longValue()).isZero();
        assertThat((Integer) JsonPath.read(none, "$.totalPages")).isZero();

        // A percent sign or underscore is plain text, not a wildcard.
        assertThat((List<?>) JsonPath.read(search("%"), "$.content")).isEmpty();
        assertThat((List<?>) JsonPath.read(search("_"), "$.content")).isEmpty();
        // An empty search is no search.
        assertThat(((Number) JsonPath.read(body("/api/payments/page?search=", "VOLUNTEER"), "$.totalElements")).longValue()).isEqualTo(9);
    }

    @Test
    void searchMatchesTheReceiptNumberOrItsDigits() throws Exception {
        String receipt = "R-" + String.format("%06d", chaltuPayment.getId());
        String digits = String.format("%06d", chaltuPayment.getId());

        for (String text : List.of(receipt, receipt.toLowerCase(), digits, String.valueOf(chaltuPayment.getId()))) {
            String json = search(text);
            assertThat((List<?>) JsonPath.read(json, "$.content")).as(text).hasSize(1);
            assertThat(((Number) JsonPath.read(json, "$.content[0].id")).longValue()).as(text).isEqualTo(chaltuPayment.getId());
        }

        assertThat((List<?>) JsonPath.read(body("/api/payments/page?search=R-999999", "VOLUNTEER"), "$.content")).isEmpty();
    }

    @Test
    void methodFilterKeepsOnlyThatMethodAndCombinesWithSearch() throws Exception {
        String check = body("/api/payments/page?method=CHECK", "VOLUNTEER");
        assertThat(memberNames(check)).containsExactly("Berhane Tesfaye");
        assertThat(((Number) JsonPath.read(check, "$.totalElements")).longValue()).isEqualTo(1);

        assertThat((List<String>) JsonPath.read(body("/api/payments/page?method=BANK_TRANSFER", "VOLUNTEER"),
            "$.content[*].paymentMethod")).containsExactly("BANK_TRANSFER", "BANK_TRANSFER");
        assertThat(memberNames(body("/api/payments/page?method=CASH&search=berhane", "VOLUNTEER"))).containsExactly("Berhane Tesfaye");
        assertThat(((Number) JsonPath.read(body("/api/payments/page?method=", "VOLUNTEER"), "$.totalElements")).longValue()).isEqualTo(9);
    }

    @Test
    void anUnknownMethodOrAnOverlongSearchIsRejected() throws Exception {
        assertBadRequest("/api/payments/page?method=BITCOIN", "method");
        assertBadRequest("/api/payments/page?search=" + "x".repeat(101), "search");
        assertThat((List<?>) JsonPath.read(body("/api/payments/page?search=" + "x".repeat(100), "VOLUNTEER"), "$.content")).isEmpty();
    }

    @Test
    void anArchivedMembersPaymentIsListedButLosesTheContactDetailsForEveryoneButAdmin() throws Exception {
        String staff = body("/api/payments/page?search=gus", "STAFF");
        assertThat((List<?>) JsonPath.read(staff, "$.content")).hasSize(1);
        assertThat((String) JsonPath.read(staff, "$.content[0].member.name")).isEqualTo("Gus Archived");
        assertThat((Object) JsonPath.read(staff, "$.content[0].member.email")).isNull();
        assertThat((Object) JsonPath.read(staff, "$.content[0].member.phone")).isNull();

        String admin = body("/api/payments/page?search=gus", "ADMIN");
        assertThat((String) JsonPath.read(admin, "$.content[0].member.email")).isEqualTo("gus@example.org");
    }

    @Test
    void aPageReadIsOneCountAndOnePageStatementWithTheMembersFetchedAlong() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        String json = body("/api/payments/page?size=4&sort=member,asc", "VOLUNTEER");

        assertThat(memberNames(json)).hasSize(4);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void bothEndpointsNeedASignInAndTheVolunteerRole() throws Exception {
        for (String url : List.of("/api/payments/page", "/api/payments/paid-months")) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(url).with(as("MEMBER"))).andExpect(status().isForbidden());
            mockMvc.perform(get(url).with(as("VOLUNTEER"))).andExpect(status().isOk());
        }
    }

    @Test
    void paidMonthsMapsEachMemberToItsSortedDistinctMonthsInTheLastTwelve() throws Exception {
        // A second payment for the same month must not repeat the month.
        pay(chaltu, NOW.minusMonths(1), 5.0, PaymentMethod.CASH, 10);

        String json = body("/api/payments/paid-months", "VOLUNTEER");

        assertThat((List<String>) JsonPath.read(json, "$['" + abebe.getId() + "']"))
            .containsExactly(NOW.minusMonths(1).toString(), NOW.toString());
        assertThat((List<String>) JsonPath.read(json, "$['" + berhane.getId() + "']"))
            .containsExactly(NOW.minusMonths(2).toString(), NOW.toString());
        assertThat((List<String>) JsonPath.read(json, "$['" + chaltu.getId() + "']")).containsExactly(NOW.minusMonths(1).toString());
        assertThat((List<String>) JsonPath.read(json, "$['" + dawit.getId() + "']")).containsExactly(NOW.minusMonths(3).toString());
        // Archived members are in the list like they are in GET /api/payments; an ID is no contact detail.
        assertThat((List<String>) JsonPath.read(json, "$['" + gus.getId() + "']")).containsExactly(NOW.toString());
        assertThat(JsonPath.<java.util.Map<String, Object>>read(json, "$")).as("no member without a payment")
            .doesNotContainKey(String.valueOf(nopay.getId())).hasSize(5);
    }

    @Test
    void paidMonthsWindowEndsWithTheCurrentMonthAndFollowsTheMonthsParameter() throws Exception {
        String one = body("/api/payments/paid-months?months=1", "VOLUNTEER");
        assertThat(JsonPath.<java.util.Map<String, Object>>read(one, "$").keySet())
            .containsExactlyInAnyOrder(String.valueOf(abebe.getId()), String.valueOf(berhane.getId()), String.valueOf(gus.getId()));
        assertThat((List<String>) JsonPath.read(one, "$['" + abebe.getId() + "']")).containsExactly(NOW.toString());

        // 12 months is this month and the 11 before it: Abebe's payment 12 months back is just outside, 13 takes it in.
        assertThat((List<String>) JsonPath.read(body("/api/payments/paid-months?months=12", "VOLUNTEER"), "$['" + abebe.getId() + "']"))
            .doesNotContain(NOW.minusMonths(12).toString());
        assertThat((List<String>) JsonPath.read(body("/api/payments/paid-months?months=13", "VOLUNTEER"), "$['" + abebe.getId() + "']"))
            .containsExactly(NOW.minusMonths(12).toString(), NOW.minusMonths(1).toString(), NOW.toString());

        // 36 months is the largest window and takes Berhane's payment from 14 months ago.
        assertThat((List<String>) JsonPath.read(body("/api/payments/paid-months?months=36", "VOLUNTEER"), "$['" + berhane.getId() + "']"))
            .first().isEqualTo(NOW.minusMonths(14).toString());
    }

    @Test
    void paidMonthsRejectsAWindowOutsideOneToThirtySix() throws Exception {
        assertBadRequest("/api/payments/paid-months?months=0", "months");
        assertBadRequest("/api/payments/paid-months?months=37", "months");
        mockMvc.perform(get("/api/payments/paid-months?months=x").with(as("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void summaryHasThisMonthAllTimeTheAverageToCentsAndTheCount() throws Exception {
        String json = body("/api/payments/summary", "VOLUNTEER");

        // This month: 10 + 30 + 7 (Abebe, Berhane, Gus); all time 147 over 9 payments; 147 / 9 = 16.33.
        assertThat(((Number) JsonPath.read(json, "$.thisMonth")).doubleValue()).isEqualTo(47.0);
        assertThat(((Number) JsonPath.read(json, "$.allTime")).doubleValue()).isEqualTo(147.0);
        assertThat(((Number) JsonPath.read(json, "$.average")).doubleValue()).isEqualTo(16.33);
        assertThat(((Number) JsonPath.read(json, "$.count")).longValue()).isEqualTo(9);
    }

    @Test
    void summaryCountsTheBillingPeriodNotTheDayThePaymentWasEnteredAndIsOneStatement() throws Exception {
        pay(dawit, NOW.minusMonths(5), 100.0, PaymentMethod.CASH, 0);   // entered today for an old month: not this month
        pay(nopay, NOW, 3.0, PaymentMethod.CASH, 90);                    // entered long ago for this month: this month
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        String json = body("/api/payments/summary", "VOLUNTEER");

        assertThat(((Number) JsonPath.read(json, "$.thisMonth")).doubleValue()).isEqualTo(50.0);
        assertThat(((Number) JsonPath.read(json, "$.allTime")).doubleValue()).isEqualTo(250.0);
        assertThat(((Number) JsonPath.read(json, "$.count")).longValue()).isEqualTo(11);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void summaryOfNoPaymentsIsZeroAndTheEndpointNeedsASignInAndTheVolunteerRole() throws Exception {
        paymentJpaRepository.deleteAll();

        String json = body("/api/payments/summary", "VOLUNTEER");

        assertThat(((Number) JsonPath.read(json, "$.thisMonth")).doubleValue()).isZero();
        assertThat(((Number) JsonPath.read(json, "$.allTime")).doubleValue()).isZero();
        assertThat(((Number) JsonPath.read(json, "$.average")).doubleValue()).isZero();
        assertThat(((Number) JsonPath.read(json, "$.count")).longValue()).isZero();
        mockMvc.perform(get("/api/payments/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/summary").with(as("MEMBER"))).andExpect(status().isForbidden());
    }
}
