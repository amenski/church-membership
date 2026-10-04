package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Edits the client-managed fields of a stored member. The missed-month counters and the last
 * payment date are system-managed and never touched here.
 */
public class UpdateMemberUseCase {

    private final MemberRepository memberRepository;
    private final RecordActivityUseCase recordActivity;

    public UpdateMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivity) {
        this.memberRepository = memberRepository;
        this.recordActivity = recordActivity;
    }

    public Optional<Member> invoke(Long id, String name, String email, String phone,
                                   LocalDate joinDate, Boolean active) {
        return memberRepository.findById(id).map(member -> {
            ActivityType statusChange = null;
            member.setName(name);
            member.setEmail(email);
            member.setPhone(phone);
            if (joinDate != null) {
                member.setJoinDate(joinDate);
            }
            if (active != null) {
                if (active && !member.isActive()) {
                    member.activate();
                    statusChange = ActivityType.MEMBER_ACTIVATED;
                } else if (!active && member.isActive()) {
                    member.deactivate();
                    statusChange = ActivityType.MEMBER_DEACTIVATED;
                }
            }
            Member saved = memberRepository.save(member);
            recordActivity.record(ActivityType.MEMBER_UPDATED, "Member " + saved.getName() + " was updated",
                    "MEMBER", saved.getId());
            if (statusChange != null) {
                String what = statusChange == ActivityType.MEMBER_ACTIVATED ? "activated" : "deactivated";
                recordActivity.record(statusChange, "Member " + saved.getName() + " was " + what,
                        "MEMBER", saved.getId());
            }
            return saved;
        });
    }
}
