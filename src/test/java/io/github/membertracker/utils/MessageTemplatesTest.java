package io.github.membertracker.utils;

import io.github.membertracker.domain.model.Member;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTemplatesTest {

    private static Member named(String name) {
        Member m = new Member();
        m.setName(name);
        return m;
    }

    @Test
    void placeholderIsReplacedEverywhereItAppears() {
        String result = MessageTemplates.personalize("Dear {{member_name}}, thanks {{member_name}}!", named("Alice"));

        assertThat(result).isEqualTo("Dear Alice, thanks Alice!");
    }

    @Test
    void nameIsTrimmed() {
        assertThat(MessageTemplates.personalize("Hi {{member_name}}", named("  Alice  "))).isEqualTo("Hi Alice");
    }

    @Test
    void nullOrBlankNameFallsBackToMember() {
        assertThat(MessageTemplates.personalize("Dear {{member_name}}", named(null))).isEqualTo("Dear member");
        assertThat(MessageTemplates.personalize("Dear {{member_name}}", named("   "))).isEqualTo("Dear member");
    }

    @Test
    void textWithoutPlaceholderIsUntouched() {
        assertThat(MessageTemplates.personalize("Service at 10 {name} {{other}}", named("Alice")))
                .isEqualTo("Service at 10 {name} {{other}}");
    }

    @Test
    void nullTextBecomesEmpty() {
        assertThat(MessageTemplates.personalize(null, named("Alice"))).isEmpty();
    }

    @Test
    void regexCharactersInTheNameAreInsertedLiterally() {
        assertThat(MessageTemplates.personalize("Dear {{member_name}}", named("$1 \\ (A)")))
                .isEqualTo("Dear $1 \\ (A)");
    }
}
