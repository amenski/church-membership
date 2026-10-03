package io.github.membertracker.infrastructure.service;

import io.github.membertracker.infrastructure.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The app.mail.smtp.* keys in application.properties and application-dev.properties must reach the JavaMail
 * properties that EmailService builds. A key that does not bind silently keeps its Java default, so each
 * assertion uses a value that differs from the default.
 */
class MailPropertiesBindingTest {

    @EnableConfigurationProperties(MailProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withInitializer(new ConfigDataApplicationContextInitializer());

    private Properties javaMailProperties(ApplicationContextRunner configured) {
        Properties[] result = new Properties[1];
        configured.run(context -> {
            EmailService service = new EmailService(context.getBean(MailProperties.class));
            JavaMailSenderImpl sender = (JavaMailSenderImpl) ReflectionTestUtils.getField(service, "mailSender");
            result[0] = sender.getJavaMailProperties();
        });
        return result[0];
    }

    @Test
    void devProfileKeysReachJavaMail() {
        Properties props = javaMailProperties(runner.withPropertyValues("spring.profiles.active=dev"));

        assertThat(props.get("mail.smtp.starttls.enable")).isEqualTo("false");
        assertThat(props.get("mail.smtp.auth")).isEqualTo("false");
        assertThat(props.get("mail.debug")).isEqualTo("true");
    }

    @Test
    void starttlsIsOnByDefaultAndTheEnvironmentVariableSwitchesItOff() {
        assertThat(javaMailProperties(runner).get("mail.smtp.starttls.enable")).isEqualTo("true");

        Properties off = javaMailProperties(runner.withSystemProperties("MAIL_STARTTLS=false"));
        assertThat(off.get("mail.smtp.starttls.enable")).isEqualTo("false");
    }

    @Test
    void authDebugAndTimeoutsFromTheEnvironmentReachJavaMail() {
        Properties props = javaMailProperties(runner
                .withSystemProperties("MAIL_AUTH=false", "MAIL_DEBUG=true")
                .withPropertyValues(
                        "app.mail.smtp.connection-timeout=1111",
                        "app.mail.smtp.timeout=2222",
                        "app.mail.smtp.write-timeout=3333"));

        assertThat(props.get("mail.smtp.auth")).isEqualTo("false");
        assertThat(props.get("mail.debug")).isEqualTo("true");
        assertThat(props.get("mail.smtp.connectiontimeout")).isEqualTo(1111);
        assertThat(props.get("mail.smtp.timeout")).isEqualTo(2222);
        assertThat(props.get("mail.smtp.writetimeout")).isEqualTo(3333);
    }
}
