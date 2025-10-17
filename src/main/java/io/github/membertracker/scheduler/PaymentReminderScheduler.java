package io.github.membertracker.scheduler;

import io.github.membertracker.usecase.SendPaymentRemindersUseCase;
import io.github.membertracker.usecase.UpdateMissingPaymentCountersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PaymentReminderScheduler.class);

    private final UpdateMissingPaymentCountersUseCase updateMissingPaymentCountersUseCase;
    private final SendPaymentRemindersUseCase sendPaymentRemindersUseCase;

    @Autowired
    public PaymentReminderScheduler(UpdateMissingPaymentCountersUseCase updateMissingPaymentCountersUseCase,
                                   SendPaymentRemindersUseCase sendPaymentRemindersUseCase) {
        this.updateMissingPaymentCountersUseCase = updateMissingPaymentCountersUseCase;
        this.sendPaymentRemindersUseCase = sendPaymentRemindersUseCase;
    }

    /**
     * Updates missing payment counters every day at 6 AM
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void updateMissingPaymentCounters() {
        try {
            logger.info("Starting update of missing payment counters");
            updateMissingPaymentCountersUseCase.invoke();
            logger.info("Successfully updated missing payment counters");
        } catch (Exception e) {
            logger.error("Failed to update missing payment counters", e);
        }
    }

    /**
     * Sends payment reminders every day at 9 AM for members who have missed 2 or more payments
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendPaymentReminders() {
        try {
            logger.info("Starting payment reminder process");
            var result = sendPaymentRemindersUseCase.invoke(2); // Send reminders for 2+ months missed
            if (result != null) {
                logger.info("Successfully sent payment reminders to {} members",
                    result.getDeliveries() != null ? result.getDeliveries().size() : 0);
            } else {
                logger.info("No payment reminders needed - no members with overdue payments");
            }
        } catch (Exception e) {
            logger.error("Failed to send payment reminders", e);
        }
    }
}
