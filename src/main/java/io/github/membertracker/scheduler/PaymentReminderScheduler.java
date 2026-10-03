package io.github.membertracker.scheduler;

import io.github.membertracker.usecase.SendPaymentRemindersUseCase;
import io.github.membertracker.usecase.UpdateMissingPaymentCountersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PaymentReminderScheduler.class);

    private final UpdateMissingPaymentCountersUseCase updateMissingPaymentCountersUseCase;
    private final SendPaymentRemindersUseCase sendPaymentRemindersUseCase;
    private final int monthsThreshold;

    @Autowired
    public PaymentReminderScheduler(UpdateMissingPaymentCountersUseCase updateMissingPaymentCountersUseCase,
                                   SendPaymentRemindersUseCase sendPaymentRemindersUseCase,
                                   @Value("${app.payment.reminder.months-threshold:3}") int monthsThreshold) {
        this.updateMissingPaymentCountersUseCase = updateMissingPaymentCountersUseCase;
        this.sendPaymentRemindersUseCase = sendPaymentRemindersUseCase;
        this.monthsThreshold = monthsThreshold;
    }

    /**
     * Counts the previous month as missed for active members without a payment, on the 1st of every month at 6 AM
     */
    @Scheduled(cron = "0 0 6 1 * ?")
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
     * Sends payment reminders on the 1st of every month at 9 AM (after the counters are updated) to active members
     * who have missed at least {@code app.payment.reminder.months-threshold} months
     */
    @Scheduled(cron = "0 0 9 1 * ?")
    public void sendPaymentReminders() {
        try {
            logger.info("Starting payment reminder process");
            var result = sendPaymentRemindersUseCase.invoke(monthsThreshold);
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
