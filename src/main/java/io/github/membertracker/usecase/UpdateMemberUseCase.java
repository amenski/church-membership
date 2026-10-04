package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
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
     */
    public Optional<Member> invoke(Long id, String name, String email, String phone,
                                   LocalDate joinDate, MemberStatus status) {
        return invoke(id, name, email, phone, joinDate, status, false, null);
    }

    /**
     * Same, and also moves the member's person to another household.
     *
     * @param setHousehold false leaves the household as it is (the request did not mention it); true applies
     *                     {@code householdId}, where null removes the person from its household. An unknown id is
     *                     refused when the member is saved (HouseholdDomainException, 400)
     */
    public Optional<Member> invoke(Long id, String name, String email, String phone,
                                   LocalDate joinDate, MemberStatus status,
                                   boolean setHousehold, Long householdId) {
        if (status == MemberStatus.ARCHIVED) {
            throw MemberDomainException.statusNotAllowed("A member cannot be archived here.");
        }
        return memberRepository.findById(id).map(member -> {
            MemberStatus before = member.getStatus();
            Long householdBefore = member.getHouseholdId();
            String householdNameBefore = member.getHouseholdName();
            member.setName(name);
            member.setEmail(email);
            member.setPhone(phone);
            if (joinDate != null) {
                member.setJoinDate(joinDate);
            }
            if (setHousehold) {
                member.setHouseholdId(householdId);
            }
            MemberStatus target = status;
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
            if (!Objects.equals(householdBefore, saved.getHouseholdId())) {
                recordActivity.record(ActivityType.MEMBER_HOUSEHOLD_CHANGED,
                        householdChange(saved, householdBefore, householdNameBefore), "MEMBER", saved.getId());
            }
            return saved;
        });
    }

    private static String householdChange(Member saved, Long before, String nameBefore) {
        String who = "Member " + saved.getName();
        if (saved.getHouseholdId() == null) {
            return who + " was removed from household " + nameBefore;
        }
        return before == null
                ? who + " was added to household " + saved.getHouseholdName()
                : who + " was moved to household " + saved.getHouseholdName();
    }

    private static void changeStatus(Member member, MemberStatus target) {
        switch (target) {
            case MEMBER -> member.activate();
            case INACTIVE -> member.deactivate();
            default -> member.setStatus(target);
        }
    }
}
