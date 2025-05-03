package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SendCommunicationToAllMembersUseCase {

    private final CommunicationRepository communicationRepository;
    private final MemberRepository memberRepository;

    public SendCommunicationToAllMembersUseCase(CommunicationRepository communicationRepository,
                                               MemberRepository memberRepository) {
        this.communicationRepository = communicationRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Sends a communication to all members.
     *
     * @param communication the communication to send
     * @return the saved communication with delivery information
     */
    @Transactional
    public Communication invoke(Communication communication) {
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
}