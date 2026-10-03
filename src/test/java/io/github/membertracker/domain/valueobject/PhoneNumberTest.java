package io.github.membertracker.domain.valueobject;

import io.github.membertracker.domain.exception.MemberDomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberTest {

    @Test
    void of_stripsSpacesDashesAndParentheses() {
        assertThat(PhoneNumber.of(" (555) 123-4567 ").getValue()).isEqualTo("5551234567");
        assertThat(PhoneNumber.of("+44 20 7946 0958").getValue()).isEqualTo("+442079460958");
    }

    @Test
    void toFormattedString_tenDigitsAreFormattedAndInternationalIsUnchanged() {
        assertThat(PhoneNumber.of("5551234567").toFormattedString()).isEqualTo("(555) 123-4567");
        assertThat(PhoneNumber.of("+442079460958").toFormattedString()).isEqualTo("+442079460958");
        assertThat(PhoneNumber.of("555-123-4567")).hasToString("(555) 123-4567");
    }

    @Test
    void equality_basedOnNormalisedValue() {
        assertThat(PhoneNumber.of("555-123-4567")).isEqualTo(PhoneNumber.of("(555) 123 4567"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "12345", "123456789", "555-CALL-NOW1", "abcdefghij", "55512345678x", "++5551234567"})
    void of_invalid_throwsInvalidMemberData(String bad) {
        assertThatThrownBy(() -> PhoneNumber.of(bad))
            .isInstanceOf(MemberDomainException.class)
            .extracting("errorCode").isEqualTo(MemberDomainException.INVALID_MEMBER_DATA);
    }
}
