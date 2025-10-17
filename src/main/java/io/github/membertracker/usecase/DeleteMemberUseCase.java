package io.github.membertracker.usecase;

import io.github.membertracker.domain.repository.MemberRepository;

public class DeleteMemberUseCase {

    private final MemberRepository memberRepository;

    public DeleteMemberUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Deletes a member from the database by their ID.
     *
     * @param id the ID of the member to delete
     */
    public void invoke(Long id) {
        memberRepository.deleteById(id);
    }
}