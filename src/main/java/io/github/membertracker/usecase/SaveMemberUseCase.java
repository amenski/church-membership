package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaveMemberUseCase {

    private final MemberRepository memberRepository;

    public SaveMemberUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Saves a member to the database.
     *
     * @param member the member to save
     * @return the saved member
     */
    @Transactional
    public Member invoke(Member member) {
        return memberRepository.save(member);
    }
}