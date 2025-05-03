package io.github.membertracker.service;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CommunicationService {

    private final CommunicationRepository communicationRepository;
    private final MemberRepository memberRepository;

    @Autowired
    public CommunicationService(CommunicationRepository communicationRepository,
                                MemberRepository memberRepository) {
        this.communicationRepository = communicationRepository;
        this.memberRepository = memberRepository;
    }

    public List<Communication> getAllCommunications() {
        return communicationRepository.findAll();
    }

    public Optional<Communication> getCommunicationById(Long id) {
        return communicationRepository.findById(id);
    }

    public Communication createCommunication(Communication communication) {
        communication.setCreatedDate(LocalDateTime.now());
        return communicationRepository.save(communication);
    }

    public Communication sendToAllMembers(Communication communication) {
        communication.setSentToAllMembers(true);
        communication.setSentDate(LocalDateTime.now());

        List<Member> allMembers = memberRepository.findAll();
        for (Member member : allMembers) {
            MessageDelivery delivery = new MessageDelivery(
                    member,
                    communication,
                    MessageDelivery.DeliveryChannel.EMAIL
            );
            communication.getDeliveries().add(delivery);
        }

        // In a real application, here you would actually send the emails

        return communicationRepository.save(communication);
    }

    public Communication sendToMembers(Communication communication, List<Member> members,
                                       MessageDelivery.DeliveryChannel channel) {
        communication.setSentDate(LocalDateTime.now());

        for (Member member : members) {
            MessageDelivery delivery = new MessageDelivery(member, communication, channel);
            communication.getDeliveries().add(delivery);
        }

        // In a real application, here you would actually send the messages

        return communicationRepository.save(communication);
    }

    public void sendPaymentReminders(int monthsThreshold) {
        List<Member> overdueMembers = memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(monthsThreshold);

        if (!overdueMembers.isEmpty()) {
            Communication reminder = new Communication();
            reminder.setTitle("Payment Reminder");
            reminder.setMessageContent("Dear {{member_name}}, this is a friendly reminder that your membership payment is overdue. Please contact us at your earliest convenience.");
            reminder.setType(Communication.CommunicationType.REMINDER);

            sendToMembers(reminder, overdueMembers, MessageDelivery.DeliveryChannel.EMAIL);
        }
    }
}
