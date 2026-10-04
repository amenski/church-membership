package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;

/** Creates a member: active, counters zero, join date today unless given. */
public class SaveMemberUseCase {

    private final MemberRepository memberRepository;
    private final RecordActivityUseCase recordActivity;

    public SaveMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivity) {
        this.memberRepository = memberRepository;
        this.recordActivity = recordActivity;
    }

    public Member invoke(String name, String email, String phone, LocalDate joinDate) {
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw MemberDomainException.emailAlreadyExists(email);
        }
        Member member = new Member(name, email, phone);
        if (joinDate != null) {
            member.setJoinDate(joinDate);
        }
        Member saved = memberRepository.save(member);
        recordActivity.record(ActivityType.MEMBER_CREATED, "Member " + saved.getName() + " was added",
                "MEMBER", saved.getId());
        return saved;
    }
}
