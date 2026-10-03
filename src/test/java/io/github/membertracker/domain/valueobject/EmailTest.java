package io.github.membertracker.domain.valueobject;

import io.github.membertracker.domain.exception.UserDomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void of_trimsAndLowercases() {
        Email e = Email.of("  John.Doe+tag@Example.COM ");
        assertThat(e.getValue()).isEqualTo("john.doe+tag@example.com");
        assertThat(e.getLocalPart()).isEqualTo("john.doe+tag");
        assertThat(e.getDomain()).isEqualTo("example.com");
        assertThat(e).hasToString("john.doe+tag@example.com");
    }

    @Test
    void equality_isCaseInsensitiveViaNormalisation() {
        assertThat(Email.of("A@b.com")).isEqualTo(Email.of("a@B.com")).hasSameHashCodeAs(Email.of("a@b.com"));
        assertThat(Email.of("a@b.com")).isNotEqualTo(Email.of("c@b.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "plainaddress", "@example.com", "user@", "user@example", "user@example.c",
        "user example@x.com", "user@@example.com"})
    void of_invalid_throwsInvalidUserData(String bad) {
        assertThatThrownBy(() -> Email.of(bad))
            .isInstanceOf(UserDomainException.class)
            .extracting("errorCode").isEqualTo(UserDomainException.INVALID_USER_DATA);
    }
}
