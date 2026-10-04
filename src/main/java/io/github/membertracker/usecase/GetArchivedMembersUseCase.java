package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.util.List;

public class GetArchivedMembersUseCase {

    private final MemberRepository memberRepository;

    public GetArchivedMembersUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** Only the archived members, which every other list hides. */
    public List<Member> invoke() {
        return memberRepository.findByStatus(MemberStatus.ARCHIVED);
    }
}
