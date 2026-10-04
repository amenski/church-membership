package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.CommunicationDomainException;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryChannel;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryStatus;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private MessageDeliveryRepository messageDeliveryRepository;
    private EmailService emailService;
    private RecordActivityUseCase recordActivity;
    private SendCommunicationToMembersUseCase useCase;
    private final List<List<DeliveryStatus>> statusesAtEachSave = new ArrayList<>();
    private Member alice;
    private Member bob;

    @BeforeEach
    void setUp() {
        communicationRepository = mock(CommunicationRepository.class);
        messageDeliveryRepository = mock(MessageDeliveryRepository.class);
        emailService = mock(EmailService.class);
        recordActivity = mock(RecordActivityUseCase.class);
        useCase = new SendCommunicationToMembersUseCase(communicationRepository, messageDeliveryRepository, emailService,
                recordActivity);
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
    void noRecipientsIsRejectedBeforeAnythingIsMarkedSentSavedOrSent() {
        Communication c = communication();

        assertThatThrownBy(() -> useCase.invoke(c, List.of(), DeliveryChannel.EMAIL))
                .isInstanceOf(CommunicationDomainException.class)
                .extracting("errorCode").isEqualTo(CommunicationDomainException.NO_RECIPIENTS);
        assertThatThrownBy(() -> useCase.invoke(c, null, DeliveryChannel.EMAIL))
                .isInstanceOf(CommunicationDomainException.class);

        assertThat(c.isSent()).isFalse();
        verify(communicationRepository, never()).save(any());
        verifyNoInteractions(emailService, messageDeliveryRepository, recordActivity);
    }

    @Test
    void sendingIsLoggedWithTheTitleAndTheRecipientCount() {
        when(emailService.sendSimpleEmail(any(), any(), any())).thenReturn(true);

        useCase.invoke(communication(), List.of(alice, bob), DeliveryChannel.EMAIL);
        useCase.invoke(communication(), List.of(alice), DeliveryChannel.EMAIL);

        verify(recordActivity).record(eq(ActivityType.MESSAGE_SENT), eq("Message \"Hello\" was sent to 2 members"),
                eq("COMMUNICATION"), any());
        verify(recordActivity).record(eq(ActivityType.MESSAGE_SENT), eq("Message \"Hello\" was sent to 1 member"),
                eq("COMMUNICATION"), any());
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
    void emailSubjectAndBodyArePersonalisedPerRecipientButTheStoredTextKeepsThePlaceholder() {
        when(emailService.sendSimpleEmail(any(), any(), any())).thenReturn(true);
        Communication c = communication();
        c.setTitle("Hello {{member_name}}");
        c.setMessageContent("Dear {{member_name}}, see you soon");

        useCase.invoke(c, List.of(alice, bob), DeliveryChannel.EMAIL);

        verify(emailService, timeout(5000)).sendSimpleEmail(alice, "Hello Alice", "Dear Alice, see you soon");
        verify(emailService, timeout(5000)).sendSimpleEmail(bob, "Hello Bob", "Dear Bob, see you soon");
        assertThat(c.getTitle()).isEqualTo("Hello {{member_name}}");
        assertThat(c.getMessageContent()).isEqualTo("Dear {{member_name}}, see you soon");
    }

    @Test
    void successfulEmailsSaveEachDeliveryAsSentAndTheCommunicationIsSavedOnlyOnce() {
        when(emailService.sendSimpleEmail(any(), any(), any())).thenReturn(true);
        Communication c = communication();

        useCase.invoke(c, List.of(alice, bob), DeliveryChannel.EMAIL);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000).times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(d -> d.getRecipient().getId()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(saved.getAllValues()).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(d.getDeliveryTime()).isNotNull();
        });
        verify(communicationRepository, times(1)).save(c);
    }

    @Test
    void failedEmailSavesTheDeliveryAsFailedWithNote() {
        when(emailService.sendSimpleEmail(eq(alice), any(), any())).thenReturn(false);
        Communication c = communication();

        useCase.invoke(c, List.of(alice), DeliveryChannel.EMAIL);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(c.getDeliveries().get(0));
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getResponseNotes()).isEqualTo("Failed to send email");
        assertThat(saved.getValue().getDeliveryTime()).isNotNull();
        verify(communicationRepository, times(1)).save(c);
    }

    @Test
    void aFailingDeliverySaveDoesNotStopTheLoop() {
        when(emailService.sendSimpleEmail(any(), any(), any())).thenReturn(true);
        when(messageDeliveryRepository.save(any(MessageDelivery.class)))
                .thenThrow(new RuntimeException("db down"));

        useCase.invoke(communication(), List.of(alice, bob), DeliveryChannel.EMAIL);

        verify(messageDeliveryRepository, timeout(5000).times(2)).save(any(MessageDelivery.class));
        verify(emailService, timeout(5000)).sendSimpleEmail(eq(bob), any(), any());
    }

    @Test
    void smsIsSavedAsFailedImmediatelyAndNoEmailIsSent() {
        Communication c = communication();

        useCase.invoke(c, List.of(alice, bob), DeliveryChannel.SMS);

        assertThat(c.getDeliveries()).allSatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(DeliveryChannel.SMS);
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(d.getResponseNotes()).isEqualTo("SMS not implemented");
        });
        verify(messageDeliveryRepository).saveAll(c.getDeliveries());
        verify(communicationRepository, times(1)).save(c);
        verifyNoInteractions(emailService);
    }

    @Test
    void whatsappIsSavedAsFailedImmediatelyAndNoEmailIsSent() {
        Communication c = communication();

        useCase.invoke(c, List.of(alice), DeliveryChannel.WHATSAPP);

        assertThat(c.getDeliveries()).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(d.getResponseNotes()).isEqualTo("WhatsApp not implemented");
        });
        verify(messageDeliveryRepository).saveAll(c.getDeliveries());
        verifyNoInteractions(emailService);
    }

}
