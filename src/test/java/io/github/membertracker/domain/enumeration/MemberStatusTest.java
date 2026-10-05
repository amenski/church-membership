package io.github.membertracker.domain.enumeration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The status rules table of docs/archive/person-membership-plan.md. */
class MemberStatusTest {

    @Test
    void thereAreFiveStatuses() {
        assertThat(MemberStatus.values()).containsExactly(
            MemberStatus.MEMBER, MemberStatus.INACTIVE, MemberStatus.DECEASED, MemberStatus.TRANSFERRED, MemberStatus.ARCHIVED);
    }

    @Test
    void onlyMemberCountsForDuesAndReceivesMessages() {
        for (MemberStatus status : MemberStatus.values()) {
            boolean isMember = status == MemberStatus.MEMBER;
            assertThat(status.countsForDues()).as("countsForDues %s", status).isEqualTo(isMember);
            assertThat(status.canReceiveMessages()).as("canReceiveMessages %s", status).isEqualTo(isMember);
        }
    }

    @Test
    void everyoneButArchivedIsListedByDefault() {
        for (MemberStatus status : MemberStatus.values()) {
            assertThat(status.listedByDefault()).as("listedByDefault %s", status).isEqualTo(status != MemberStatus.ARCHIVED);
        }
    }

    @Test
    void theNamesAreTheStoredValues() {
        assertThat(MemberStatus.valueOf("TRANSFERRED")).isSameAs(MemberStatus.TRANSFERRED);
    }
}
