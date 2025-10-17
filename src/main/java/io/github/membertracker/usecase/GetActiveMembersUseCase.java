package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.util.List;

public class GetActiveMembersUseCase {

    private final MemberRepository memberRepository;

    public GetActiveMembersUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Retrieves all active members from the database.
     *
     * @return a list of all active members
     */
    public List<Member> invoke() {
        return memberRepository.findByActive(true);
    }
}