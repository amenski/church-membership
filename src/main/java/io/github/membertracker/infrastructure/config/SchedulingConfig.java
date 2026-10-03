package io.github.membertracker.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} processing (see PaymentReminderScheduler). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
