package io.github.membertracker;

import com.jayway.jsonpath.JsonPath;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.persistence.entity.MessageDeliveryEntity;
import io.github.membertracker.infrastructure.persistence.repository.CommunicationJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.MessageDeliveryJpaRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Delivery rows must be written when a communication is sent, updated as each email result comes in,
 * served by GET /{id}/deliveries and be retryable. Real persistence on in-memory H2, EmailService mocked.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:commdelivery;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@AutoConfigureMockMvc
class CommunicationDeliveryPersistenceTest {

    private static final String BODY = "{\"title\":\"Hi {{member_name}}\",\"messageContent\":\"Test\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CommunicationRepository communicationRepository;
    @Autowired private MessageDeliveryJpaRepository messageDeliveryJpaRepository;
    @Autowired private CommunicationJpaRepository communicationJpaRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;

    @MockitoBean private EmailService emailService;

    private Member alice;
    private Member bob;

    @BeforeEach
    void createMembers() {
        alice = memberRepository.save(new Member("Alice", "alice@example.com", "+1234567890"));
        bob = memberRepository.save(new Member("Bob", "bob@example.com", "+1234567891"));
    }

    @AfterEach
    void cleanUp() {
        messageDeliveryJpaRepository.deleteAll();
        communicationJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    /** Alice's email goes out, Bob's fails. */
    private void aliceSucceedsBobFails() {
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any()))
                .thenAnswer(i -> ((Member) i.getArgument(0)).getEmail().equals("alice@example.com"));
        when(emailService.sendSimpleEmail(any(), any(), any()))
                .thenAnswer(i -> ((Member) i.getArgument(0)).getEmail().equals("alice@example.com"));
    }

    private String asStaff(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                           String body) throws Exception {
        return mockMvc.perform(request.with(user("staff@example.com").roles("STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String asVolunteer(String url) throws Exception {
        return mockMvc.perform(get(url).with(user("volunteer@example.com").roles("VOLUNTEER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void awaitNoPendingDeliveries(long communicationId, int expectedRows) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<MessageDeliveryEntity> rows = messageDeliveryJpaRepository.findByCommunicationId(communicationId);
            assertThat(rows).hasSize(expectedRows);
            assertThat(rows).noneMatch(r -> r.getStatus() == MessageDeliveryEntity.DeliveryStatus.PENDING);
        });
    }

    @Test
    void sendToAllStoresDeliveriesTheirStatusesAndTheFailedOneCanBeRetried() throws Exception {
        aliceSucceedsBobFails();

        String response = asStaff(post("/api/communications/send-to-all"), BODY);

        assertThat(response).doesNotContain("deliveries").doesNotContain("alice@example.com");
        int communicationId = JsonPath.read(response, "$.id");
        awaitNoPendingDeliveries(communicationId, 2);

        String deliveries = asVolunteer("/api/communications/" + communicationId + "/deliveries");
        assertThat((List<?>) JsonPath.read(deliveries, "$")).hasSize(2);
        assertThat((List<String>) JsonPath.read(deliveries, "$[?(@.recipient.email=='alice@example.com')].status"))
                .containsExactly("SENT");
        assertThat((List<String>) JsonPath.read(deliveries, "$[?(@.recipient.email=='bob@example.com')].status"))
                .containsExactly("FAILED");
        assertThat((List<String>) JsonPath.read(deliveries, "$[?(@.recipient.email=='bob@example.com')].responseNotes"))
                .containsExactly("Failed after max retry attempts");
        assertThat((List<Integer>) JsonPath.read(deliveries, "$[*].communication.id")).containsOnly(communicationId);
        int failedId = ((List<Integer>) JsonPath.read(deliveries,
                "$[?(@.recipient.email=='bob@example.com')].id")).get(0);

        // The mail server is back: retry the failed delivery
        doReturn(true).when(emailService).sendSimpleEmailWithRetry(any(), any(), any(), any());
        String retried = asStaff(post("/api/communications/" + communicationId + "/deliveries/" + failedId + "/retry"), "");

        assertThat((String) JsonPath.read(retried, "$.status")).isEqualTo("SENT");
        assertThat(messageDeliveryJpaRepository.findById((long) failedId).orElseThrow().getStatus())
                .isEqualTo(MessageDeliveryEntity.DeliveryStatus.SENT);
        assertThat(messageDeliveryJpaRepository.findByCommunicationId((long) communicationId))
                .allMatch(r -> r.getStatus() == MessageDeliveryEntity.DeliveryStatus.SENT);
    }

    @Test
    void sendToOneMemberStoresItsDelivery() throws Exception {
        aliceSucceedsBobFails();

        String response = asStaff(post("/api/communications/send-to-member/" + bob.getId()), BODY);

        assertThat(response).doesNotContain("deliveries");
        int communicationId = JsonPath.read(response, "$.id");
        awaitNoPendingDeliveries(communicationId, 1);
        String deliveries = asVolunteer("/api/communications/" + communicationId + "/deliveries");
        assertThat((List<String>) JsonPath.read(deliveries, "$[*].status")).containsExactly("FAILED");
        assertThat((List<String>) JsonPath.read(deliveries, "$[*].recipient.name")).containsExactly("Bob");
        assertThat((List<String>) JsonPath.read(deliveries, "$[*].channel")).containsExactly("EMAIL");
    }

    @Test
    void aDraftHasNoDeliveries() throws Exception {
        String response = asStaff(post("/api/communications"), BODY);

        int communicationId = JsonPath.read(response, "$.id");
        assertThat((List<?>) JsonPath.read(
                asVolunteer("/api/communications/" + communicationId + "/deliveries"), "$")).isEmpty();
    }

    @Test
    void saveReturnsTheDeliveriesWithIdsAndSavingAgainWithoutDeliveriesKeepsTheStoredRows() {
        Communication communication = new Communication();
        communication.setTitle("Direct");
        communication.setMessageContent("Body");
        communication.addDelivery(new MessageDelivery(alice, communication, MessageDelivery.DeliveryChannel.EMAIL));
        communication.addDelivery(new MessageDelivery(bob, communication, MessageDelivery.DeliveryChannel.SMS));

        Communication saved = communicationRepository.save(communication);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDeliveries()).hasSize(2).allSatisfy(d -> {
            assertThat(d.getId()).isNotNull();
            assertThat(d.getCommunication()).isSameAs(saved);
        });
        assertThat(saved.getDeliveries()).extracting(MessageDelivery::getRecipient).containsExactly(alice, bob);
        List<MessageDeliveryEntity> rows = messageDeliveryJpaRepository.findByCommunicationId(saved.getId());
        assertThat(rows).extracting(MessageDeliveryEntity::getId)
                .containsExactlyInAnyOrderElementsOf(saved.getDeliveries().stream().map(MessageDelivery::getId).toList());
        assertThat(rows).extracting(MessageDeliveryEntity::getChannel)
                .containsExactlyInAnyOrder(MessageDeliveryEntity.DeliveryChannel.EMAIL,
                        MessageDeliveryEntity.DeliveryChannel.SMS);

        // findById does not load deliveries; saving it again (draft/markAsSent paths) must not delete the rows
        Communication reloaded = communicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDeliveries()).isEmpty();
        reloaded.setTitle("Direct, edited");
        communicationRepository.save(reloaded);

        assertThat(messageDeliveryJpaRepository.findByCommunicationId(saved.getId())).hasSize(2);
        assertThat(communicationRepository.findById(saved.getId()).orElseThrow().getTitle()).isEqualTo("Direct, edited");
    }
}
