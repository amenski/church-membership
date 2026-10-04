package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class UpdateMemberUseCaseTest {

    private static final LocalDate JOINED = LocalDate.of(2024, 3, 1);

    private MemberRepository memberRepository;
    private RecordActivityUseCase recordActivity;
    private UpdateMemberUseCase useCase;
    private Member stored;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        recordActivity = mock(RecordActivityUseCase.class);
        useCase = new UpdateMemberUseCase(memberRepository, recordActivity);
        stored = new Member("Old", "old@example.com", "+390611111111");
        stored.setId(1L);
        stored.setJoinDate(JOINED);
        stored.setConsecutiveMonthsMissed(4);
        stored.setLastPaymentDate(LocalDate.of(2026, 5, 10));
        stored.setLastMissedCountMonth(YearMonth.of(2026, 9));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(memberRepository.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void unknownIdReturnsEmptyAndSavesNothing() {
        assertThat(useCase.invoke(42L, "N", "n@example.com", null, null, null, null)).isEmpty();
        verify(memberRepository, never()).save(any());
        verifyNoInteractions(recordActivity);
    }

    @Test
    void anEmailAnotherMemberHasIsAllowed() {
        Member result = useCase.invoke(1L, "New", "OTHER@example.com", "+390622222222", null, null, null).orElseThrow();

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getEmail()).isEqualTo("OTHER@example.com");
        assertThat(result.getPhone()).isEqualTo("+390622222222");
        verify(memberRepository).save(stored);
    }

    @Test
    void theEmailCanBeRemoved() {
        Member result = useCase.invoke(1L, "Old", null, null, null, null, null).orElseThrow();

        assertThat(result.getEmail()).isNull();
        verify(memberRepository).save(stored);
    }

    @Test
    void systemManagedFieldsArePreserved() {
        Member result = useCase.invoke(1L, "New", "new@example.com", null, null, null, null).orElseThrow();

        assertThat(result.getConsecutiveMonthsMissed()).isEqualTo(4);
        assertThat(result.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(result.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(result.getPhone()).isNull();
    }

    @Test
    void reactivatingGoesThroughTheDomainAndResetsTheCounter() {
        stored.setStatus(MemberStatus.INACTIVE);

        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, null, true).orElseThrow();

        assertThat(result.isActive()).isTrue();
        assertThat(result.getConsecutiveMonthsMissed()).isZero();
        assertThat(result.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    void deactivatingAnActiveMember() {
        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, null, false).orElseThrow();

        assertThat(result.isActive()).isFalse();
        assertThat(result.getConsecutiveMonthsMissed()).isEqualTo(4);
    }

    @Test
    void anEditRecordsOneUpdateEntryAndNoStatusEntry() {
        useCase.invoke(1L, "New", "new@example.com", null, null, null, null);

        verify(recordActivity).record(ActivityType.MEMBER_UPDATED, "Member New was updated", "MEMBER", 1L);
        verifyNoMoreInteractions(recordActivity);
    }

    @Test
    void deactivatingRecordsOnlyTheTypedEntry() {
        useCase.invoke(1L, "Old", "old@example.com", null, null, null, false);

        verify(recordActivity).record(ActivityType.MEMBER_DEACTIVATED, "Member Old was deactivated", "MEMBER", 1L);
        verifyNoMoreInteractions(recordActivity);
    }

    @Test
    void reactivatingAlsoRecordsTheStatusChange() {
        stored.setStatus(MemberStatus.INACTIVE);

        useCase.invoke(1L, "Old", "old@example.com", null, null, null, true);

        verify(recordActivity).record(ActivityType.MEMBER_ACTIVATED, "Member Old was activated", "MEMBER", 1L);
    }

    @Test
    void anUnchangedStatusRecordsNoStatusEntry() {
        useCase.invoke(1L, "Old", "old@example.com", null, null, null, true);

        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_ACTIVATED), any(), any(), any());
        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_DEACTIVATED), any(), any(), any());
    }

    @Test
    void sameOrAbsentActiveStateDoesNotThrowOrChangeAnything() {
        assertThatCode(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, null, true)).doesNotThrowAnyException();
        assertThat(stored.getConsecutiveMonthsMissed()).isEqualTo(4);

        stored.setStatus(MemberStatus.INACTIVE);
        assertThatCode(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, null, false)).doesNotThrowAnyException();
        assertThatCode(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, null, null)).doesNotThrowAnyException();
        assertThat(stored.isActive()).isFalse();
    }

    @Test
    void joinDateChangesOnlyWhenGiven() {
        useCase.invoke(1L, "Old", "old@example.com", null, null, null, null);
        assertThat(stored.getJoinDate()).isEqualTo(JOINED);

        useCase.invoke(1L, "Old", "old@example.com", null, LocalDate.of(2025, 2, 2), null, null);
        assertThat(stored.getJoinDate()).isEqualTo(LocalDate.of(2025, 2, 2));
    }

    @Test
    void aStatusInTheRequestWinsOverActive() {
        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, MemberStatus.TRANSFERRED, true).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(MemberStatus.TRANSFERRED);
        assertThat(result.isActive()).isFalse();
        assertThat(result.getConsecutiveMonthsMissed()).isEqualTo(4);
    }

    @Test
    void markingAMemberDeceasedFreezesTheCounterAndRecordsDeactivationAndTheNewStatus() {
        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, MemberStatus.DECEASED, null).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(MemberStatus.DECEASED);
        assertThat(result.getConsecutiveMonthsMissed()).isEqualTo(4);
        verify(recordActivity).record(ActivityType.MEMBER_DEACTIVATED, "Member Old was deactivated, status is now deceased", "MEMBER", 1L);
        verifyNoMoreInteractions(recordActivity);
    }

    @Test
    void reactivatingFromAnyOtherStatusResetsTheCounter() {
        for (MemberStatus from : new MemberStatus[] {MemberStatus.INACTIVE, MemberStatus.DECEASED, MemberStatus.TRANSFERRED}) {
            stored.setStatus(from);
            stored.setConsecutiveMonthsMissed(4);

            Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, MemberStatus.MEMBER, null).orElseThrow();

            assertThat(result.getStatus()).as("from %s", from).isEqualTo(MemberStatus.MEMBER);
            assertThat(result.getConsecutiveMonthsMissed()).as("from %s", from).isZero();
        }
        verify(recordActivity, org.mockito.Mockito.times(3))
            .record(eq(ActivityType.MEMBER_ACTIVATED), eq("Member Old was activated"), eq("MEMBER"), eq(1L));
    }

    @Test
    void movingBetweenTwoStatusesThatDoNotCountForDuesWritesNoActivationEntry() {
        stored.setStatus(MemberStatus.INACTIVE);

        useCase.invoke(1L, "Old", "old@example.com", null, null, MemberStatus.TRANSFERRED, null);

        verify(recordActivity).record(ActivityType.MEMBER_UPDATED, "Member Old was updated, status is now transferred", "MEMBER", 1L);
        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_ACTIVATED), any(), any(), any());
        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_DEACTIVATED), any(), any(), any());
    }

    @Test
    void anOldClientSendingActiveFalseDoesNotTurnADeceasedPersonIntoInactive() {
        stored.setStatus(MemberStatus.DECEASED);

        Member result = useCase.invoke(1L, "New name", "old@example.com", null, null, null, false).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(MemberStatus.DECEASED);
        assertThat(result.getName()).isEqualTo("New name");
    }

    @Test
    void anOldClientSendingActiveTrueReactivatesAnyNonMember() {
        stored.setStatus(MemberStatus.TRANSFERRED);

        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, null, true).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(MemberStatus.MEMBER);
        assertThat(result.getConsecutiveMonthsMissed()).isZero();
    }

    @Test
    void archivedIsRefusedWithAFieldErrorAndNothingIsSaved() {
        assertThatThrownBy(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, MemberStatus.ARCHIVED, null))
            .isInstanceOf(MemberDomainException.class)
            .hasMessage("A member cannot be archived here.")
            .satisfies(e -> {
                MemberDomainException ex = (MemberDomainException) e;
                assertThat(ex.getField()).isEqualTo("status");
                assertThat(ex.getErrorCode()).isEqualTo(MemberDomainException.STATUS_NOT_ALLOWED);
            });

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(recordActivity);
        assertThat(stored.getStatus()).isEqualTo(MemberStatus.MEMBER);
    }

    @Test
    void anArchivedMemberCanBeRestoredToMemberWhichClearsTheArchiveTimeAndResetsTheCounter() {
        stored.archive(java.time.LocalDateTime.of(2026, 9, 1, 12, 0));

        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, MemberStatus.MEMBER, null).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(MemberStatus.MEMBER);
        assertThat(result.getArchivedAt()).isNull();
        assertThat(result.getConsecutiveMonthsMissed()).isZero();
    }

    @Test
    void anArchivedMemberCanBeRestoredToInactiveKeepingTheCounter() {
        stored.archive(java.time.LocalDateTime.of(2026, 9, 1, 12, 0));

        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, MemberStatus.INACTIVE, null).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(result.getArchivedAt()).isNull();
        assertThat(result.getConsecutiveMonthsMissed()).isEqualTo(4);
    }

    @Test
    void anArchivedMemberCannotJumpToDeceasedOrTransferred() {
        stored.archive(java.time.LocalDateTime.of(2026, 9, 1, 12, 0));

        for (MemberStatus target : new MemberStatus[] {MemberStatus.DECEASED, MemberStatus.TRANSFERRED}) {
            assertThatThrownBy(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, target, null))
                .isInstanceOf(MemberDomainException.class)
                .extracting("errorCode").isEqualTo(MemberDomainException.STATUS_NOT_ALLOWED);
        }
        verify(memberRepository, never()).save(any());
        assertThat(stored.getStatus()).isEqualTo(MemberStatus.ARCHIVED);
    }
}
