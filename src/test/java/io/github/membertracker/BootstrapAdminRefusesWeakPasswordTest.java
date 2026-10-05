package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** An invalid first-administrator value must stop the start with a message that names the variable, not the value. */
class BootstrapAdminRefusesWeakPasswordTest {

    private static final String WEAK_PASSWORD = "weakpass1";

    private static String[] arguments(String name, String email, String password) {
        return new String[] {"--spring.main.banner-mode=off",
            "--spring.datasource.url=jdbc:h2:mem:" + name + ";MODE=MySQL",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.liquibase.change-log=classpath:db/h2-master.xml",
            "--app.bootstrap.admin-email=" + email,
            "--app.bootstrap.admin-password=" + password};
    }

    private static void run(String... arguments) {
        new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE).run(arguments).close();
    }

    @Test
    void startupFailsOnAWeakPasswordWithoutEchoingIt() {
        assertThatThrownBy(() -> run(arguments("bootstrapweak", "owner@example.org", WEAK_PASSWORD)))
            .hasStackTraceContaining("BOOTSTRAP_ADMIN_PASSWORD is not strong enough")
            .satisfies(e -> assertThat(stackTraceText(e)).doesNotContain(WEAK_PASSWORD));
    }

    @Test
    void startupFailsOnABadEmail() {
        assertThatThrownBy(() -> run(arguments("bootstrapbademail", "not-an-email", "Str0ng-Passw0rd!")))
            .hasStackTraceContaining("BOOTSTRAP_ADMIN_EMAIL is not a valid email address");
    }

    private static String stackTraceText(Throwable e) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }
}
