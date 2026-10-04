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

    /** The email service tries {@code attempts} times (reporting each one to the callback), then gives {@code sent}. */
    private static org.mockito.stubbing.Answer<Boolean> madeAttempts(int attempts, boolean sent) {
        return invocation -> {
            EmailService.RetryCallback callback = invocation.getArgument(3);
            for (int i = 1; i <= attempts; i++) {
                callback.onRetry(i, 3);
            }
            return sent;
        };
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
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);

        useCase.invoke(communication(), List.of(alice, bob), DeliveryChannel.EMAIL);
        useCase.invoke(communication(), List.of(alice), DeliveryChannel.EMAIL);

        verify(recordActivity).record(eq(ActivityType.MESSAGE_SENT), eq("Message \"Hello\" was sent to 2 members"),
                eq("COMMUNICATION"), any());
        verify(recordActivity).record(eq(ActivityType.MESSAGE_SENT), eq("Message \"Hello\" was sent to 1 member"),
                eq("COMMUNICATION"), any());
    }

    @Test
    void emailCreatesOnePendingDeliveryPerRecipientAndSavesBeforeSending() {
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
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
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);

        useCase.invoke(communication(), List.of(alice, bob), DeliveryChannel.EMAIL);

        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(eq(alice), eq("Hello"), eq("Body"), any());
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(eq(bob), eq("Hello"), eq("Body"), any());
    }

    @Test
    void emailSubjectAndBodyArePersonalisedPerRecipientButTheStoredTextKeepsThePlaceholder() {
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
        Communication c = communication();
        c.setTitle("Hello {{member_name}}");
        c.setMessageContent("Dear {{member_name}}, see you soon");

        useCase.invoke(c, List.of(alice, bob), DeliveryChannel.EMAIL);

        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(eq(alice), eq("Hello Alice"), eq("Dear Alice, see you soon"), any());
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(eq(bob), eq("Hello Bob"), eq("Dear Bob, see you soon"), any());
        assertThat(c.getTitle()).isEqualTo("Hello {{member_name}}");
        assertThat(c.getMessageContent()).isEqualTo("Dear {{member_name}}, see you soon");
    }

    @Test
    void successfulEmailsSaveEachDeliveryAsSentAndTheCommunicationIsSavedOnlyOnce() {
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
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
    void anEmailThatFailsTwiceThenSucceedsIsSentWithThreeAttempts() {
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenAnswer(madeAttempts(3, true));
        Communication c = communication();

        useCase.invoke(c, List.of(alice), DeliveryChannel.EMAIL);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(saved.getValue().getAttempts()).isEqualTo(3);
    }

    @Test
    void anEmailThatFailsEveryTimeRecordsTheMaximumAttempts() {
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenAnswer(madeAttempts(3, false));
        Communication c = communication();

        useCase.invoke(c, List.of(alice), DeliveryChannel.EMAIL);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getAttempts()).isEqualTo(3);
    }

    @Test
    void whenMailIsOffNoAttemptIsCounted() {
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(false);
        Communication c = communication();

        useCase.invoke(c, List.of(alice), DeliveryChannel.EMAIL);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getAttempts()).isZero();
    }

    @Test
    void failedEmailSavesTheDeliveryAsFailedWithNote() {
        when(emailService.sendSimpleEmailWithRetry(eq(alice), any(), any(), any())).thenReturn(false);
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
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
        when(messageDeliveryRepository.save(any(MessageDelivery.class)))
                .thenThrow(new RuntimeException("db down"));

        useCase.invoke(communication(), List.of(alice, bob), DeliveryChannel.EMAIL);

        verify(messageDeliveryRepository, timeout(5000).times(2)).save(any(MessageDelivery.class));
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(eq(bob), any(), any(), any());
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

    @Test
    void membersWithoutAnEmailAreLeftOutAndGetNoDelivery() {
        Member child = new Member("Child", null, null);
        child.setId(3L);
        Communication c = communication();

        useCase.invoke(c, List.of(alice, child), DeliveryChannel.EMAIL);

        assertThat(c.getDeliveries()).extracting(MessageDelivery::getRecipient).containsExactly(alice);
        verify(recordActivity).record(eq(ActivityType.MESSAGE_SENT), eq("Message \"Hello\" was sent to 1 member"),
                eq("COMMUNICATION"), any());
    }

    @Test
    void membersSharingAnAddressGetOneDeliveryBetweenThem() {
        Member spouse = new Member("Spouse", "ALICE@example.com", null);
        spouse.setId(5L);
        Communication c = communication();

        useCase.invoke(c, List.of(spouse, alice), DeliveryChannel.EMAIL);

        assertThat(c.getDeliveries()).extracting(MessageDelivery::getRecipient).containsExactly(alice);
        verify(emailService, timeout(2000).times(1)).sendSimpleEmailWithRetry(any(), any(), any(), any());
    }

    @Test
    void aSingleMemberWithoutAnEmailIsRejectedWithAClearMessage() {
        Member child = new Member("Child", null, null);
        child.setId(3L);
        Communication c = communication();

        assertThatThrownBy(() -> useCase.invoke(c, List.of(child), DeliveryChannel.EMAIL))
                .isInstanceOf(CommunicationDomainException.class)
                .hasMessage("Member 'Child' has no email address, so there is nothing to send to.")
                .extracting("errorCode").isEqualTo(CommunicationDomainException.MEMBER_HAS_NO_EMAIL);

        assertThat(c.isSent()).isFalse();
        verify(communicationRepository, never()).save(any());
        verifyNoInteractions(emailService, messageDeliveryRepository, recordActivity);
    }

    @Test
    void aSingleMemberWhoIsNotAMemberAnymoreIsRejectedEvenWithAnEmail() {
        alice.setStatus(io.github.membertracker.domain.enumeration.MemberStatus.DECEASED);

        assertThatThrownBy(() -> useCase.invoke(communication(), List.of(alice), DeliveryChannel.EMAIL))
                .isInstanceOf(CommunicationDomainException.class)
                .extracting("errorCode").isEqualTo(CommunicationDomainException.MEMBER_CANNOT_RECEIVE_MESSAGES);
        verify(communicationRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void severalMembersAllWithoutAnEmailAreRejectedAsNoRecipients() {
        Member a = new Member("A", null, null);
        Member b = new Member("B", " ", null);

        assertThatThrownBy(() -> useCase.invoke(communication(), List.of(a, b), DeliveryChannel.EMAIL))
                .isInstanceOf(CommunicationDomainException.class)
                .extracting("errorCode").isEqualTo(CommunicationDomainException.NO_RECIPIENTS);
        verify(communicationRepository, never()).save(any());
    }
}
