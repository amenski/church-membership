package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.HouseholdDomainException;
import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.domain.model.HouseholdSummary;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.HouseholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseholdUseCasesTest {

    private HouseholdRepository repo;
    private RecordActivityUseCase recordActivity;

    @BeforeEach
    void setUp() {
        repo = mock(HouseholdRepository.class);
        recordActivity = mock(RecordActivityUseCase.class);
        when(repo.save(any(Household.class))).thenAnswer(invocation -> {
            Household household = invocation.getArgument(0);
            if (household.getId() == null) {
                household.setId(7L);
            }
            return household;
        });
    }

    private Household stored() {
        Household household = new Household("Kebede family", "Via Roma 1", null, "Roma", "00100", "secret note");
        household.setId(7L);
        when(repo.findById(7L)).thenReturn(Optional.of(household));
        return household;
    }

    @Test
    void createSavesTheHouseholdAndRecordsItWithTheNameOnly() {
        HouseholdDetails created = new CreateHouseholdUseCase(repo, recordActivity)
            .invoke("Kebede family", "Via Roma 1", "Scala B", "Roma", "00100", "secret note");

        ArgumentCaptor<Household> saved = ArgumentCaptor.forClass(Household.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Kebede family");
        assertThat(saved.getValue().getAddressLine2()).isEqualTo("Scala B");
        assertThat(created.household().getId()).isEqualTo(7L);
        assertThat(created.members()).isEmpty();
        verify(recordActivity).record(ActivityType.HOUSEHOLD_CREATED, "Household Kebede family was created", "HOUSEHOLD", 7L);
    }

    @Test
    void updateReplacesEveryFieldAndReturnsTheMembers() {
        stored();
        Member member = new Member("Abebe", null, null);
        member.setId(1L);
        when(repo.findMembers(7L)).thenReturn(List.of(member));

        HouseholdDetails updated = new UpdateHouseholdUseCase(repo, recordActivity)
            .invoke(7L, "Tesfaye family", null, null, "Milano", null, null).orElseThrow();

        assertThat(updated.household().getName()).isEqualTo("Tesfaye family");
        assertThat(updated.household().getAddressLine1()).isNull();
        assertThat(updated.household().getCity()).isEqualTo("Milano");
        assertThat(updated.household().getNotes()).isNull();
        assertThat(updated.members()).containsExactly(member);
        verify(recordActivity).record(ActivityType.HOUSEHOLD_UPDATED, "Household Tesfaye family was updated", "HOUSEHOLD", 7L);
    }

    @Test
    void updateOfAnUnknownHouseholdIsEmptyAndRecordsNothing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(new UpdateHouseholdUseCase(repo, recordActivity).invoke(99L, "X", null, null, null, null, null)).isEmpty();

        verify(repo, never()).save(any());
        verify(recordActivity, never()).record(any(), any(), any(), any());
    }

    @Test
    void deleteRemovesAnEmptyHouseholdAndRecordsIt() {
        stored();
        when(repo.countPeople(7L)).thenReturn(0L);

        assertThat(new DeleteHouseholdUseCase(repo, recordActivity).invoke(7L)).isTrue();

        verify(repo).deleteById(7L);
        verify(recordActivity).record(ActivityType.HOUSEHOLD_DELETED, "Household Kebede family was deleted", "HOUSEHOLD", 7L);
    }

    @Test
    void deleteIsRefusedWithAConflictWhilePeopleAreAssigned() {
        stored();
        when(repo.countPeople(7L)).thenReturn(2L);

        assertThatThrownBy(() -> new DeleteHouseholdUseCase(repo, recordActivity).invoke(7L))
            .isInstanceOfSatisfying(HouseholdDomainException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo("HOUSEHOLD_002");
                assertThat(e.isConflict()).isTrue();
                assertThat(e.getMessage()).doesNotContain("Kebede");
            });

        verify(repo, never()).deleteById(anyLong());
        verify(recordActivity, never()).record(any(), any(), any(), any());
    }

    @Test
    void deleteOfAnUnknownHouseholdIsFalse() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(new DeleteHouseholdUseCase(repo, recordActivity).invoke(99L)).isFalse();

        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void getByIdReturnsTheHouseholdWithItsMembersOrEmpty() {
        Household household = stored();
        Member member = new Member("Abebe", null, null);
        when(repo.findMembers(7L)).thenReturn(List.of(member));
        when(repo.findById(8L)).thenReturn(Optional.empty());

        HouseholdDetails details = new GetHouseholdByIdUseCase(repo).invoke(7L).orElseThrow();

        assertThat(details.household()).isSameAs(household);
        assertThat(details.members()).containsExactly(member);
        assertThat(new GetHouseholdByIdUseCase(repo).invoke(8L)).isEmpty();
    }

    @Test
    void getAllReturnsTheSummariesFromTheRepository() {
        List<HouseholdSummary> summaries = List.of(new HouseholdSummary(7L, "Kebede family", "Roma", 3, 1, 4));
        when(repo.findAllSummaries()).thenReturn(summaries);

        assertThat(new GetAllHouseholdsUseCase(repo).invoke()).isEqualTo(summaries);
    }
}
