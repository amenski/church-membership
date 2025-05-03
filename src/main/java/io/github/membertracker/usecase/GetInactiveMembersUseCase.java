package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetInactiveMembersUseCase {

    private final MemberRepository memberRepository;

    public GetInactiveMembersUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Retrieves all inactive members from the database.
     *
     * @return a list of all inactive members
     */
    @Transactional(readOnly = true)
    public List<Member> invoke() {
        return memberRepository.findByActive(false);
    }
}