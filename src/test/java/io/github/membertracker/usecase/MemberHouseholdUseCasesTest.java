package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.HouseholdDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The household of a member's person: set on create, moved or cleared on update, left alone when not mentioned. */
class MemberHouseholdUseCasesTest {

    private MemberRepository repo;
    private RecordActivityUseCase recordActivity;
    private Member stored;

    @BeforeEach
    void setUp() {
        repo = mock(MemberRepository.class);
        recordActivity = mock(RecordActivityUseCase.class);
        stored = new Member("Abebe", "abebe@example.com", "+390611111111");
        stored.setId(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(stored));
        // The real repository resolves the household and fills in its name; the mock stands in for that.
        when(repo.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            if (member.getId() == null) {
                member.setId(1L);
            }
            member.setHouseholdName(member.getHouseholdId() == null ? null : "House " + member.getHouseholdId());
            return member;
        });
    }

    private UpdateMemberUseCase update() {
        return new UpdateMemberUseCase(repo, recordActivity);
    }

    @Test
    void createWithAHouseholdPassesItToTheRepositoryAndRecordsTheAssignment() {
        Member created = new SaveMemberUseCase(repo, recordActivity).invoke("Abebe", null, null, null, null, 5L);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getHouseholdId()).isEqualTo(5L);
        assertThat(created.getHouseholdName()).isEqualTo("House 5");
        verify(recordActivity).record(ActivityType.MEMBER_CREATED, "Member Abebe was added", "MEMBER", 1L);
        verify(recordActivity).record(ActivityType.MEMBER_HOUSEHOLD_CHANGED, "Member Abebe was added to household House 5",
            "MEMBER", 1L);
    }

    @Test
    void createWithoutAHouseholdRecordsNoHouseholdActivity() {
        new SaveMemberUseCase(repo, recordActivity).invoke("Abebe", null, null, null, null);

        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_HOUSEHOLD_CHANGED), any(), any(), any());
    }

    @Test
    void anUnknownHouseholdFromTheRepositoryReachesTheCallerAndRecordsNothing() {
        when(repo.save(any(Member.class))).thenThrow(HouseholdDomainException.notFound());

        assertThatThrownBy(() -> new SaveMemberUseCase(repo, recordActivity).invoke("Abebe", null, null, null, null, 99L))
            .isInstanceOf(HouseholdDomainException.class);
        assertThatThrownBy(() -> update().invoke(1L, "Abebe", null, null, null, null, null, true, 99L))
            .isInstanceOf(HouseholdDomainException.class);

        verify(recordActivity, never()).record(any(), any(), any(), any());
    }

    @Test
    void updateAssignsAHouseholdToAMemberWithNone() {
        update().invoke(1L, "Abebe", "abebe@example.com", "+390611111111", null, null, null, true, 5L).orElseThrow();

        assertThat(stored.getHouseholdId()).isEqualTo(5L);
        verify(recordActivity).record(ActivityType.MEMBER_UPDATED, "Member Abebe was updated", "MEMBER", 1L);
        verify(recordActivity).record(ActivityType.MEMBER_HOUSEHOLD_CHANGED, "Member Abebe was added to household House 5",
            "MEMBER", 1L);
    }

    @Test
    void updateMovesAMemberBetweenHouseholds() {
        stored.setHouseholdId(5L);
        stored.setHouseholdName("House 5");

        update().invoke(1L, "Abebe", null, null, null, null, null, true, 6L).orElseThrow();

        assertThat(stored.getHouseholdId()).isEqualTo(6L);
        verify(recordActivity).record(ActivityType.MEMBER_HOUSEHOLD_CHANGED, "Member Abebe was moved to household House 6",
            "MEMBER", 1L);
    }

    @Test
    void anExplicitNullRemovesTheMemberFromItsHousehold() {
        stored.setHouseholdId(5L);
        stored.setHouseholdName("House 5");

        update().invoke(1L, "Abebe", null, null, null, null, null, true, null).orElseThrow();

        assertThat(stored.getHouseholdId()).isNull();
        verify(recordActivity).record(ActivityType.MEMBER_HOUSEHOLD_CHANGED, "Member Abebe was removed from household House 5",
            "MEMBER", 1L);
    }

    @Test
    void notMentioningTheHouseholdKeepsItAndRecordsNothingAboutIt() {
        stored.setHouseholdId(5L);
        stored.setHouseholdName("House 5");

        update().invoke(1L, "Abebe", null, null, null, null, null).orElseThrow();

        assertThat(stored.getHouseholdId()).isEqualTo(5L);
        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_HOUSEHOLD_CHANGED), any(), any(), any());
    }

    @Test
    void sendingTheSameHouseholdAgainRecordsNoChange() {
        stored.setHouseholdId(5L);
        stored.setHouseholdName("House 5");

        update().invoke(1L, "Abebe", null, null, null, null, null, true, 5L).orElseThrow();

        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_HOUSEHOLD_CHANGED), any(), any(), any());
    }
}
