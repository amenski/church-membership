package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;
import java.util.List;

public class GetMembersWithoutRecentPaymentUseCase {

    private final MemberRepository memberRepository;

    public GetMembersWithoutRecentPaymentUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Retrieves members who haven't made a payment in a specified number of months.
     *
     * @param monthsBack the number of months to look back for payments
     * @return a list of members who haven't made a payment in the specified period
     */
    public List<Member> invoke(int monthsBack) {
        LocalDate cutoffDate = LocalDate.now().minusMonths(monthsBack);
        return memberRepository.findMembersWithLastPaymentBefore(cutoffDate);
    }
}