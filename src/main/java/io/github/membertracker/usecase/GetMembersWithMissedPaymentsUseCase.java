package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.util.List;

public class GetMembersWithMissedPaymentsUseCase {

    private final MemberRepository memberRepository;

    public GetMembersWithMissedPaymentsUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Retrieves the dues-paying members who have missed payments for a specified number of consecutive months.
     *
     * @param monthsThreshold the minimum number of consecutive months missed to include a member
     * @return dues-paying members at least that many months behind, the one furthest behind first
     */
    public List<Member> invoke(int monthsThreshold) {
        return memberRepository.findDuesPayingWithMissedAtLeastOrderByMissedDesc(monthsThreshold);
    }
}