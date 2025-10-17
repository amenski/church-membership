package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.YearMonth;
import java.util.List;

public class UpdateMissingPaymentCountersUseCase {

    private final MemberRepository memberRepository;
    private final HasPaymentForMonthUseCase hasPaymentForMonthUseCase;

    public UpdateMissingPaymentCountersUseCase(MemberRepository memberRepository,
                                              HasPaymentForMonthUseCase hasPaymentForMonthUseCase) {
        this.memberRepository = memberRepository;
        this.hasPaymentForMonthUseCase = hasPaymentForMonthUseCase;
    }

    /**
     * Updates the consecutive months missed counter for all members.
     * Increments the counter for members who haven't made a payment for the previous month.
     */
    public void invoke() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        List<Member> allMembers = memberRepository.findAll();

        for (Member member : allMembers) {
            if (!hasPaymentForMonthUseCase.invoke(member, previousMonth)) {
                member.setConsecutiveMonthsMissed(member.getConsecutiveMonthsMissed() + 1);
                memberRepository.save(member);
            }
        }
    }
}