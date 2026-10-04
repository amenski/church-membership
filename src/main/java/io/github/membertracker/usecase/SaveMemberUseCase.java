package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;

/**
 * Creates a member: counters zero, join date today unless given. The status is MEMBER unless INACTIVE is
 * asked for; a new member cannot start as deceased, transferred or archived.
 */
public class SaveMemberUseCase {

    private final MemberRepository memberRepository;
    private final RecordActivityUseCase recordActivity;

    public SaveMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivity) {
        this.memberRepository = memberRepository;
        this.recordActivity = recordActivity;
    }

    public Member invoke(String name, String email, String phone, LocalDate joinDate, MemberStatus status) {
        return invoke(name, email, phone, joinDate, status, null);
    }

    /**
     * @param householdId the household to put the new member's person in, or null for none; an unknown id is refused
     *                    when the member is saved (HouseholdDomainException, 400)
     */
    public Member invoke(String name, String email, String phone, LocalDate joinDate, MemberStatus status,
                         Long householdId) {
        if (status == MemberStatus.DECEASED || status == MemberStatus.TRANSFERRED || status == MemberStatus.ARCHIVED) {
            throw MemberDomainException.statusNotAllowed("A new member can only be MEMBER or INACTIVE.");
        }
        Member member = new Member(name, email, phone);
        if (joinDate != null) {
            member.setJoinDate(joinDate);
        }
        if (status != null) {
            member.setStatus(status);
        }
        member.setHouseholdId(householdId);
        Member saved = memberRepository.save(member);
        recordActivity.record(ActivityType.MEMBER_CREATED, "Member " + saved.getName() + " was added",
                "MEMBER", saved.getId());
        if (saved.getHouseholdId() != null) {
            recordActivity.record(ActivityType.MEMBER_HOUSEHOLD_CHANGED,
                    "Member " + saved.getName() + " was added to household " + saved.getHouseholdName(),
                    "MEMBER", saved.getId());
        }
        return saved;
    }
}
