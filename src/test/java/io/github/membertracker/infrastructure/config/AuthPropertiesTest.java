package io.github.membertracker.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String secret) {
        AuthProperties props = new AuthProperties();
        props.setJwtSecret(secret);
        return validator.validate(props).isEmpty();
    }

    @Test
    void nullSecretIsRejected() {
        assertThat(valid(null)).isFalse();
    }

    @Test
    void blankSecretIsRejected() {
        assertThat(valid("   ")).isFalse();
    }

    @Test
    void thirtyOneCharacterSecretIsRejected() {
        assertThat(valid("a".repeat(31))).isFalse();
    }

    @Test
    void thirtyTwoCharacterSecretIsAccepted() {
        assertThat(valid("a".repeat(32))).isTrue();
    }
}
