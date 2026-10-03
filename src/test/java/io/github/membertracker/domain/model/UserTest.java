package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.exception.UserDomainException;
import io.github.membertracker.domain.valueobject.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final String STRONG = "Abcdef1!";

    private User user(UserRole role) {
        return new User(Email.of("user@example.com"), "irrelevant", role);
    }

    private User user() {
        return user(UserRole.MEMBER);
    }

    // validatePasswordStrength

    @Test
    void validatePasswordStrength_acceptsStrongPasswordsIncludingBoundaryLength() {
        assertThatCode(() -> User.validatePasswordStrength(STRONG)).doesNotThrowAnyException();
        assertThatCode(() -> User.validatePasswordStrength("Zz9@Zz9@Zz9@")).doesNotThrowAnyException();
        for (char special : "@$!%*?&-_#.".toCharArray()) {
            assertThatCode(() -> User.validatePasswordStrength("Abcdef1" + special)).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Abcdef1-",
        "Abcdef1_x",
        "Abcdef1#",
        "Abcdef 1!",                       // spaces are allowed (passphrases)
        "Correct Horse 9 Battery-Staple"
    })
    void validatePasswordStrength_acceptsAnySpecialCharacterAndSpaces(String ok) {
        assertThatCode(() -> User.validatePasswordStrength(ok)).doesNotThrowAnyException();
    }

    @Test
    void validatePasswordStrength_limitsTheLengthInUtf8BytesNotCharacters() {
        assertThatCode(() -> User.validatePasswordStrength("Aa1!" + "x".repeat(68))).doesNotThrowAnyException(); // 72 bytes
        assertThatCode(() -> User.validatePasswordStrength("Aa1!" + "\u00e9".repeat(34))).doesNotThrowAnyException(); // 72 bytes
        assertThatThrownBy(() -> User.validatePasswordStrength("Aa1!" + "x".repeat(69))) // 73 bytes
            .isInstanceOf(UserDomainException.class);
        assertThatThrownBy(() -> User.validatePasswordStrength("Aa1!" + "\u00e9".repeat(35))) // 74 bytes, 39 chars
            .isInstanceOf(UserDomainException.class);
    }

    @Test
    void validatePasswordStrength_explainsTheRuleInOneMessage() {
        assertThatThrownBy(() -> User.validatePasswordStrength("weak"))
            .hasMessage("Password must be 8 to 72 characters (bytes) and contain an uppercase letter, "
                + "a lowercase letter, a digit and a special character");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "Abcde1!",      // 7 chars
        "abcdef1!",     // no upper
        "ABCDEF1!",     // no lower
        "Abcdefg!",     // no digit
        "Abcdefg1",     // no special
        "Abcdef 1",     // whitespace is not a special character
        "   Abcd1   "   // whitespace only as the "special"
    })
    void validatePasswordStrength_rejectsWeakPasswords(String weak) {
        assertThatThrownBy(() -> User.validatePasswordStrength(weak))
            .isInstanceOf(UserDomainException.class)
            .extracting("errorCode").isEqualTo(UserDomainException.WEAK_PASSWORD);
    }

    // changePassword

    @Test
    void changePassword_updatesPasswordAndResetsFailedAttempts() {
        User u = user();
        u.setLastPasswordChange(LocalDateTime.now().minusDays(100));
        u.recordFailedLoginAttempt();
        u.recordFailedLoginAttempt();
        assertThat(u.isPasswordExpired()).isTrue();

        u.changePassword(STRONG);

        assertThat(u.getPassword()).isEqualTo(STRONG);
        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.isPasswordExpired()).isFalse();
    }

    @Test
    void changePassword_storesEncodedValueWithoutStrengthCheck() {
        User u = user();
        String bcryptHash = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BUwYhXKZl1u2bQ3vGZ0H8s6y9Ety";

        u.changePassword(bcryptHash);

        assertThat(u.getPassword()).isEqualTo(bcryptHash);
    }

    // failed logins

    @Test
    void recordFailedLoginAttempt_locksOnFifthAttemptNotBefore() {
        User u = user();
        for (int i = 1; i <= 4; i++) {
            u.recordFailedLoginAttempt();
            assertThat(u.isAccountLocked()).as("after attempt %d", i).isFalse();
            assertThat(u.isAccountNonLocked()).isTrue();
        }

        u.recordFailedLoginAttempt();

        assertThat(u.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(u.isAccountLocked()).isTrue();
        assertThat(u.isAccountNonLocked()).isFalse();
    }

    @Test
    void recordFailedLoginAttempt_stayLockedBeyondThreshold() {
        User u = user();
        for (int i = 0; i < 7; i++) {
            u.recordFailedLoginAttempt();
        }
        assertThat(u.getFailedLoginAttempts()).isEqualTo(7);
        assertThat(u.isAccountLocked()).isTrue();
    }

    @Test
    void resetFailedLoginAttempts_unlocksAccount() {
        User u = user();
        for (int i = 0; i < 5; i++) {
            u.recordFailedLoginAttempt();
        }

        u.resetFailedLoginAttempts();

        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.isAccountLocked()).isFalse();
    }

    // enable / disable

    @Test
    void disable_thenEnable_togglesEnabledFlag() {
        User u = user();
        assertThat(u.isEnabled()).isTrue();
        u.disable();
        assertThat(u.isEnabled()).isFalse();
        u.enable();
        assertThat(u.isEnabled()).isTrue();
    }

    @Test
    void enable_whenAlreadyEnabled_throws() {
        assertThatThrownBy(() -> user().enable())
            .isInstanceOf(UserDomainException.class)
            .extracting("errorCode").isEqualTo(UserDomainException.USER_ALREADY_ENABLED);
    }

    @Test
    void disable_whenAlreadyDisabled_throws() {
        User u = user();
        u.disable();
        assertThatThrownBy(u::disable)
            .isInstanceOf(UserDomainException.class)
            .extracting("errorCode").isEqualTo(UserDomainException.USER_ALREADY_DISABLED);
    }

    // role helpers

    @Test
    void roleHelpers_isAdminAndIsManagerOrAdmin() {
        assertThat(user(UserRole.ADMIN).isAdmin()).isTrue();
        assertThat(user(UserRole.STAFF).isAdmin()).isFalse();
        assertThat(user(UserRole.ADMIN).isManagerOrAdmin()).isTrue();
        assertThat(user(UserRole.STAFF).isManagerOrAdmin()).isTrue();
        assertThat(user(UserRole.VOLUNTEER).isManagerOrAdmin()).isFalse();
        assertThat(user(UserRole.MEMBER).isManagerOrAdmin()).isFalse();
    }

    @Test
    void getAuthorities_usesRolePrefixAndDefaultsToMember() {
        assertThat(user(UserRole.STAFF).getAuthorities()).extracting("authority").containsExactly("ROLE_STAFF");
        User noRole = user();
        noRole.setRole(null);
        assertThat(noRole.getAuthorities()).extracting("authority").containsExactly("ROLE_MEMBER");
    }

    @Test
    void newUserDefaultsToMemberRoleEnabledAndUnlocked() {
        User u = new User(Email.of("a@b.co"), "pw");
        assertThat(u.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(u.isEnabled()).isTrue();
        assertThat(u.isAccountNonLocked()).isTrue();
        assertThat(u.getUsername()).isEqualTo("a@b.co");
    }

    @Test
    void promoteToRole_changesRole_butRejectsSameRoleAndMemberDemotion() {
        User u = user(UserRole.MEMBER);
        u.promoteToRole(UserRole.STAFF);
        assertThat(u.getRole()).isEqualTo(UserRole.STAFF);

        assertThatThrownBy(() -> u.promoteToRole(UserRole.STAFF)).isInstanceOf(UserDomainException.class);
        assertThatThrownBy(() -> u.promoteToRole(UserRole.MEMBER)).isInstanceOf(UserDomainException.class);
        assertThat(u.getRole()).isEqualTo(UserRole.STAFF);
    }
}
