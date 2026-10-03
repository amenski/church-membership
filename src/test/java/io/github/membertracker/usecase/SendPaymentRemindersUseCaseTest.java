package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    @Disabled("AUDIT C4: reminder body is sent with the literal \"{{member_name}}\" placeholder instead of the member's name")
    void reminderBodyDoesNotContainUnfilledPlaceholder() {
        when(memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(2))
                .thenReturn(List.of(new Member("Alice", "a@example.com", "+1234567890")));

        useCase.invoke(2);

        ArgumentCaptor<Communication> comm = ArgumentCaptor.forClass(Communication.class);
        verify(sender).invoke(comm.capture(), any(), any());
        assertThat(comm.getValue().getMessageContent()).doesNotContain("{{member_name}}");
    }
}
