package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.MemberRepository;

import java.util.List;

public class SendPaymentRemindersUseCase {

    private final MemberRepository memberRepository;
    private final SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase;

    public SendPaymentRemindersUseCase(MemberRepository memberRepository,
                                      SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase) {
        this.memberRepository = memberRepository;
        this.sendCommunicationToMembersUseCase = sendCommunicationToMembersUseCase;
    }

    /**
     * Sends payment reminders to members who have missed payments for a specified number of months.
     *
     * @param monthsThreshold the number of consecutive months missed to trigger a reminder
     * @return the created communication if reminders were sent, or null if no reminders were needed
     */
    public Communication invoke(int monthsThreshold) {
        List<Member> overdueMembers = memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(monthsThreshold);

        if (!overdueMembers.isEmpty()) {
            Communication reminder = new Communication();
            reminder.setTitle("Payment Reminder");
            reminder.setMessageContent("Dear {{member_name}}, this is a friendly reminder that your membership payment is overdue. Please contact us at your earliest convenience.");
            reminder.setType(Communication.CommunicationType.REMINDER);

            return sendCommunicationToMembersUseCase.invoke(reminder, overdueMembers, MessageDelivery.DeliveryChannel.EMAIL);
        }
        
        return null;
    }
}