package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.CommunicationDomainException;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import io.github.membertracker.utils.MessageTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SendCommunicationToMembersUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SendCommunicationToMembersUseCase.class);
    
    private final CommunicationRepository communicationRepository;
    private final MessageDeliveryRepository messageDeliveryRepository;
    private final EmailService emailService;
    private final RecordActivityUseCase recordActivity;
    private final ExecutorService executorService;

    public SendCommunicationToMembersUseCase(CommunicationRepository communicationRepository,
                                            MessageDeliveryRepository messageDeliveryRepository,
                                            EmailService emailService,
                                            RecordActivityUseCase recordActivity) {
        this.communicationRepository = communicationRepository;
        this.messageDeliveryRepository = messageDeliveryRepository;
        this.emailService = emailService;
        this.recordActivity = recordActivity;
        this.executorService = Executors.newCachedThreadPool(); // Java 17 compatible
    }

    /**
     * Sends a communication to specific members using the specified delivery channel.
     *
     * @param communication the communication to send
     * @param members the list of members to send the communication to; empty is rejected with
     *                {@link CommunicationDomainException#noRecipients()} before anything is stored
     * @param channel the delivery channel to use
     * @return the saved communication with delivery information
     */
    public Communication invoke(Communication communication, List<Member> members, MessageDelivery.DeliveryChannel channel) {
        if (members == null || members.isEmpty()) {
            throw CommunicationDomainException.noRecipients();
        }
        communication.markAsSent();

        logger.info("Sending communication '{}' to {} members via {}", 
            communication.getTitle(), members.size(), channel);

        // First save the communication with pending deliveries
        for (Member member : members) {
            MessageDelivery delivery = new MessageDelivery(member, communication, channel);
            delivery.setStatus(MessageDelivery.DeliveryStatus.PENDING);
            communication.addDelivery(delivery);
        }
        
        Communication savedCommunication = communicationRepository.save(communication);
        
        recordActivity.record(ActivityType.MESSAGE_SENT, sentDescription(savedCommunication, members.size()),
                "COMMUNICATION", savedCommunication.getId());

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
    
    private static String sentDescription(Communication communication, int recipients) {
        return "Message \"" + communication.getTitle() + "\" was sent to " + recipients
                + (recipients == 1 ? " member" : " members");
    }

    private void sendEmailsAsync(Communication communication, List<Member> members) {
        executorService.submit(() -> {
            for (Member member : members) {
                AtomicInteger attempts = new AtomicInteger();
                try {
                    boolean sent = emailService.sendSimpleEmailWithRetry(
                        member,
                        MessageTemplates.personalize(communication.getTitle(), member),
                        MessageTemplates.personalize(communication.getMessageContent(), member),
                        (currentAttempt, maxAttempts) -> attempts.set(currentAttempt)
                    );
                    
                    // Update delivery status
                    updateDeliveryStatus(communication, member, 
                        sent ? MessageDelivery.DeliveryStatus.SENT : MessageDelivery.DeliveryStatus.FAILED,
                        sent ? null : "Failed to send email", attempts.get()
                    );
                    
                    // Small delay to avoid overwhelming SMTP server
                    Thread.sleep(100);
                } catch (Exception e) {
                    logger.error("Error sending email to {}: {}", member.getEmail(), e.getMessage(), e);
                    updateDeliveryStatus(communication, member, 
                        MessageDelivery.DeliveryStatus.FAILED, 
                        "Exception: " + e.getMessage(), attempts.get()
                    );
                }
            }
            logger.info("Finished sending communication '{}' to selected members", communication.getTitle());
        });
    }
    
    private void updateDeliveryStatus(Communication communication, Member member, 
                                     MessageDelivery.DeliveryStatus status, String notes, int attempts) {
        communication.getDeliveries().stream()
            .filter(d -> d.getRecipient().getId().equals(member.getId()))
            .findFirst()
            .ifPresent(delivery -> {
                delivery.setStatus(status);
                delivery.setAttempts(attempts);
                delivery.setDeliveryTime(LocalDateTime.now());
                if (notes != null) {
                    delivery.setResponseNotes(notes);
                }
                // One row per recipient; a failed save must not stop the loop
                try {
                    messageDeliveryRepository.save(delivery);
                } catch (Exception e) {
                    logger.error("Could not save delivery status {} for {}: {}",
                        status, member.getEmail(), e.getMessage(), e);
                }
            });
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
        messageDeliveryRepository.saveAll(communication.getDeliveries());
    }
}