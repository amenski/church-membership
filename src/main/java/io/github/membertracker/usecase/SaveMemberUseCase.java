package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;

public class SaveMemberUseCase {

    private final MemberRepository memberRepository;

    public SaveMemberUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member invoke(Member member) {
        // Set default values for new members
        if (member.getId() == null) {
            if (member.getJoinDate() == null) {
                member.setJoinDate(LocalDate.now());
            }
            // Ensure active is set to true for new members if not explicitly set
            if (!member.isActive()) {
                member.setActive(true);
            }
        }

        return memberRepository.save(member);
    }
}