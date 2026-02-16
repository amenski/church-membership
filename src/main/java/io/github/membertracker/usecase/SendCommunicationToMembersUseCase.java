package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SendCommunicationToMembersUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SendCommunicationToMembersUseCase.class);
    
    private final CommunicationRepository communicationRepository;
    private final EmailService emailService;
    private final ExecutorService executorService;

    public SendCommunicationToMembersUseCase(CommunicationRepository communicationRepository,
                                            EmailService emailService) {
        this.communicationRepository = communicationRepository;
        this.emailService = emailService;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor(); // Java 21+
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

        logger.info("Sending communication '{}' to {} members via {}", 
            communication.getTitle(), members.size(), channel);

        // First save the communication with pending deliveries
        for (Member member : members) {
            MessageDelivery delivery = new MessageDelivery(member, communication, channel);
            delivery.setStatus(MessageDelivery.DeliveryStatus.PENDING);
            communication.getDeliveries().add(delivery);
        }
        
        Communication savedCommunication = communicationRepository.save(communication);
        
        // Send messages asynchronously based on channel
        if (channel == MessageDelivery.DeliveryChannel.EMAIL) {
            sendEmailsAsync(savedCommunication, members);
        } else if (channel == MessageDelivery.DeliveryChannel.SMS) {
            // TODO: Implement SMS sending
            logger.warn("SMS sending not yet implemented");
            updateAllDeliveries(savedCommunication, MessageDelivery.DeliveryStatus.FAILED, "SMS not implemented");
        } else if (channel == MessageDelivery.DeliveryChannel.WHATSAPP) {
            // TODO: Implement WhatsApp sending
            logger.warn("WhatsApp sending not yet implemented");
            updateAllDeliveries(savedCommunication, MessageDelivery.DeliveryStatus.FAILED, "WhatsApp not implemented");
        }
        
        return savedCommunication;
    }
    
    private void sendEmailsAsync(Communication communication, List<Member> members) {
        executorService.submit(() -> {
            for (Member member : members) {
                try {
                    boolean sent = emailService.sendSimpleEmail(
                        member,
                        communication.getTitle(),
                        communication.getMessageContent()
                    );
                    
                    // Update delivery status
                    updateDeliveryStatus(communication, member, 
                        sent ? MessageDelivery.DeliveryStatus.SENT : MessageDelivery.DeliveryStatus.FAILED,
                        sent ? null : "Failed to send email"
                    );
                    
                    // Small delay to avoid overwhelming SMTP server
                    Thread.sleep(100);
                } catch (Exception e) {
                    logger.error("Error sending email to {}: {}", member.getEmail(), e.getMessage(), e);
                    updateDeliveryStatus(communication, member, 
                        MessageDelivery.DeliveryStatus.FAILED, 
                        "Exception: " + e.getMessage()
                    );
                }
            }
            logger.info("Finished sending communication '{}' to selected members", communication.getTitle());
        });
    }
    
    private void updateDeliveryStatus(Communication communication, Member member, 
                                     MessageDelivery.DeliveryStatus status, String notes) {
        communication.getDeliveries().stream()
            .filter(d -> d.getRecipient().getId().equals(member.getId()))
            .findFirst()
            .ifPresent(delivery -> {
                delivery.setStatus(status);
                delivery.setDeliveryTime(LocalDateTime.now());
                if (notes != null) {
                    delivery.setResponseNotes(notes);
                }
            });
        
        // Save updated delivery status
        communicationRepository.save(communication);
    }
    
    private void updateAllDeliveries(Communication communication, 
                                    MessageDelivery.DeliveryStatus status, String notes) {
        for (MessageDelivery delivery : communication.getDeliveries()) {
            delivery.setStatus(status);
            delivery.setDeliveryTime(LocalDateTime.now());
            if (notes != null) {
                delivery.setResponseNotes(notes);
            }
        }
        communicationRepository.save(communication);
    }
}