package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PaymentEntity;
import io.github.membertracker.infrastructure.persistence.mapper.MemberPersistenceMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        MemberEntity memberEntity = MemberPersistenceMapper.toEntity(member);
        return paymentJpaRepository.findByMember(memberEntity).stream()
                .map(this::mapToPayment)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByMemberAndPeriod(Member member, YearMonth period) {
        MemberEntity memberEntity = MemberPersistenceMapper.toEntity(member);
        return paymentJpaRepository.existsByMemberAndPeriod(memberEntity, period);
    }

    @Override
    public double sumAmountByPeriod(YearMonth period) {
        return paymentJpaRepository.sumAmountByPeriod(period);
    }

    @Override
    public List<Payment> findRecent(int limit) {
        Sort newestFirst = Sort.by(Sort.Order.desc("paymentDate"), Sort.Order.desc("id"));
        return paymentJpaRepository.findAll(PageRequest.of(0, limit, newestFirst)).stream()
                .map(this::mapToPayment)
                .collect(Collectors.toList());
    }

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = mapToEntity(payment);
        return mapToPayment(paymentJpaRepository.save(entity));
    }

    private Payment mapToPayment(PaymentEntity entity) {
        Payment payment = new Payment();
        payment.setId(entity.getId());
        payment.setMember(MemberPersistenceMapper.toDomain(entity.getMember()));
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
        entity.setMember(MemberPersistenceMapper.toEntity(payment.getMember()));
        entity.setPeriod(payment.getPeriod());
        entity.setPaymentDate(payment.getPaymentDate());
        entity.setAmount(payment.getAmount());
        entity.setPaymentMethod(payment.getPaymentMethod().name());
        entity.setNotes(payment.getNotes());
        return entity;
    }
}