package io.github.membertracker.usecase;

import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DeleteMemberUseCaseTest {

    @Test
    void deletesOnlyTheGivenId() {
        MemberRepository repo = mock(MemberRepository.class);

        new DeleteMemberUseCase(repo).invoke(42L);

        verify(repo).deleteById(42L);
        verifyNoMoreInteractions(repo);
    }
}
