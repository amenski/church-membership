package io.github.membertracker.infrastructure.security;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.model.Payment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

/**
 * The one rule for ARCHIVED members: only an ADMIN may see them. Every controller path that reads a member by id
 * (or embeds a member in a payment or a delivery) goes through here.
 * <ul>
 *   <li>{@link #visible(Optional)}: a member looked up by id; an archived one is "not found" for everybody but an admin.</li>
 *   <li>{@link #redact(List)}: payments and deliveries keep their history, but an archived member embedded in them
 *       loses the email and phone for a non-admin (the name stays, so the history still reads).</li>
 * </ul>
 */
public final class ArchivedVisibility {

    private ArchivedVisibility() {
    }

    public static boolean canSeeArchived() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static Optional<Member> visible(Optional<Member> member) {
        return member.filter(m -> m.getStatus() != MemberStatus.ARCHIVED || canSeeArchived());
    }

    public static <T> List<T> redact(List<T> items) {
        if (!canSeeArchived()) {
            items.forEach(ArchivedVisibility::redactOne);
        }
        return items;
    }

    public static <T> T redact(T item) {
        if (!canSeeArchived()) {
            redactOne(item);
        }
        return item;
    }

    private static void redactOne(Object item) {
        Member member = item instanceof Payment payment ? payment.getMember()
                : item instanceof MessageDelivery delivery ? delivery.getRecipient() : null;
        if (member != null && member.getStatus() == MemberStatus.ARCHIVED) {
            member.setEmail(null);
            member.setPhone(null);
        }
    }
}
