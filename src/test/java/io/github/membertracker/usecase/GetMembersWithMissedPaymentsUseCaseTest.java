package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetMembersWithMissedPaymentsUseCaseTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);

    @Test
    void usesTheGivenThresholdAndReturnsTheActiveMembersOfTheRepository() {
        List<Member> late = List.of(new Member("a", "a@example.com", "+1234567890"));
        when(memberRepository.findActiveWithMissedAtLeastOrderByMissedDesc(2)).thenReturn(late);

        assertThat(new GetMembersWithMissedPaymentsUseCase(memberRepository).invoke(2)).isSameAs(late);
        assertThat(new GetMembersWithMissedPaymentsUseCase(memberRepository).invoke(3)).isEmpty();
    }
}
