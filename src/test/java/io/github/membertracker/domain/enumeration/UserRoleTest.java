package io.github.membertracker.domain.enumeration;

import io.github.membertracker.domain.exception.PaymentDomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleTest {

    @Test
    void toAuthority_prefixesTheCodeWithRole() {
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
}
