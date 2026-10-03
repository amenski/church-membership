package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

public class UpdateMissingPaymentCountersUseCase {

    private final MemberRepository memberRepository;
    private final HasPaymentForMonthUseCase hasPaymentForMonthUseCase;
    private final Clock clock;

    public UpdateMissingPaymentCountersUseCase(MemberRepository memberRepository,
                                              HasPaymentForMonthUseCase hasPaymentForMonthUseCase) {
        this(memberRepository, hasPaymentForMonthUseCase, Clock.systemDefaultZone());
    }

    public UpdateMissingPaymentCountersUseCase(MemberRepository memberRepository,
                                              HasPaymentForMonthUseCase hasPaymentForMonthUseCase,
                                              Clock clock) {
        this.memberRepository = memberRepository;
        this.hasPaymentForMonthUseCase = hasPaymentForMonthUseCase;
        this.clock = clock;
    }

    /**
     * Raises the consecutive months missed counter of active members who have no payment for the
     * previous month. Each member is counted at most once per month, so re-running is safe.
     * Members who joined after the end of the previous month are skipped.
     */
    public void invoke() {
        YearMonth previousMonth = YearMonth.now(clock).minusMonths(1);
        List<Member> activeMembers = memberRepository.findByActive(true);

        for (Member member : activeMembers) {
            if (member.getJoinDate() != null && member.getJoinDate().isAfter(previousMonth.atEndOfMonth())) {
                continue;
            }
            if (hasPaymentForMonthUseCase.invoke(member, previousMonth)) {
                continue;
            }
            if (member.markMissedFor(previousMonth)) {
                memberRepository.save(member);
            }
        }
    }
}
