package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberListUseCasesTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);

    private static Member member(String name, MemberStatus status) {
        Member m = new Member(name, null, null);
        m.setStatus(status);
        return m;
    }

    @Test
    void activeMembersAreTheDuesPayingOnes() {
        List<Member> paying = List.of(member("a", MemberStatus.MEMBER));
        when(memberRepository.findDuesPaying()).thenReturn(paying);

        assertThat(new GetActiveMembersUseCase(memberRepository).invoke()).isSameAs(paying);
    }

    @Test
    void inactiveMembersAreEveryListedMemberWhoseDuesDoNotCount() {
        Member here = member("here", MemberStatus.MEMBER);
        Member away = member("away", MemberStatus.INACTIVE);
        Member gone = member("gone", MemberStatus.DECEASED);
        Member moved = member("moved", MemberStatus.TRANSFERRED);
        when(memberRepository.findAll()).thenReturn(List.of(here, away, gone, moved));

        assertThat(new GetInactiveMembersUseCase(memberRepository).invoke()).containsExactly(away, gone, moved);
    }

    @Test
    void archivedMembersComeFromTheStatusQuery() {
        List<Member> archived = List.of(member("old", MemberStatus.ARCHIVED));
        when(memberRepository.findByStatus(MemberStatus.ARCHIVED)).thenReturn(archived);

        assertThat(new GetArchivedMembersUseCase(memberRepository).invoke()).isSameAs(archived);
    }
}
