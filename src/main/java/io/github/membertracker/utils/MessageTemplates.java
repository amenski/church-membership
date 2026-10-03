package io.github.membertracker.utils;

import io.github.membertracker.domain.model.Member;

/**
 * Fills the placeholders of a stored communication for one recipient, at send time.
 */
public final class MessageTemplates {

    private static final String MEMBER_NAME = "{{member_name}}";
    private static final String FALLBACK_NAME = "member";

    private MessageTemplates() {
        // Utility class - prevent instantiation
    }

    /**
     * Replaces every {@code {{member_name}}} with the member's trimmed name ("member" when the name is
     * null or blank). Any other text is left untouched; null text becomes "".
     */
    public static String personalize(String text, Member member) {
        if (text == null) {
            return "";
        }
        String name = member == null ? null : member.getName();
        String replacement = name == null || name.isBlank() ? FALLBACK_NAME : name.trim();
        return text.replace(MEMBER_NAME, replacement);
    }
}
