package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteMemberUseCaseTest {

    private final MemberRepository repo = mock(MemberRepository.class);
    private final RecordActivityUseCase recordActivity = mock(RecordActivityUseCase.class);
    private final DeleteMemberUseCase useCase = new DeleteMemberUseCase(repo, recordActivity);

    @Test
    void deletesTheGivenIdAndRecordsTheNameThatIsGoneAfterwards() {
        when(repo.findById(42L)).thenReturn(Optional.of(new Member("Jane Smith", "jane@example.com", "+390612345678")));

        useCase.invoke(42L);

        InOrder order = inOrder(repo, recordActivity);
        order.verify(repo).findById(42L);
        order.verify(repo).deleteById(42L);
        order.verify(recordActivity).record(ActivityType.MEMBER_DELETED, "Member Jane Smith was deleted", "MEMBER", 42L);
    }

    @Test
    void anUnknownIdIsDeletedAsBeforeAndRecordsNothing() {
        when(repo.findById(42L)).thenReturn(Optional.empty());

        useCase.invoke(42L);

        verify(repo).deleteById(42L);
        verifyNoInteractions(recordActivity);
    }
}
