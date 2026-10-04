package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.CommunicationDomainException;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryChannel;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryStatus;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
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
 * Emails go out on a private cached thread pool, so tests assert the state captured synchronously
 * at the first save plus calls to EmailService awaited with a timeout.
 */
class SendCommunicationToAllMembersUseCaseTest {

    private CommunicationRepository communicationRepository;
    private MemberRepository memberRepository;
    private MessageDeliveryRepository messageDeliveryRepository;
    private EmailService emailService;
    private RecordActivityUseCase recordActivity;
    private SendCommunicationToAllMembersUseCase useCase;
    private final List<List<DeliveryStatus>> statusesAtEachSave = new ArrayList<>();
    private Member alice;
    private Member bob;

    @BeforeEach
    void setUp() {
        communicationRepository = mock(CommunicationRepository.class);
        memberRepository = mock(MemberRepository.class);
        messageDeliveryRepository = mock(MessageDeliveryRepository.class);
        emailService = mock(EmailService.class);
        recordActivity = mock(RecordActivityUseCase.class);
        useCase = new SendCommunicationToAllMembersUseCase(communicationRepository, memberRepository,
                messageDeliveryRepository, emailService, recordActivity);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(i -> {
            Communication c = i.getArgument(0);
            synchronized (statusesAtEachSave) {
                statusesAtEachSave.add(c.getDeliveries().stream().map(MessageDelivery::getStatus).toList());
            }
            return c;
        });
        alice = member(1L, "Alice", true);
        bob = member(2L, "Bob", true);
    }

    private Member member(long id, String name, boolean active) {
        Member m = new Member(name, name.toLowerCase() + "@example.com", "+1234567890");
        m.setId(id);
        m.setActive(active);
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
        c.setTitle("News");
        c.setMessageContent("Body");
        return c;
    }

    @Test
    void flagsCommunicationAsSentToAllAndCreatesPendingEmailDeliveryPerMember() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice, bob));
        Communication c = communication();
        LocalDateTime before = LocalDateTime.now();

        Communication result = useCase.invoke(c);

        assertThat(result).isSameAs(c);
        assertThat(c.isSentToAllMembers()).isTrue();
        assertThat(c.getSentDate()).isAfterOrEqualTo(before);
        assertThat(c.getDeliveries()).extracting(MessageDelivery::getRecipient).containsExactly(alice, bob);
        assertThat(c.getDeliveries()).allSatisfy(d -> assertThat(d.getChannel()).isEqualTo(DeliveryChannel.EMAIL));
        synchronized (statusesAtEachSave) {
            assertThat(statusesAtEachSave.get(0)).containsExactly(DeliveryStatus.PENDING, DeliveryStatus.PENDING);
        }
    }

    @Test
    void sendingIsLoggedWithTheTitleAndTheRecipientCount() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice, bob));

        useCase.invoke(communication());

        verify(recordActivity).record(eq(ActivityType.MESSAGE_SENT), eq("Message \"News\" was sent to 2 members"),
                eq("COMMUNICATION"), any());
    }

    @Test
    void noActiveMembersIsRejectedBeforeAnythingIsMarkedSentSavedOrSent() {
        when(memberRepository.findByActive(true)).thenReturn(List.of());
        Communication c = communication();

        assertThatThrownBy(() -> useCase.invoke(c))
                .isInstanceOf(CommunicationDomainException.class)
                .hasMessage("There is nobody to send this to.")
                .extracting("errorCode").isEqualTo(CommunicationDomainException.NO_RECIPIENTS);

        assertThat(c.isSent()).isFalse();
        assertThat(c.isSentToAllMembers()).isFalse();
        verify(communicationRepository, never()).save(any());
        verifyNoInteractions(emailService, messageDeliveryRepository, recordActivity);
    }

    @Test
    void usesTheRetryEnabledEmailPathForEveryMember() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice, bob));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);

        useCase.invoke(communication());

        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(any(), org.mockito.ArgumentMatchers.eq("News"),
                org.mockito.ArgumentMatchers.eq("Body"), any());
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(org.mockito.ArgumentMatchers.eq(bob), any(), any(), any());
    }

    @Test
    void emailSubjectAndBodyArePersonalisedPerRecipientButTheStoredTextKeepsThePlaceholder() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice, bob));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
        Communication c = communication();
        c.setTitle("News for {{member_name}}");
        c.setMessageContent("Dear {{member_name}}, welcome");

        useCase.invoke(c);

        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(org.mockito.ArgumentMatchers.eq(alice),
                org.mockito.ArgumentMatchers.eq("News for Alice"),
                org.mockito.ArgumentMatchers.eq("Dear Alice, welcome"), any());
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(org.mockito.ArgumentMatchers.eq(bob),
                org.mockito.ArgumentMatchers.eq("News for Bob"),
                org.mockito.ArgumentMatchers.eq("Dear Bob, welcome"), any());
        assertThat(c.getTitle()).isEqualTo("News for {{member_name}}");
        assertThat(c.getMessageContent()).isEqualTo("Dear {{member_name}}, welcome");
    }

    @Test
    void anEmailThatFailsTwiceThenSucceedsIsSentWithThreeAttempts() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenAnswer(madeAttempts(3, true));
        Communication c = communication();

        useCase.invoke(c);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(saved.getValue().getAttempts()).isEqualTo(3);
    }

    @Test
    void anEmailThatFailsEveryTimeRecordsTheMaximumAttempts() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenAnswer(madeAttempts(3, false));
        Communication c = communication();

        useCase.invoke(c);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getAttempts()).isEqualTo(3);
    }

    @Test
    void whenMailIsOffNoAttemptIsCounted() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(false);
        Communication c = communication();

        useCase.invoke(c);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getAttempts()).isZero();
    }

    @Test
    void failedEmailEventuallySavesTheDeliveryAsFailedWithNote() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(false);
        Communication c = communication();

        useCase.invoke(c);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000)).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(c.getDeliveries().get(0));
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getResponseNotes()).isEqualTo("Failed after max retry attempts");
        assertThat(saved.getValue().getDeliveryTime()).isNotNull();
    }

    @Test
    void successfulEmailsSaveEachDeliveryAsSentAndTheCommunicationIsSavedOnlyOnce() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice, bob));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
        Communication c = communication();

        useCase.invoke(c);

        ArgumentCaptor<MessageDelivery> saved = ArgumentCaptor.forClass(MessageDelivery.class);
        verify(messageDeliveryRepository, timeout(5000).times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(d -> d.getRecipient().getId()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(saved.getAllValues()).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(DeliveryStatus.SENT));
        verify(communicationRepository, times(1)).save(c);
    }

    @Test
    void aFailingDeliverySaveDoesNotStopTheLoop() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice, bob));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
        when(messageDeliveryRepository.save(any(MessageDelivery.class)))
                .thenThrow(new RuntimeException("db down"));

        useCase.invoke(communication());

        verify(messageDeliveryRepository, timeout(5000).times(2)).save(any(MessageDelivery.class));
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(org.mockito.ArgumentMatchers.eq(bob), any(), any(), any());
    }

    @Test
    void inactiveMembersAreNotSelectedAsRecipients() {
        Member inactive = member(3L, "Gone", false);
        when(memberRepository.findAll()).thenReturn(List.of(alice, inactive));
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice));
        Communication c = communication();

        useCase.invoke(c);

        assertThat(c.getDeliveries()).extracting(MessageDelivery::getRecipient).containsExactly(alice);
    }
}
