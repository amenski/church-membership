package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
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
        assertThat(useCase.invoke(42L, "N", "n@example.com", null, null, null)).isEmpty();
        verify(memberRepository, never()).save(any());
        verifyNoInteractions(recordActivity);
    }

    @Test
    void ownEmailUnchangedIsAllowed() {
        when(memberRepository.findByEmailIgnoreCase("OLD@example.com")).thenReturn(Optional.of(stored));

        Member result = useCase.invoke(1L, "New", "OLD@example.com", "+390622222222", null, null).orElseThrow();

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getEmail()).isEqualTo("OLD@example.com");
        assertThat(result.getPhone()).isEqualTo("+390622222222");
        verify(memberRepository).save(stored);
    }

    @Test
    void anotherMembersEmailIsRejectedAndNothingIsSaved() {
        Member other = new Member("Other", "other@example.com", null);
        other.setId(2L);
        when(memberRepository.findByEmailIgnoreCase("OTHER@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.invoke(1L, "New", "OTHER@example.com", null, null, null))
            .isInstanceOf(MemberDomainException.class)
            .hasMessage("A member with this email already exists");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void systemManagedFieldsArePreserved() {
        Member result = useCase.invoke(1L, "New", "new@example.com", null, null, null).orElseThrow();

        assertThat(result.getConsecutiveMonthsMissed()).isEqualTo(4);
        assertThat(result.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(result.getLastMissedCountMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(result.getPhone()).isNull();
    }

    @Test
    void reactivatingGoesThroughTheDomainAndResetsTheCounter() {
        stored.setActive(false);

        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, true).orElseThrow();

        assertThat(result.isActive()).isTrue();
        assertThat(result.getConsecutiveMonthsMissed()).isZero();
        assertThat(result.getLastPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    void deactivatingAnActiveMember() {
        Member result = useCase.invoke(1L, "Old", "old@example.com", null, null, false).orElseThrow();

        assertThat(result.isActive()).isFalse();
        assertThat(result.getConsecutiveMonthsMissed()).isEqualTo(4);
    }

    @Test
    void anEditRecordsOneUpdateEntryAndNoStatusEntry() {
        useCase.invoke(1L, "New", "new@example.com", null, null, null);

        verify(recordActivity).record(ActivityType.MEMBER_UPDATED, "Member New was updated", "MEMBER", 1L);
        verifyNoMoreInteractions(recordActivity);
    }

    @Test
    void deactivatingAlsoRecordsTheStatusChange() {
        useCase.invoke(1L, "Old", "old@example.com", null, null, false);

        verify(recordActivity).record(ActivityType.MEMBER_UPDATED, "Member Old was updated", "MEMBER", 1L);
        verify(recordActivity).record(ActivityType.MEMBER_DEACTIVATED, "Member Old was deactivated", "MEMBER", 1L);
    }

    @Test
    void reactivatingAlsoRecordsTheStatusChange() {
        stored.setActive(false);

        useCase.invoke(1L, "Old", "old@example.com", null, null, true);

        verify(recordActivity).record(ActivityType.MEMBER_ACTIVATED, "Member Old was activated", "MEMBER", 1L);
    }

    @Test
    void anUnchangedStatusRecordsNoStatusEntry() {
        useCase.invoke(1L, "Old", "old@example.com", null, null, true);

        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_ACTIVATED), any(), any(), any());
        verify(recordActivity, never()).record(eq(ActivityType.MEMBER_DEACTIVATED), any(), any(), any());
    }

    @Test
    void sameOrAbsentActiveStateDoesNotThrowOrChangeAnything() {
        assertThatCode(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, true)).doesNotThrowAnyException();
        assertThat(stored.getConsecutiveMonthsMissed()).isEqualTo(4);

        stored.setActive(false);
        assertThatCode(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, false)).doesNotThrowAnyException();
        assertThatCode(() -> useCase.invoke(1L, "Old", "old@example.com", null, null, null)).doesNotThrowAnyException();
        assertThat(stored.isActive()).isFalse();
    }

    @Test
    void joinDateChangesOnlyWhenGiven() {
        useCase.invoke(1L, "Old", "old@example.com", null, null, null);
        assertThat(stored.getJoinDate()).isEqualTo(JOINED);

        useCase.invoke(1L, "Old", "old@example.com", null, LocalDate.of(2025, 2, 2), null);
        assertThat(stored.getJoinDate()).isEqualTo(LocalDate.of(2025, 2, 2));
    }
}
