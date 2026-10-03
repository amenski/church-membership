package io.github.membertracker.scheduler;

import io.github.membertracker.usecase.SendPaymentRemindersUseCase;
import io.github.membertracker.usecase.UpdateMissingPaymentCountersUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentReminderSchedulerTest {

    private UpdateMissingPaymentCountersUseCase counters;
    private SendPaymentRemindersUseCase reminders;
    private PaymentReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        counters = mock(UpdateMissingPaymentCountersUseCase.class);
        reminders = mock(SendPaymentRemindersUseCase.class);
        scheduler = new PaymentReminderScheduler(counters, reminders, 3);
    }

    private static String cronOf(String methodName) throws Exception {
        return PaymentReminderScheduler.class.getMethod(methodName).getAnnotation(Scheduled.class).cron();
    }

    @Test
    void countersRunOnTheFirstOfTheMonthAt0600() throws Exception {
        assertThat(cronOf("updateMissingPaymentCounters")).isEqualTo("0 0 6 1 * ?");
    }

    @Test
    void remindersRunOnTheFirstOfTheMonthAt0900() throws Exception {
        assertThat(cronOf("sendPaymentReminders")).isEqualTo("0 0 9 1 * ?");
    }

    @Test
    void counterMethodInvokesTheCountersUseCase() {
        scheduler.updateMissingPaymentCounters();

        verify(counters).invoke();
        verifyNoInteractions(reminders);
    }

    @Test
    void reminderMethodPassesTheConfiguredThreshold() {
        scheduler.sendPaymentReminders();

        verify(reminders).invoke(3);
        verifyNoInteractions(counters);
    }

    @Test
    void aFailingJobIsLoggedNotThrown() {
        doThrow(new IllegalStateException("boom")).when(counters).invoke();

        scheduler.updateMissingPaymentCounters();

        verify(counters).invoke();
    }
}
