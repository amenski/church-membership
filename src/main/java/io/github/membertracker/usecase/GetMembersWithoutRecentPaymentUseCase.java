package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
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
    @Transactional(readOnly = true)
    public List<Member> invoke(int monthsBack) {
        LocalDate cutoffDate = LocalDate.now().minusMonths(monthsBack);
        return memberRepository.findMembersWithLastPaymentBefore(cutoffDate);
    }
}