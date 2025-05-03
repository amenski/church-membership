package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetActiveMembersUseCase {

    private final MemberRepository memberRepository;

    public GetActiveMembersUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Retrieves all active members from the database.
     *
     * @return a list of all active members
     */
    @Transactional(readOnly = true)
    public List<Member> invoke() {
        return memberRepository.findByActive(true);
    }
}