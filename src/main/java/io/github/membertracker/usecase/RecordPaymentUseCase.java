package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class RecordPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;

    public RecordPaymentUseCase(PaymentRepository paymentRepository, MemberRepository memberRepository) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Records a payment and updates the member's payment status.
     *
     * @param payment the payment to record
     * @return the saved payment
     */
    @Transactional
    public Payment invoke(Payment payment) {
        // Update the member's last payment date
        Member member = payment.getMember();
        member.setLastPaymentDate(payment.getPaymentDate());

        // Reset consecutive months missed counter if this is a current payment
        YearMonth currentMonth = YearMonth.now();
        if (payment.getPeriod().equals(currentMonth) ||
                payment.getPeriod().isAfter(currentMonth.minusMonths(2))) {
            member.setConsecutiveMonthsMissed(0);
        }

        memberRepository.save(member);
        return paymentRepository.save(payment);
    }
}