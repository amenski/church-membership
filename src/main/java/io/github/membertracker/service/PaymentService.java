package io.github.membertracker.service;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, MemberRepository memberRepository) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepository.findById(id);
    }

    public List<Payment> getPaymentsByMember(Member member) {
        return paymentRepository.findByMember(member);
    }

    public Payment recordPayment(Payment payment) {
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

    public boolean hasPaymentForMonth(Member member, YearMonth month) {
        return !paymentRepository.findByMemberAndPeriod(member, month).isEmpty();
    }

    public void updateMissingPaymentCounters() {
        YearMonth currentMonth = YearMonth.now();
        List<Member> allMembers = memberRepository.findAll();

        for (Member member : allMembers) {
            if (!hasPaymentForMonth(member, currentMonth.minusMonths(1))) {
                member.setConsecutiveMonthsMissed(member.getConsecutiveMonthsMissed() + 1);
                memberRepository.save(member);
            }
        }
    }
}
