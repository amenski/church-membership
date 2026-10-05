package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.PageResult;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.model.PaymentPageQuery;
import io.github.membertracker.domain.model.PaymentSummary;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentRepository {
    List<Payment> findAll();
    
    Optional<Payment> findById(Long id);
    
    List<Payment> findByMember(Member member);
    
    boolean existsByMemberAndPeriod(Member member, YearMonth period);
    
    /** Sum of the amounts whose billing period is {@code period}; 0.0 when there are none. */
    double sumAmountByPeriod(YearMonth period);

    /** Newest payment date first, then newest id. */
    List<Payment> findRecent(int limit);

    /** One filtered, sorted page (see {@link PaymentPageQuery}): a count statement and a page statement. */
    PageResult<Payment> findPage(PaymentPageQuery query);

    /**
     * The distinct billing months paid from {@code from} to {@code to}, both inclusive, per member id, oldest first;
     * members with none are absent. One grouped statement.
     */
    Map<Long, List<YearMonth>> findPaidMonthsBetween(YearMonth from, YearMonth to);

    /** What was paid for {@code currentMonth} (by billing period), what was ever paid and the count: one aggregate statement. */
    PaymentSummary summarize(YearMonth currentMonth);

    /** How many payments this member has, whatever their period. */
    long countByMemberId(Long memberId);

    Payment save(Payment payment);
}