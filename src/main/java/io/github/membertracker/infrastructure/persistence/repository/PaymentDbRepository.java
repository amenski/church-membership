package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PaymentDbRepository implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;
    private final MemberDbRepository memberDbRepository;

    public PaymentDbRepository(PaymentJpaRepository paymentJpaRepository, MemberDbRepository memberDbRepository) {
        this.paymentJpaRepository = paymentJpaRepository;
        this.memberDbRepository = memberDbRepository;
    }

    @Override
    public List<Payment> findAll() {
        return paymentJpaRepository.findAll().stream()
                .map(this::mapToPayment)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id)
                .map(this::mapToPayment);
    }

    @Override
    public List<Payment> findByMember(Member member) {
        MemberEntity memberEntity = mapToMemberEntity(member);
        return paymentJpaRepository.findByMember(memberEntity).stream()
                .map(this::mapToPayment)
                .collect(Collectors.toList());
    }

    @Override
    public List<Payment> findByMemberAndPeriod(Member member, YearMonth period) {
        MemberEntity memberEntity = mapToMemberEntity(member);
        return paymentJpaRepository.findByMemberAndPeriod(memberEntity, period).stream()
                .map(this::mapToPayment)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Payment> findFirstByMemberOrderByPaymentDateDesc(Member member) {
        MemberEntity memberEntity = mapToMemberEntity(member);
        return paymentJpaRepository.findFirstByMemberOrderByPaymentDateDesc(memberEntity)
                .map(this::mapToPayment);
    }

    @Override
    public boolean existsByMemberAndPeriod(Member member, YearMonth period) {
        MemberEntity memberEntity = mapToMemberEntity(member);
        return paymentJpaRepository.existsByMemberAndPeriod(memberEntity, period);
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = mapToEntity(payment);
        return mapToPayment(paymentJpaRepository.save(entity));
    }

    private Payment mapToPayment(PaymentEntity entity) {
        Payment payment = new Payment();
        payment.setId(entity.getId());
        payment.setMember(mapToMember(entity.getMember()));
        payment.setPeriod(entity.getPeriod());
        payment.setPaymentDate(entity.getPaymentDate());
        payment.setAmount(entity.getAmount());
        payment.setPaymentMethod(io.github.membertracker.domain.enumeration.PaymentMethod.valueOf(entity.getPaymentMethod()));
        payment.setNotes(entity.getNotes());
        return payment;
    }

    private PaymentEntity mapToEntity(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.setId(payment.getId());
        entity.setMember(mapToMemberEntity(payment.getMember()));
        entity.setPeriod(payment.getPeriod());
        entity.setPaymentDate(payment.getPaymentDate());
        entity.setAmount(payment.getAmount());
        entity.setPaymentMethod(payment.getPaymentMethod().name());
        entity.setNotes(payment.getNotes());
        return entity;
    }

    private Member mapToMember(MemberEntity entity) {
        Member member = new Member();
        member.setId(entity.getId());
        member.setName(entity.getName());
        member.setEmail(entity.getEmail());
        member.setPhone(entity.getPhone());
        member.setJoinDate(entity.getJoinDate());
        member.setLastPaymentDate(entity.getLastPaymentDate());
        member.setConsecutiveMonthsMissed(entity.getConsecutiveMonthsMissed());
        member.setActive(entity.isActive());
        return member;
    }

    private MemberEntity mapToMemberEntity(Member member) {
        MemberEntity entity = new MemberEntity();
        entity.setId(member.getId());
        entity.setName(member.getName());
        entity.setEmail(member.getEmail());
        entity.setPhone(member.getPhone());
        entity.setJoinDate(member.getJoinDate());
        entity.setLastPaymentDate(member.getLastPaymentDate());
        entity.setConsecutiveMonthsMissed(member.getConsecutiveMonthsMissed());
        entity.setActive(member.isActive());
        return entity;
    }
}