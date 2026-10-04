package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The dues of the member whose email is the signed-in user's email. Only that one member's data is returned.
 * Never guesses: no match, or more than one (a shared family address), is "nothing found".
 */
public class GetMyDuesUseCase {

    static final int MONTHS = 12;
    static final int RECENT_PAYMENTS = 12;

    /** One receipt row: {@code receiptNumber} is "R-" and the payment id padded to six digits, as on the payments screen. */
    public record MyPayment(String receiptNumber, String period, LocalDate paymentDate, String method, double amount) {
    }

    public record MyDues(Long memberId, String name, MemberStatus status, LocalDate joinDate, int monthsBehind,
                         LocalDate lastPaymentDate, String householdName, String email, String phone,
                         List<String> paidMonths, List<MyPayment> payments) {
    }

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;

    public GetMyDuesUseCase(MemberRepository memberRepository, PaymentRepository paymentRepository) {
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
    }

    public Optional<MyDues> invoke(String signedInEmail) {
        List<Member> matches = memberRepository.findNotArchivedByEmail(signedInEmail);
        if (matches.size() != 1) {
            return Optional.empty();
        }
        Member member = matches.get(0);
        List<Payment> payments = paymentRepository.findByMember(member);

        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(MONTHS - 1);
        List<String> paidMonths = payments.stream()
                .map(Payment::getPeriod)
                .filter(period -> period != null && !period.isBefore(first) && !period.isAfter(current))
                .distinct()
                .sorted()
                .map(YearMonth::toString)
                .toList();

        List<MyPayment> recent = payments.stream()
                .sorted(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Payment::getId, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed())
                .limit(RECENT_PAYMENTS)
                .map(p -> new MyPayment(String.format("R-%06d", p.getId()),
                        p.getPeriod() == null ? null : p.getPeriod().toString(),
                        p.getPaymentDate(), p.getPaymentMethod().name(), p.getAmount()))
                .toList();

        return Optional.of(new MyDues(member.getId(), member.getName(), member.getStatus(), member.getJoinDate(),
                member.getConsecutiveMonthsMissed(), member.getLastPaymentDate(), member.getHouseholdName(),
                member.getEmail(), member.getPhone(), paidMonths, recent));
    }
}
