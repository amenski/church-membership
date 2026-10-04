package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SendPaymentRemindersUseCaseTest {

    private MemberRepository memberRepository;
    private SendCommunicationToMembersUseCase sender;
    private SendPaymentRemindersUseCase useCase;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        sender = mock(SendCommunicationToMembersUseCase.class);
        useCase = new SendPaymentRemindersUseCase(memberRepository, sender);
    }

    @Test
    void noOverdueMembersMeansNothingIsSentAndNullReturned() {
        when(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(2)).thenReturn(List.of());

        assertThat(useCase.invoke(2)).isNull();

        verifyNoInteractions(sender);
    }

    @Test
    @SuppressWarnings("unchecked")
    void overdueMembersReceiveAnEmailReminderCommunication() {
        List<Member> overdue = List.of(new Member("a", "a@example.com", "+1234567890"),
                new Member("b", "b@example.com", "+1234567890"));
        when(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(2)).thenReturn(overdue);
        Communication returned = new Communication();
        when(sender.invoke(any(), any(), any())).thenReturn(returned);

        Communication result = useCase.invoke(2);

        assertThat(result).isSameAs(returned);
        ArgumentCaptor<Communication> comm = ArgumentCaptor.forClass(Communication.class);
        verify(sender).invoke(comm.capture(), org.mockito.ArgumentMatchers.eq(overdue),
                org.mockito.ArgumentMatchers.eq(MessageDelivery.DeliveryChannel.EMAIL));
        assertThat(comm.getValue().getType()).isEqualTo(CommunicationType.REMINDER);
        assertThat(comm.getValue().getTitle()).isEqualTo("Payment Reminder");
    }

    @Test
    void inactiveMembersAreNotReminded() {
        Member active = new Member("a", "a@example.com", "+1234567890");
        Member inactive = new Member("b", "b@example.com", "+1234567890");
        inactive.setActive(false);
        when(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(3)).thenReturn(List.of(active, inactive));
        when(sender.invoke(any(), any(), any())).thenReturn(new Communication());

        useCase.invoke(3);

        verify(sender).invoke(any(), org.mockito.ArgumentMatchers.eq(List.of(active)),
                org.mockito.ArgumentMatchers.eq(MessageDelivery.DeliveryChannel.EMAIL));
    }

    @Test
    void onlyInactiveOverdueMembersMeansNothingIsSent() {
        Member inactive = new Member("b", "b@example.com", "+1234567890");
        inactive.setActive(false);
        when(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(3)).thenReturn(List.of(inactive));

        assertThat(useCase.invoke(3)).isNull();

        verifyNoInteractions(sender);
    }

    @Test
    void reminderHandedToTheSendUseCaseIsTheTemplateWithThePlaceholder() {
        when(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(2))
                .thenReturn(List.of(new Member("Alice", "a@example.com", "+1234567890")));

        useCase.invoke(2);

        ArgumentCaptor<Communication> comm = ArgumentCaptor.forClass(Communication.class);
        verify(sender).invoke(comm.capture(), any(), any());
        assertThat(comm.getValue().getMessageContent()).contains("{{member_name}}");
    }

    @Test
    void theEmailedReminderNamesTheMember() {
        CommunicationRepository communicationRepository = mock(CommunicationRepository.class);
        when(communicationRepository.save(any(Communication.class))).thenAnswer(i -> i.getArgument(0));
        EmailService emailService = mock(EmailService.class);
        when(emailService.sendSimpleEmailWithRetry(any(), any(), any(), any())).thenReturn(true);
        Member alice = new Member("Alice", "a@example.com", "+1234567890");
        alice.setId(1L);
        when(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(3)).thenReturn(List.of(alice));
        SendPaymentRemindersUseCase endToEnd = new SendPaymentRemindersUseCase(memberRepository,
                new SendCommunicationToMembersUseCase(communicationRepository,
                        mock(MessageDeliveryRepository.class), emailService, mock(RecordActivityUseCase.class)));

        endToEnd.invoke(3);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(5000)).sendSimpleEmailWithRetry(org.mockito.ArgumentMatchers.eq(alice),
                org.mockito.ArgumentMatchers.eq("Payment Reminder"), body.capture(), any());
        assertThat(body.getValue()).startsWith("Dear Alice,").doesNotContain("{{member_name}}");
    }
}
