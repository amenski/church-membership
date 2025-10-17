package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.util.List;

public class GetAllMembersUseCase {

    private final MemberRepository memberRepository;

    public GetAllMembersUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> invoke() {
        return memberRepository.findAll();
    }
}