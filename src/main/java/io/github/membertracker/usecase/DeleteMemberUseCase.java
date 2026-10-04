package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.repository.MemberRepository;

public class DeleteMemberUseCase {

    private final MemberRepository memberRepository;
    private final RecordActivityUseCase recordActivity;

    public DeleteMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivity) {
        this.memberRepository = memberRepository;
        this.recordActivity = recordActivity;
    }

    /**
     * Deletes a member from the database by their ID. The audit entry keeps the name, which is gone afterwards.
     *
     * @param id the ID of the member to delete
     */
    public void invoke(Long id) {
        String name = memberRepository.findById(id).map(member -> member.getName()).orElse(null);
        memberRepository.deleteById(id);
        if (name != null) {
            recordActivity.record(ActivityType.MEMBER_DELETED, "Member " + name + " was deleted", "MEMBER", id);
        }
    }
}
