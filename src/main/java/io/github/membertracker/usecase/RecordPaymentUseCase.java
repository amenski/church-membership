package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;

public class RecordPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;

    public RecordPaymentUseCase(PaymentRepository paymentRepository, MemberRepository memberRepository) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
    }

    public Payment invoke(Payment payment) {
        Member member = payment.getMember();
        
        payment.validateAmount();
        payment.validatePeriod();

        if (paymentRepository.existsByMemberAndPeriod(member, payment.getPeriod())) {
            throw MemberDomainException.duplicatePaymentForPeriod(
                member.getName(), payment.getPeriod().toString());
        }

        payment.markAsProcessed();
        
        member.recordPayment(payment);

        memberRepository.save(member);
        return paymentRepository.save(payment);
    }
}