package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;

import java.time.LocalDateTime;
import java.util.List;

public class SendCommunicationToMembersUseCase {

    private final CommunicationRepository communicationRepository;

    public SendCommunicationToMembersUseCase(CommunicationRepository communicationRepository) {
        this.communicationRepository = communicationRepository;
    }

    /**
     * Sends a communication to specific members using the specified delivery channel.
     *
     * @param communication the communication to send
     * @param members the list of members to send the communication to
     * @param channel the delivery channel to use
     * @return the saved communication with delivery information
     */
    public Communication invoke(Communication communication, List<Member> members, MessageDelivery.DeliveryChannel channel) {
        communication.setSentDate(LocalDateTime.now());

        for (Member member : members) {
            MessageDelivery delivery = new MessageDelivery(member, communication, channel);
            communication.getDeliveries().add(delivery);
        }

        // In a real application, here you would actually send the messages

        return communicationRepository.save(communication);
    }
}