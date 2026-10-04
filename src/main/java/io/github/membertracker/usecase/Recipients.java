package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Who a message can actually reach. Members who cannot receive messages (any status but MEMBER) and members without an email address are left out, and members who
 * share an address get one message between them (the one with the lowest id).
 */
public final class Recipients {

    private Recipients() {
    }

    public static List<Member> reachable(List<Member> members) {
        Set<String> seen = new HashSet<>();
        return members.stream()
                .filter(member -> member.getStatus().canReceiveMessages())
                .filter(member -> member.getEmail() != null && !member.getEmail().isBlank())
                .sorted(Comparator.comparing(Member::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .filter(member -> seen.add(member.getEmail().trim().toLowerCase(Locale.ROOT)))
                .toList();
    }
}
