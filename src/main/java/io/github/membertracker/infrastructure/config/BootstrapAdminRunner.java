package io.github.membertracker.infrastructure.config;

import io.github.membertracker.usecase.BootstrapFirstAdminUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs {@link BootstrapFirstAdminUseCase} once the context is up (so Liquibase has finished). The values come from
 * the environment through app.bootstrap.admin-email and app.bootstrap.admin-password, empty by default. An invalid
 * value throws, which stops the application with the use case's message.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final BootstrapFirstAdminUseCase bootstrapFirstAdminUseCase;
    private final String adminEmail;
    private final String adminPassword;

    public BootstrapAdminRunner(BootstrapFirstAdminUseCase bootstrapFirstAdminUseCase,
                                @Value("${app.bootstrap.admin-email:}") String adminEmail,
                                @Value("${app.bootstrap.admin-password:}") String adminPassword) {
        this.bootstrapFirstAdminUseCase = bootstrapFirstAdminUseCase;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapFirstAdminUseCase.execute(adminEmail, adminPassword);
    }
}
