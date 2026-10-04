package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientsTest {

    private static Member member(Long id, String email) {
        Member m = new Member("Name " + id, email, null);
        m.setId(id);
        return m;
    }

    @Test
    void nullAndBlankEmailsAreDropped() {
        Member none = member(1L, null);
        Member empty = member(2L, "");
        Member blank = member(3L, "   ");
        Member ok = member(4L, "ok@example.com");

        assertThat(Recipients.reachable(List.of(none, empty, blank, ok))).containsExactly(ok);
    }

    @Test
    void twoMembersOneAddressKeepsTheLowestId() {
        Member second = member(7L, "family@example.com");
        Member first = member(3L, "family@example.com");

        assertThat(Recipients.reachable(List.of(second, first))).containsExactly(first);
    }

    @Test
    void caseAndSurroundingSpacesDoNotMakeAnAddressDifferent() {
        Member lower = member(1L, "family@example.com");
        Member upper = member(2L, " FAMILY@Example.com ");
        Member other = member(3L, "other@example.com");

        assertThat(Recipients.reachable(List.of(upper, other, lower))).containsExactly(lower, other);
    }

    @Test
    void anEmptyListGivesAnEmptyList() {
        assertThat(Recipients.reachable(List.of())).isEmpty();
    }

    @Test
    void membersWithoutAnIdAreKeptInTheOrderGiven() {
        Member a = member(null, "a@example.com");
        Member b = member(null, "b@example.com");

        assertThat(Recipients.reachable(List.of(a, b))).containsExactly(a, b);
    }
}
