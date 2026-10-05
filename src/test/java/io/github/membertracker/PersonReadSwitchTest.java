package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.entity.CommunicationEntity;
import io.github.membertracker.infrastructure.persistence.entity.MessageDeliveryEntity;
import io.github.membertracker.infrastructure.persistence.repository.CommunicationJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.MessageDeliveryJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.PaymentJpaRepository;
import io.github.membertracker.usecase.SaveMemberUseCase;
import jakarta.persistence.EntityManagerFactory;
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

/**
 * Name, email and phone live on the person row only (the legacy member columns are gone, plan step 12): every read
 * path (members, overdue lists, exports, payments, deliveries) must answer with the person's values, and each list read
 * must be one joined statement. Real persistence on in-memory H2.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:personreadswitch;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true"
    })
@AutoConfigureMockMvc
class PersonReadSwitchTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private SaveMemberUseCase saveMember;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MessageDeliveryRepository messageDeliveryRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private CommunicationJpaRepository communicationJpaRepository;
    @Autowired private MessageDeliveryJpaRepository messageDeliveryJpaRepository;

    private Member abebe;
    private Member zed;
    private CommunicationEntity communication;

    @BeforeEach
    void seed() {
        abebe = saveMember.invoke("Abebe Kebede", "abebe@example.org", "+390611", null, null);
        zed = saveMember.invoke("Zed Zewdu", "zed@example.org", "+390622", null, null);
        abebe.setConsecutiveMonthsMissed(2);
        zed.setConsecutiveMonthsMissed(2);
        memberRepository.save(abebe);
        memberRepository.save(zed);

        paymentRepository.save(new Payment(abebe, YearMonth.now(), 25.0, PaymentMethod.CASH));

        communication = communicationJpaRepository.save(new CommunicationEntity());
        messageDeliveryJpaRepository.save(new MessageDeliveryEntity(
            memberJpaRepository.findById(abebe.getId()).orElseThrow(), communication,
            MessageDeliveryEntity.DeliveryChannel.EMAIL));
    }

    @AfterEach
    void cleanUp() {
        messageDeliveryJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        communicationJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private String getAsAdmin(String url) throws Exception {
        return mockMvc.perform(get(url).with(user("admin@example.org").roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    @Test
    void theMemberListAndTheMemberByIdAnswerWithThePersonValues() throws Exception {
        String list = getAsAdmin("/api/members");
        assertThat((List<String>) JsonPath.read(list, "$[*].name")).containsExactly("Abebe Kebede", "Zed Zewdu");
        assertThat((List<String>) JsonPath.read(list, "$[*].email")).containsExactly("abebe@example.org", "zed@example.org");
        assertThat((List<String>) JsonPath.read(list, "$[*].phone")).containsExactly("+390611", "+390622");

        String one = getAsAdmin("/api/members/" + abebe.getId());
        assertThat((String) JsonPath.read(one, "$.name")).isEqualTo("Abebe Kebede");
        assertThat((String) JsonPath.read(one, "$.email")).isEqualTo("abebe@example.org");
        assertThat((String) JsonPath.read(one, "$.phone")).isEqualTo("+390611");

        assertThat((List<String>) JsonPath.read(getAsAdmin("/api/members/active"), "$[*].name"))
            .containsExactly("Abebe Kebede", "Zed Zewdu");
    }

    @Test
    void theOverdueListsSortByThePersonName() throws Exception {
        // Same months missed, so the tie is broken by the person's name: Abebe < Zed.
        assertThat((List<String>) JsonPath.read(getAsAdmin("/api/members/overdue/1"), "$[*].name"))
            .containsExactly("Abebe Kebede", "Zed Zewdu");
        assertThat(memberRepository.findDuesPayingWithMissedAtLeastOrderByMissedDesc(1))
            .extracting(Member::getName).containsExactly("Abebe Kebede", "Zed Zewdu");
    }

    @Test
    void theMemberExportWritesThePersonValues() throws Exception {
        String csv = getAsAdmin("/api/members/export");

        assertThat(csv).contains("Abebe Kebede").contains("abebe@example.org").contains("+390611");
    }

    @Test
    void aPaymentsEmbeddedMemberCarriesThePersonValuesOnEveryPaymentRead() throws Exception {
        for (String url : List.of("/api/payments", "/api/payments/member/" + abebe.getId())) {
            String body = getAsAdmin(url);
            assertThat((List<String>) JsonPath.read(body, "$[*].member.name")).as(url).containsExactly("Abebe Kebede");
            assertThat((List<String>) JsonPath.read(body, "$[*].member.email")).as(url).containsExactly("abebe@example.org");
            assertThat((List<String>) JsonPath.read(body, "$[*].member.phone")).as(url).containsExactly("+390611");
        }
        long paymentId = paymentJpaRepository.findAll().get(0).getId();
        assertThat((String) JsonPath.read(getAsAdmin("/api/payments/" + paymentId), "$.member.name")).isEqualTo("Abebe Kebede");
        assertThat(getAsAdmin("/api/payments/export")).contains("Abebe Kebede");
    }

    @Test
    void aSavedPaymentReturnsItsMemberWithThePersonValues() {
        Payment saved = paymentRepository.save(new Payment(zed, YearMonth.now().minusMonths(1), 10.0, PaymentMethod.CASH));

        assertThat(saved.getMember().getName()).isEqualTo("Zed Zewdu");
        assertThat(saved.getMember().getEmail()).isEqualTo("zed@example.org");
    }

    @Test
    void aDeliverysRecipientCarriesThePersonValues() throws Exception {
        String body = getAsAdmin("/api/communications/" + communication.getId() + "/deliveries");

        assertThat((List<String>) JsonPath.read(body, "$[*].recipient.name")).containsExactly("Abebe Kebede");
        assertThat((List<String>) JsonPath.read(body, "$[*].recipient.email")).containsExactly("abebe@example.org");
        assertThat((List<String>) JsonPath.read(body, "$[*].recipient.phone")).containsExactly("+390611");
        assertThat(messageDeliveryRepository.findByCommunicationId(communication.getId()))
            .extracting(d -> d.getRecipient().getName()).containsExactly("Abebe Kebede");
    }

    @Test
    void everyListReadIsOneJoinedStatementNotOnePerRow() {
        for (int i = 0; i < 4; i++) {
            Member extra = saveMember.invoke("Extra " + i, "extra" + i + "@example.org", null, null, null);
            paymentRepository.save(new Payment(extra, YearMonth.now(), 5.0, PaymentMethod.CASH));
            messageDeliveryJpaRepository.save(new MessageDeliveryEntity(
                memberJpaRepository.findById(extra.getId()).orElseThrow(), communication,
                MessageDeliveryEntity.DeliveryChannel.EMAIL));
        }
        Statistics statistics = statistics();

        assertThat(memberRepository.findAll()).hasSize(6);
        assertThat(statistics.getPrepareStatementCount()).as("member list").isEqualTo(1);

        statistics.clear();
        assertThat(memberRepository.findDuesPayingWithMissedAtLeastOrderByMissedDesc(0)).hasSize(6);
        assertThat(statistics.getPrepareStatementCount()).as("overdue list").isEqualTo(1);

        statistics.clear();
        assertThat(paymentRepository.findAll()).hasSize(5);
        assertThat(statistics.getPrepareStatementCount()).as("payment list").isEqualTo(1);

        statistics.clear();
        assertThat(paymentRepository.findRecent(50)).hasSize(5);
        assertThat(statistics.getPrepareStatementCount()).as("recent payments").isEqualTo(1);

        statistics.clear();
        assertThat(messageDeliveryRepository.findByCommunicationId(communication.getId())).hasSize(5);
        assertThat(statistics.getPrepareStatementCount()).as("deliveries of a communication").isEqualTo(1);
    }
}
