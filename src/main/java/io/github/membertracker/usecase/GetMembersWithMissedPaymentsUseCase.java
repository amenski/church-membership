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
     * Retrieves members who have missed payments for a specified number of consecutive months.
     *
     * @param monthsThreshold the minimum number of consecutive months missed to include a member
     * @return a list of members who have missed payments for at least the specified number of months
     */
    public List<Member> invoke(int monthsThreshold) {
        return memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(monthsThreshold);
    }
}