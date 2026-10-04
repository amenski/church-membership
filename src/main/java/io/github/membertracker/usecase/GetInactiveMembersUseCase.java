package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.util.List;

public class GetInactiveMembersUseCase {

    private final MemberRepository memberRepository;

    public GetInactiveMembersUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Retrieves the listed members whose dues do not count: INACTIVE, DECEASED and TRANSFERRED.
     * Archived members are hidden.
     *
     * @return a list of all members who are not dues-paying and not archived
     */
    public List<Member> invoke() {
        return memberRepository.findAll().stream()
                .filter(member -> !member.getStatus().countsForDues())
                .toList();
    }
}