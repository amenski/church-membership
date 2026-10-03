package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryChannel;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryStatus;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The email path hands work to a private cached thread pool, so tests only assert what is
 * visible at the moment the first save happens (captured synchronously inside the repository
 * mock) plus emails reaching the EmailService, awaited with a timeout.
 */
class SendCommunicationToMembersUseCaseTest {

    private CommunicationRepository communicationRepository;
    private EmailService emailService;
    private SendCommunicationToMembersUseCase useCase;
    private final List<List<DeliveryStatus>> statusesAtEachSave = new ArrayList<>();
    private Member alice;
    private Member bob;

    @BeforeEach
    void setUp() {
        communicationRepository = mock(CommunicationRepository.class);
        emailService = mock(EmailService.class);
        useCase = new SendCommunicationToMembersUseCase(communicationRepository, emailService);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(i -> {
            Communication c = i.getArgument(0);
            synchronized (statusesAtEachSave) {
                statusesAtEachSave.add(c.getDeliveries().stream().map(MessageDelivery::getStatus).toList());
            }
            return c;
        });
        alice = member(1L, "Alice");
        bob = member(2L, "Bob");
    }

    private Member member(long id, String name) {
        Member m = new Member(name, name.toLowerCase() + "@example.com", "+1234567890");
        m.setId(id);
        return m;
    }

    private Communication communication() {
        Communication c = new Communication();
        c.setTitle("Hello");
        c.setMessageContent("Body");
        return c;
    }

    @Test
    void emailCreatesOnePendingDeliveryPerRecipientAndSavesBeforeSending() {
        when(emailService.sendSimpleEmail(any(), any(), any())).thenReturn(true);
        Communication c = communication();
        LocalDateTime before = LocalDateTime.now();

        Communication result = useCase.invoke(c, List.of(alice, bob), DeliveryChannel.EMAIL);

        assertThat(result).isSameAs(c);
        assertThat(c.getSentDate()).isAfterOrEqualTo(before);
        assertThat(c.getDeliveries()).extracting(MessageDelivery::getRecipient).containsExactly(alice, bob);
        assertThat(c.getDeliveries()).allSatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(DeliveryChannel.EMAIL);
            assertThat(d.getCommunication()).isSameAs(c);
        });
        synchronized (statusesAtEachSave) {
            assertThat(statusesAtEachSave.get(0)).containsExactly(DeliveryStatus.PENDING, DeliveryStatus.PENDING);
        }
    }

    @Test
    void emailIsSentToEachRecipientWithTitleAndContent() {
        when(emailService.sendSimpleEmail(any(), any(), any())).thenReturn(true);

        useCase.invoke(communication(), List.of(alice, bob), DeliveryChannel.EMAIL);

        verify(emailService, timeout(5000)).sendSimpleEmail(alice, "Hello", "Body");
        verify(emailService, timeout(5000)).sendSimpleEmail(bob, "Hello", "Body");
    }

    @Test
    void successfulEmailsEventuallyMarkDeliveriesSent() {
        when(emailService.sendSimpleEmail(any(), any(), any())).thenReturn(true);
        Communication c = communication();

        useCase.invoke(c, List.of(alice, bob), DeliveryChannel.EMAIL);

        verify(communicationRepository, timeout(5000).times(3)).save(c);
        assertThat(c.getDeliveries()).extracting(MessageDelivery::getStatus)
                .containsExactly(DeliveryStatus.SENT, DeliveryStatus.SENT);
    }

    @Test
    void failedEmailEventuallyMarksDeliveryFailedWithNote() {
        when(emailService.sendSimpleEmail(eq(alice), any(), any())).thenReturn(false);
        Communication c = communication();

        useCase.invoke(c, List.of(alice), DeliveryChannel.EMAIL);

        verify(communicationRepository, timeout(5000).times(2)).save(c);
        MessageDelivery d = c.getDeliveries().get(0);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(d.getResponseNotes()).isEqualTo("Failed to send email");
        assertThat(d.getDeliveryTime()).isNotNull();
    }

    @Test
    void smsIsMarkedFailedImmediatelyAndNoEmailIsSent() {
        Communication c = communication();

        useCase.invoke(c, List.of(alice, bob), DeliveryChannel.SMS);

        assertThat(c.getDeliveries()).allSatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(DeliveryChannel.SMS);
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(d.getResponseNotes()).isEqualTo("SMS not implemented");
        });
        verify(communicationRepository, times(2)).save(c);
        verifyNoInteractions(emailService);
    }

    @Test
    void whatsappIsMarkedFailedImmediatelyAndNoEmailIsSent() {
        Communication c = communication();

        useCase.invoke(c, List.of(alice), DeliveryChannel.WHATSAPP);

        assertThat(c.getDeliveries()).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(d.getResponseNotes()).isEqualTo("WhatsApp not implemented");
        });
        verifyNoInteractions(emailService);
    }

    @Test
    void emptyRecipientListSavesCommunicationWithNoDeliveries() {
        Communication c = communication();

        useCase.invoke(c, List.of(), DeliveryChannel.SMS);

        assertThat(c.getDeliveries()).isEmpty();
        verify(communicationRepository, times(2)).save(c);
        verifyNoInteractions(emailService);
    }
}
