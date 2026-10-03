package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryChannel;
import io.github.membertracker.domain.model.MessageDelivery.DeliveryStatus;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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
    private EmailService emailService;
    private SendCommunicationToAllMembersUseCase useCase;
    private final List<List<DeliveryStatus>> statusesAtEachSave = new ArrayList<>();
    private Member alice;
    private Member bob;

    @BeforeEach
    void setUp() {
        communicationRepository = mock(CommunicationRepository.class);
        memberRepository = mock(MemberRepository.class);
        emailService = mock(EmailService.class);
        useCase = new SendCommunicationToAllMembersUseCase(communicationRepository, memberRepository, emailService);
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
    void usesTheRetryEnabledEmailPathForEveryMember() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice, bob));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);

        useCase.invoke(communication());

        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(any(), org.mockito.ArgumentMatchers.eq("News"),
                org.mockito.ArgumentMatchers.eq("Body"), any());
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(org.mockito.ArgumentMatchers.eq(bob), any(), any(), any());
    }

    @Test
    void failedEmailEventuallyMarksDeliveryFailed() {
        when(memberRepository.findByActive(true)).thenReturn(List.of(alice));
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(false);
        Communication c = communication();

        useCase.invoke(c);

        verify(communicationRepository, timeout(5000).times(2)).save(c);
        MessageDelivery d = c.getDeliveries().get(0);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(d.getResponseNotes()).isEqualTo("Failed after max retry attempts");
    }

    @Test
    void noMembersSavesEmptyCommunicationAndSendsNothing() {
        when(memberRepository.findByActive(true)).thenReturn(List.of());
        Communication c = communication();

        useCase.invoke(c);

        assertThat(c.getDeliveries()).isEmpty();
        assertThat(c.isSentToAllMembers()).isTrue();
        verify(communicationRepository).save(c);
        verifyNoInteractions(emailService);
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
