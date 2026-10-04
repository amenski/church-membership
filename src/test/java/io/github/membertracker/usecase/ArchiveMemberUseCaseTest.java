package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArchiveMemberUseCaseTest {

    private final MemberRepository repo = mock(MemberRepository.class);
    private final RecordActivityUseCase recordActivity = mock(RecordActivityUseCase.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T10:15:00Z"), ZoneOffset.UTC);
    private final ArchiveMemberUseCase useCase = new ArchiveMemberUseCase(repo, recordActivity, clock);

    private Member stored(String name) {
        Member member = new Member(name, "jane@example.com", "+390612345678");
        member.setId(42L);
        member.setConsecutiveMonthsMissed(3);
        when(repo.findById(42L)).thenReturn(Optional.of(member));
        when(repo.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
        return member;
    }

    @Test
    void savesTheMemberAsArchivedWithTheTimeAndNeverDeletes() {
        stored("Jane Smith");

        Optional<Member> result = useCase.invoke(42L);

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MemberStatus.ARCHIVED);
        assertThat(saved.getValue().getArchivedAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 10, 15));
        assertThat(result).containsSame(saved.getValue());
        verify(repo, never()).deleteById(anyLong());
        verify(recordActivity).record(ActivityType.MEMBER_ARCHIVED, "Member Jane Smith was archived", "MEMBER", 42L);
    }

    @Test
    void theMonthsBehindCounterIsLeftAlone() {
        stored("Jane Smith");

        assertThat(useCase.invoke(42L).orElseThrow().getConsecutiveMonthsMissed()).isEqualTo(3);
    }

    @Test
    void anUnknownIdIsEmptyAndTouchesNothing() {
        when(repo.findById(42L)).thenReturn(Optional.empty());

        assertThat(useCase.invoke(42L)).isEmpty();

        verify(repo, never()).save(any());
        verify(repo, never()).deleteById(anyLong());
        verifyNoInteractions(recordActivity);
    }

    @Test
    void archivingAnArchivedMemberChangesAndRecordsNothing() {
        Member member = stored("Jane Smith");
        member.archive(LocalDateTime.of(2026, 1, 1, 0, 0));

        useCase.invoke(42L);

        assertThat(member.getArchivedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        verify(repo, never()).save(any());
        verifyNoInteractions(recordActivity);
    }
}
