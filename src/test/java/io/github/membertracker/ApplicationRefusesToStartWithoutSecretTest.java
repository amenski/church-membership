package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** The default profile must stop at startup when JWT_SECRET is missing, not fall back to a public key. */
class ApplicationRefusesToStartWithoutSecretTest {

    @Test
    void startupFailsWhenTheJwtSecretIsEmpty() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off",
                        "--auth.jwt-secret=",
                        "--spring.datasource.url=jdbc:h2:mem:nosecret;MODE=MySQL",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.liquibase.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"))
                .hasStackTraceContaining("auth.jwt-secret");
    }
}
