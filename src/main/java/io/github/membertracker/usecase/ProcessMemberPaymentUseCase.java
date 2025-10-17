package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.policy.MembershipPolicy;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public class ProcessMemberPaymentUseCase {

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipPolicy membershipPolicy;

    public ProcessMemberPaymentUseCase(
            MemberRepository memberRepository,
            PaymentRepository paymentRepository,
            MembershipPolicy membershipPolicy) {
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.membershipPolicy = membershipPolicy;
    }

    public Payment invoke(Long memberId, BigDecimal amount, YearMonth period,
                         String paymentMethodCode, String notes) {
        
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> MemberDomainException.memberNotFound(memberId));

        if (!member.isActive()) {
            throw MemberDomainException.memberAlreadyInactive(member.getName());
        }

        PaymentMethod paymentMethod = PaymentMethod.fromCode(paymentMethodCode);
        Payment payment = new Payment(member, period, amount, paymentMethod);
        payment.setNotes(notes);

        payment.validateAmount();
        payment.validatePeriod();
        
        if (paymentRepository.existsByMemberAndPeriod(member, period)) {
            throw MemberDomainException.duplicatePaymentForPeriod(
                member.getName(), period.toString());
        }

        payment.markAsProcessed();
        member.recordPayment(payment);

        applyMembershipPolicies(member);

        paymentRepository.save(payment);
        memberRepository.save(member);

        return payment;
    }

    private void applyMembershipPolicies(Member member) {
        LocalDate currentDate = LocalDate.now();

        if (membershipPolicy.shouldDeactivate(member, currentDate)) {
            member.deactivate();
        }

        boolean inGoodStanding = membershipPolicy.isInGoodStanding(member, currentDate);
        
        if (!inGoodStanding && member.isActive()) {
        }
    }

    public Payment processPaymentWithReactivation(Long memberId, BigDecimal amount,
                                                 YearMonth period, String paymentMethodCode) {
        
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> MemberDomainException.memberNotFound(memberId));

        LocalDate currentDate = LocalDate.now();

        if (!member.isActive() && membershipPolicy.canReactivate(member, currentDate)) {
            member.activate();
        }

        return invoke(memberId, amount, period, paymentMethodCode, "Payment with reactivation");
    }

    public MemberPaymentStatus getMemberPaymentStatus(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> MemberDomainException.memberNotFound(memberId));

        LocalDate currentDate = LocalDate.now();
        
        long membershipDuration = member.getMembershipDurationInMonths();
        boolean paymentOverdue = member.isPaymentOverdue();
        boolean shouldSendReminder = membershipPolicy.shouldSendReminder(member, currentDate);
        int daysUntilDue = membershipPolicy.daysUntilPaymentDue(member, currentDate);
        boolean inGoodStanding = membershipPolicy.isInGoodStanding(member, currentDate);

        return new MemberPaymentStatus(
            member.getId(),
            member.getName(),
            membershipDuration,
            paymentOverdue,
            shouldSendReminder,
            daysUntilDue,
            inGoodStanding,
            member.getConsecutiveMonthsMissed()
        );
    }

    public static class MemberPaymentStatus {
        private final Long memberId;
        private final String memberName;
        private final long membershipDurationMonths;
        private final boolean paymentOverdue;
        private final boolean shouldSendReminder;
        private final int daysUntilPaymentDue;
        private final boolean inGoodStanding;
        private final int consecutiveMonthsMissed;

        public MemberPaymentStatus(Long memberId, String memberName, long membershipDurationMonths,
                                 boolean paymentOverdue, boolean shouldSendReminder,
                                 int daysUntilPaymentDue, boolean inGoodStanding,
                                 int consecutiveMonthsMissed) {
            this.memberId = memberId;
            this.memberName = memberName;
            this.membershipDurationMonths = membershipDurationMonths;
            this.paymentOverdue = paymentOverdue;
            this.shouldSendReminder = shouldSendReminder;
            this.daysUntilPaymentDue = daysUntilPaymentDue;
            this.inGoodStanding = inGoodStanding;
            this.consecutiveMonthsMissed = consecutiveMonthsMissed;
        }

        public Long getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public long getMembershipDurationMonths() { return membershipDurationMonths; }
        public boolean isPaymentOverdue() { return paymentOverdue; }
        public boolean isShouldSendReminder() { return shouldSendReminder; }
        public int getDaysUntilPaymentDue() { return daysUntilPaymentDue; }
        public boolean isInGoodStanding() { return inGoodStanding; }
        public int getConsecutiveMonthsMissed() { return consecutiveMonthsMissed; }
    }
}