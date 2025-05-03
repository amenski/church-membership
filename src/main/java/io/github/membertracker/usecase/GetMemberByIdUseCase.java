package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GetMemberByIdUseCase {

    private final MemberRepository memberRepository;

    public GetMemberByIdUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Retrieves a member by their ID.
     *
     * @param id the ID of the member to retrieve
     * @return an Optional containing the member if found, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Member> invoke(Long id) {
        return memberRepository.findById(id);
    }
}