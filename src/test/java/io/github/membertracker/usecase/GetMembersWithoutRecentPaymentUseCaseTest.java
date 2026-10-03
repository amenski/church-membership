package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetMembersWithoutRecentPaymentUseCaseTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final GetMembersWithoutRecentPaymentUseCase useCase = new GetMembersWithoutRecentPaymentUseCase(memberRepository);

    @Test
    void cutoffIsTheGivenNumberOfMonthsBeforeToday() {
        List<Member> stale = List.of(new Member("a", "a@example.com", "+1234567890"));
        when(memberRepository.findMembersWithLastPaymentBefore(LocalDate.now().minusMonths(3))).thenReturn(stale);

        assertThat(useCase.invoke(3)).isSameAs(stale);
        assertThat(useCase.invoke(6)).isEmpty();
    }
}
