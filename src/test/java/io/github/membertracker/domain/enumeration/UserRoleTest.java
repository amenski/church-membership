package io.github.membertracker.domain.enumeration;

import io.github.membertracker.domain.exception.PaymentDomainException;
import io.github.membertracker.domain.exception.UserDomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleTest {

    @Test
    void fromCode_validCodes_roundTripForEveryRole() {
        for (UserRole role : UserRole.values()) {
            assertThat(UserRole.fromCode(role.getCode())).isEqualTo(role);
        }
    }

    @Test
    void fromCode_isCaseInsensitive() {
        assertThat(UserRole.fromCode("admin")).isEqualTo(UserRole.ADMIN);
        assertThat(UserRole.fromCode("Staff")).isEqualTo(UserRole.STAFF);
    }

    @Test
    void fromCode_unknownOrNull_throwsInvalidUserData() {
        assertThatThrownBy(() -> UserRole.fromCode("SUPERUSER"))
            .isInstanceOf(UserDomainException.class)
            .extracting("errorCode").isEqualTo(UserDomainException.INVALID_USER_DATA);
        assertThatThrownBy(() -> UserRole.fromCode(null))
            .isInstanceOf(UserDomainException.class)
            .extracting("errorCode").isEqualTo(UserDomainException.INVALID_USER_DATA);
        assertThatThrownBy(() -> UserRole.fromCode(""))
            .isInstanceOf(UserDomainException.class);
    }

    @Test
    void isValid_andAuthority() {
        assertThat(UserRole.isValid("volunteer")).isTrue();
        assertThat(UserRole.isValid("nope")).isFalse();
        assertThat(UserRole.isValid(null)).isFalse();
        assertThat(UserRole.ADMIN.toAuthority()).isEqualTo("ROLE_ADMIN");
    }

    // PaymentMethod conversion

    @Test
    void paymentMethod_fromCode_validCaseInsensitive() {
        assertThat(PaymentMethod.fromCode("CASH")).isEqualTo(PaymentMethod.CASH);
        assertThat(PaymentMethod.fromCode("bank_transfer")).isEqualTo(PaymentMethod.BANK_TRANSFER);
        for (PaymentMethod m : PaymentMethod.values()) {
            assertThat(PaymentMethod.fromCode(m.getCode())).isEqualTo(m);
        }
    }

    @Test
    void paymentMethod_fromCode_unknownOrNull_throwsNotSupported() {
        assertThatThrownBy(() -> PaymentMethod.fromCode("BITCOIN"))
            .isInstanceOf(PaymentDomainException.class)
            .extracting("errorCode").isEqualTo(PaymentDomainException.PAYMENT_METHOD_NOT_SUPPORTED);
        assertThatThrownBy(() -> PaymentMethod.fromCode(null))
            .isInstanceOf(PaymentDomainException.class)
            .extracting("errorCode").isEqualTo(PaymentDomainException.PAYMENT_METHOD_NOT_SUPPORTED);
    }

    @Test
    void paymentMethod_isValid() {
        assertThat(PaymentMethod.isValid("credit_card")).isTrue();
        assertThat(PaymentMethod.isValid("BITCOIN")).isFalse();
        assertThat(PaymentMethod.isValid(null)).isFalse();
    }
}
