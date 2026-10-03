package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;

/** Creates a member: active, counters zero, join date today unless given. */
public class SaveMemberUseCase {

    private final MemberRepository memberRepository;

    public SaveMemberUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member invoke(String name, String email, String phone, LocalDate joinDate) {
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw MemberDomainException.emailAlreadyExists(email);
        }
        Member member = new Member(name, email, phone);
        if (joinDate != null) {
            member.setJoinDate(joinDate);
        }
        return memberRepository.save(member);
    }
}
