package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;
import java.util.Locale;
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

    /**
     * @param status the new status, or null to leave it; ARCHIVED is refused as a target (archiving is
     *               {@link ArchiveMemberUseCase}). An ARCHIVED member may be moved back to MEMBER or INACTIVE, which
     *               clears {@code archivedAt}: this is the restore
     * @param active legacy on/off form, used only when {@code status} is null: true makes a member of anyone
     *               who is not one, false makes INACTIVE of a member; it never changes a deceased or
     *               transferred person who is already off
     */
    public Optional<Member> invoke(Long id, String name, String email, String phone,
                                   LocalDate joinDate, MemberStatus status, Boolean active) {
        if (status == MemberStatus.ARCHIVED) {
            throw MemberDomainException.statusNotAllowed("A member cannot be archived here.");
        }
        return memberRepository.findById(id).map(member -> {
            MemberStatus before = member.getStatus();
            member.setName(name);
            member.setEmail(email);
            member.setPhone(phone);
            if (joinDate != null) {
                member.setJoinDate(joinDate);
            }
            MemberStatus target = status != null ? status : fromActive(member, active);
            if (before == MemberStatus.ARCHIVED && (target == MemberStatus.DECEASED || target == MemberStatus.TRANSFERRED)) {
                throw MemberDomainException.statusNotAllowed("An archived member can only be restored to MEMBER or INACTIVE.");
            }
            if (target != null && target != before) {
                changeStatus(member, target);
            }
            Member saved = memberRepository.save(member);
            MemberStatus after = saved.getStatus();
            String statusNote = after != before ? ", status is now " + after.name().toLowerCase(Locale.ROOT) : "";
            if (before.countsForDues() != after.countsForDues()) {
                ActivityType type = after.countsForDues() ? ActivityType.MEMBER_ACTIVATED : ActivityType.MEMBER_DEACTIVATED;
                String what = after.countsForDues() ? "activated" : "deactivated";
                // The typed entry already says it; only a status beyond plain member / inactive needs naming
                String note = after == MemberStatus.MEMBER || after == MemberStatus.INACTIVE ? "" : statusNote;
                recordActivity.record(type, "Member " + saved.getName() + " was " + what + note, "MEMBER", saved.getId());
            } else {
                recordActivity.record(ActivityType.MEMBER_UPDATED,
                        "Member " + saved.getName() + " was updated" + statusNote, "MEMBER", saved.getId());
            }
            return saved;
        });
    }

    private static MemberStatus fromActive(Member member, Boolean active) {
        if (active == null) {
            return null;
        }
        if (active && !member.isActive()) {
            return MemberStatus.MEMBER;
        }
        if (!active && member.isActive()) {
            return MemberStatus.INACTIVE;
        }
        return null;
    }

    private static void changeStatus(Member member, MemberStatus target) {
        switch (target) {
            case MEMBER -> member.activate();
            case INACTIVE -> member.deactivate();
            default -> member.setStatus(target);
        }
    }
}
