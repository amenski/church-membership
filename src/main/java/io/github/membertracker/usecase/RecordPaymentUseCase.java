package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

public class RecordPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final RecordActivityUseCase recordActivity;

    public RecordPaymentUseCase(PaymentRepository paymentRepository, MemberRepository memberRepository,
                                RecordActivityUseCase recordActivity) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.recordActivity = recordActivity;
    }

    /**
     * @param period      the month the payment covers; null means the current month
     * @param paymentDate the day it was paid; null means today (a back-dated entry passes the real day)
     */
    public Payment invoke(Long memberId, Double amount, PaymentMethod paymentMethod,
                          YearMonth period, LocalDate paymentDate, String notes) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> MemberDomainException.memberNotFound(memberId));
        if (!member.getStatus().countsForDues()) {
            throw MemberDomainException.memberInactive(member.getName());
        }

        Payment payment = new Payment(member, period != null ? period : YearMonth.now(), amount, paymentMethod);
        if (paymentDate != null) {
            payment.setPaymentDate(paymentDate);
        }
        payment.setNotes(notes);

        payment.validateAmount();
        payment.validatePeriod();
        payment.validatePaymentDate();

        if (paymentRepository.existsByMemberAndPeriod(member, payment.getPeriod())) {
            throw MemberDomainException.duplicatePaymentForPeriod(
                member.getName(), payment.getPeriod().toString());
        }

        payment.markAsProcessed();
        
        member.recordPayment(payment);

        memberRepository.save(member);
        Payment saved = paymentRepository.save(payment);
        recordActivity.record(ActivityType.PAYMENT_RECORDED,
                String.format(Locale.ROOT, "Payment of %.2f for %s was recorded for %s",
                        saved.getAmount(), saved.getPeriod(), member.getName()),
                "PAYMENT", saved.getId());
        return saved;
    }
}