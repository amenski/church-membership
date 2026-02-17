package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SendCommunicationToAllMembersUseCase {

    private static final Logger logger = LoggerFactory.getLogger(SendCommunicationToAllMembersUseCase.class);
    
    private final CommunicationRepository communicationRepository;
    private final MemberRepository memberRepository;
    private final EmailService emailService;
    private final ExecutorService executorService;

    public SendCommunicationToAllMembersUseCase(CommunicationRepository communicationRepository,
                                               MemberRepository memberRepository,
                                               EmailService emailService) {
        this.communicationRepository = communicationRepository;
        this.memberRepository = memberRepository;
        this.emailService = emailService;
        this.executorService = Executors.newCachedThreadPool(); // Java 17 compatible
    }

    /**
     * Sends a communication to all members.
     *
     * @param communication the communication to send
     * @return the saved communication with delivery information
     */
    public Communication invoke(Communication communication) {
        communication.setSentToAllMembers(true);
        communication.setSentDate(LocalDateTime.now());

        List<Member> allMembers = memberRepository.findAll();
        logger.info("Sending communication '{}' to {} members", communication.getTitle(), allMembers.size());

        // First save the communication with pending deliveries
        for (Member member : allMembers) {
            MessageDelivery delivery = new MessageDelivery(
                    member,
                    communication,
                    MessageDelivery.DeliveryChannel.EMAIL
            );
            delivery.setStatus(MessageDelivery.DeliveryStatus.PENDING);
            communication.getDeliveries().add(delivery);
        }
        
        Communication savedCommunication = communicationRepository.save(communication);
        
        // Send emails asynchronously
        sendEmailsAsync(savedCommunication, allMembers);
        
        return savedCommunication;
    }
    
    private void sendEmailsAsync(Communication communication, List<Member> members) {
        executorService.submit(() -> {
            for (Member member : members) {
                try {
                    // Use retry-enabled email sending with callback for logging
                    boolean sent = emailService.sendSimpleEmailWithRetry(
                        member,
                        communication.getTitle(),
                        communication.getMessageContent(),
                        new EmailService.RetryCallback() {
                            @Override
                            public void onRetry(int currentAttempt, int maxAttempts) {
                                logger.info("Attempt {}/{} to send email to {}", 
                                    currentAttempt, maxAttempts, member.getEmail());
                            }
                        }
                    );
                    
                    // Update delivery status
                    if (sent) {
                        updateDeliveryStatus(communication, member, 
                            MessageDelivery.DeliveryStatus.SENT, 
                            null);
                        logger.info("Email sent successfully to {}", member.getEmail());
                    } else {
                        updateDeliveryStatus(communication, member, 
                            MessageDelivery.DeliveryStatus.FAILED, 
                            "Failed after max retry attempts");
                        logger.error("Email failed after retries for {}", member.getEmail());
                    }
                    
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
            logger.info("Finished sending communication '{}' to all members", communication.getTitle());
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
}
