package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.CommunicationDomainException;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
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

public class SendCommunicationToAllMembersUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SendCommunicationToAllMembersUseCase.class);
    
    private final CommunicationRepository communicationRepository;
    private final MemberRepository memberRepository;
    private final MessageDeliveryRepository messageDeliveryRepository;
    private final EmailService emailService;
    private final RecordActivityUseCase recordActivity;
    private final ExecutorService executorService;

    public SendCommunicationToAllMembersUseCase(CommunicationRepository communicationRepository,
                                               MemberRepository memberRepository,
                                               MessageDeliveryRepository messageDeliveryRepository,
                                               EmailService emailService,
                                               RecordActivityUseCase recordActivity) {
        this.communicationRepository = communicationRepository;
        this.memberRepository = memberRepository;
        this.messageDeliveryRepository = messageDeliveryRepository;
        this.emailService = emailService;
        this.recordActivity = recordActivity;
        this.executorService = Executors.newCachedThreadPool(); // Java 17 compatible
    }

    /**
     * Sends a communication to all active members who have an email address, one message per address.
     * Nothing is stored when there are none ({@link CommunicationDomainException#noRecipients()}).
     *
     * @param communication the communication to send
     * @return the saved communication with delivery information
     */
    public Communication invoke(Communication communication) {
        List<Member> allMembers = Recipients.reachable(memberRepository.findByActive(true));
        if (allMembers.isEmpty()) {
            throw CommunicationDomainException.noRecipients();
        }

        communication.setSentToAllMembers(true);
        communication.markAsSent();
        logger.info("Sending communication '{}' to {} members", communication.getTitle(), allMembers.size());

        // First save the communication with pending deliveries
        for (Member member : allMembers) {
            MessageDelivery delivery = new MessageDelivery(
                    member,
                    communication,
                    MessageDelivery.DeliveryChannel.EMAIL
            );
            delivery.setStatus(MessageDelivery.DeliveryStatus.PENDING);
            communication.addDelivery(delivery);
        }
        
        Communication savedCommunication = communicationRepository.save(communication);
        
        recordActivity.record(ActivityType.MESSAGE_SENT, sentDescription(savedCommunication, allMembers.size()),
                "COMMUNICATION", savedCommunication.getId());

        // Send emails asynchronously
        sendEmailsAsync(savedCommunication, allMembers);
        
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
                    // Use retry-enabled email sending with callback for logging
                    boolean sent = emailService.sendSimpleEmailWithRetry(
                        member,
                        MessageTemplates.personalize(communication.getTitle(), member),
                        MessageTemplates.personalize(communication.getMessageContent(), member),
                        new EmailService.RetryCallback() {
                            @Override
                            public void onRetry(int currentAttempt, int maxAttempts) {
                                attempts.set(currentAttempt);
                                logger.info("Attempt {}/{} to send email to {}", 
                                    currentAttempt, maxAttempts, member.getEmail());
                            }
                        }
                    );
                    
                    // Update delivery status
                    if (sent) {
                        updateDeliveryStatus(communication, member, 
                            MessageDelivery.DeliveryStatus.SENT, 
                            null, attempts.get());
                        logger.info("Email sent successfully to {}", member.getEmail());
                    } else {
                        updateDeliveryStatus(communication, member, 
                            MessageDelivery.DeliveryStatus.FAILED, 
                            "Failed after max retry attempts", attempts.get());
                        logger.error("Email failed after retries for {}", member.getEmail());
                    }
                    
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
            logger.info("Finished sending communication '{}' to all members", communication.getTitle());
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
}
