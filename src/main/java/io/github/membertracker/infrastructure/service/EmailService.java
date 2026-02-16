package io.github.membertracker.infrastructure.service;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final MailProperties mailProperties;
    private final TemplateEngine templateEngine;
    private JavaMailSender mailSender;

    public EmailService(MailProperties mailProperties, TemplateEngine templateEngine) {
        this.mailProperties = mailProperties;
        this.templateEngine = templateEngine;
        initializeMailSender();
    }

    private void initializeMailSender() {
        if (!mailProperties.isEnabled()) {
            logger.warn("Email service is disabled. Emails will not be sent.");
            return;
        }

        JavaMailSenderImpl mailSenderImpl = new JavaMailSenderImpl();
        mailSenderImpl.setHost(mailProperties.getSmtp().getHost());
        mailSenderImpl.setPort(mailProperties.getSmtp().getPort());
        mailSenderImpl.setUsername(mailProperties.getSmtp().getUsername());
        mailSenderImpl.setPassword(mailProperties.getSmtp().getPassword());

        Properties props = mailSenderImpl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(mailProperties.getSmtp().isAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(mailProperties.getSmtp().isStarttlsEnable()));
        props.put("mail.debug", String.valueOf(mailProperties.getSmtp().isDebug()));
        props.put("mail.smtp.connectiontimeout", mailProperties.getSmtp().getConnectionTimeout());
        props.put("mail.smtp.timeout", mailProperties.getSmtp().getTimeout());
        props.put("mail.smtp.writetimeout", mailProperties.getSmtp().getWriteTimeout());

        this.mailSender = mailSenderImpl;
        logger.info("Email service initialized for host: {}", mailProperties.getSmtp().getHost());
    }

    /**
     * Send a simple text email to a member with retry support
     */
    public boolean sendSimpleEmail(Member member, String subject, String content) {
        return sendSimpleEmailWithRetry(member, subject, content, null);
    }

    /**
     * Send a simple text email with custom retry callback for status updates
     */
    public boolean sendSimpleEmailWithRetry(Member member, String subject, String content, 
                                            RetryCallback retryCallback) {
        if (!mailProperties.isEnabled() || mailSender == null) {
            logger.warn("Email service is disabled or not initialized. Email not sent to: {}", member.getEmail());
            return false;
        }

        int maxAttempts = mailProperties.getRetry().getMaxAttempts();
        long initialDelay = mailProperties.getRetry().getInitialDelayMs();
        double multiplier = mailProperties.getRetry().getMultiplier();
        long maxDelay = mailProperties.getRetry().getMaxDelayMs();

        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (retryCallback != null) {
                    retryCallback.onRetry(attempt, maxAttempts);
                }
                
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                helper.setTo(member.getEmail());
                helper.setFrom(mailProperties.getFrom());
                helper.setSubject(subject);
                helper.setText(content, false);

                mailSender.send(message);
                logger.info("Email sent successfully to: {} on attempt {}", member.getEmail(), attempt);
                return true;
                
            } catch (MailException | MessagingException e) {
                lastException = e;
                logger.warn("Attempt {}/{} failed to send email to {}: {}", 
                    attempt, maxAttempts, member.getEmail(), e.getMessage());
                
                if (attempt < maxAttempts) {
                    long delay = calculateBackoffDelay(initialDelay, multiplier, attempt - 1, maxDelay);
                    logger.info("Retrying in {}ms...", delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted for {}: {}", member.getEmail(), ie.getMessage());
                        break;
                    }
                }
            }
        }
        
        logger.error("Failed to send email to {} after {} attempts: {}", 
            member.getEmail(), maxAttempts, lastException != null ? lastException.getMessage() : "Unknown error");
        return false;
    }

    /**
     * Calculate exponential backoff delay
     */
    private long calculateBackoffDelay(long initialDelay, double multiplier, int attempt, long maxDelay) {
        double delay = initialDelay * Math.pow(multiplier, attempt);
        return Math.min((long) delay, maxDelay);
    }

    /**
     * Retry callback interface for status updates during retries
     */
    public interface RetryCallback {
        void onRetry(int currentAttempt, int maxAttempts);
    }

    /**
     * Send a templated HTML email to a member
     */
    public boolean sendTemplatedEmail(Member member, String subject, String templateName, Map<String, Object> templateVariables) {
        if (!mailProperties.isEnabled() || mailSender == null) {
            logger.warn("Email service is disabled or not initialized. Templated email not sent to: {}", member.getEmail());
            return false;
        }

        try {
            // Prepare template context
            Context context = new Context();
            context.setVariables(templateVariables);
            context.setVariable("member", member);
            context.setVariable("churchName", mailProperties.getChurch().getName());
            context.setVariable("churchPhone", mailProperties.getChurch().getPhone());
            context.setVariable("churchEmail", mailProperties.getChurch().getEmail());

            // Process template
            String htmlContent = templateEngine.process("emails/" + templateName, context);

            // Create and send email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(member.getEmail());
            helper.setFrom(mailProperties.getFrom());
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML

            mailSender.send(message);
            logger.info("Templated email '{}' sent successfully to: {}", templateName, member.getEmail());
            return true;
        } catch (MailException | MessagingException e) {
            logger.error("Failed to send templated email to {}: {}", member.getEmail(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("Error processing email template '{}' for {}: {}", templateName, member.getEmail(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send a payment reminder email
     */
    public boolean sendPaymentReminder(Member member, int monthsMissed) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("monthsMissed", monthsMissed);
        // Use a default amount or fetch from last payment - for now use placeholder
        variables.put("paymentAmount", 50.0); // Default amount, TODO: Make configurable or fetch from last payment
        variables.put("dueDate", "End of this month"); // TODO: Calculate actual due date

        String subject = String.format("Payment Reminder: %d Month%s Overdue", 
            monthsMissed, monthsMissed > 1 ? "s" : "");

        return sendTemplatedEmail(member, subject, mailProperties.getTemplates().getPaymentReminder(), variables);
    }

    /**
     * Send a welcome email to new member
     */
    public boolean sendWelcomeEmail(Member member) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("welcomeMessage", "Welcome to our church community!");

        return sendTemplatedEmail(member, "Welcome to Our Church", 
            mailProperties.getTemplates().getWelcome(), variables);
    }

    /**
     * Send a general announcement
     */
    public boolean sendAnnouncement(Member member, String announcementTitle, String announcementContent) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("announcementTitle", announcementTitle);
        variables.put("announcementContent", announcementContent);

        return sendTemplatedEmail(member, announcementTitle, 
            mailProperties.getTemplates().getAnnouncement(), variables);
    }

    /**
     * Test if email service is properly configured
     */
    public boolean testConnection() {
        if (!mailProperties.isEnabled() || mailSender == null) {
            return false;
        }

        try {
            if (mailSender instanceof JavaMailSenderImpl) {
                ((JavaMailSenderImpl) mailSender).testConnection();
                logger.info("Email connection test successful");
                return true;
            }
            return false;
        } catch (MessagingException e) {
            logger.error("Email connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
