package io.github.membertracker.infrastructure.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateUserProfileRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(UpdateUserProfileRequest request) {
        return validator.validate(request).isEmpty();
    }

    private boolean fieldRejected(UpdateUserProfileRequest request, String field) {
        return validator.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankPhoneBecomesNullAndPasses(String blank) {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("Ada", "Lovelace", blank, null);

        assertThat(request.getPhone()).isNull();
        assertThat(valid(request)).isTrue();

        request.setPhone(blank);
        assertThat(request.getPhone()).isNull();
        assertThat(valid(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0612345678", "+39 333 1234567", "(06) 123-4567"})
    void aWellFormedPhonePasses(String phone) {
        assertThat(valid(new UpdateUserProfileRequest("Ada", "Lovelace", phone, null))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"5551234", "abcdefghij", "06123abc45"})
    void aMalformedPhoneIsRejected(String phone) {
        assertThat(fieldRejected(new UpdateUserProfileRequest("Ada", "Lovelace", phone, null), "phone")).isTrue();
    }

    @Test
    void namesAreCappedAt50Characters() {
        assertThat(valid(new UpdateUserProfileRequest("a".repeat(50), "b".repeat(50), null, null))).isTrue();
        assertThat(fieldRejected(new UpdateUserProfileRequest("a".repeat(51), "Lovelace", null, null), "firstName")).isTrue();
        assertThat(fieldRejected(new UpdateUserProfileRequest("Ada", "b".repeat(51), null, null), "lastName")).isTrue();
    }

    @Test
    void shortNamesAreFine() {
        assertThat(valid(new UpdateUserProfileRequest("A", "B", null, null))).isTrue();
    }

    @Test
    void bioIsCappedAt500Characters() {
        assertThat(valid(new UpdateUserProfileRequest("Ada", "Lovelace", null, "x".repeat(500)))).isTrue();
        assertThat(fieldRejected(new UpdateUserProfileRequest("Ada", "Lovelace", null, "x".repeat(501)), "bio")).isTrue();
    }
}
