package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.PaymentDomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public class Payment {

    private Long id;
    private Member member;
    private YearMonth period;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String notes;

    public Payment() {
    }

    public Payment(Member member, YearMonth period, BigDecimal amount, PaymentMethod paymentMethod) {
        this.member = member;
        this.period = period;
        this.paymentDate = LocalDate.now();
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }

    public void validateAmount() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentDomainException.invalidPaymentAmount(amount, BigDecimal.TEN);
        }
    }

    public void validatePeriod() {
        if (period == null) {
            throw PaymentDomainException.invalidPaymentPeriod(null);
        }

        YearMonth currentMonth = YearMonth.now();
        if (period.isAfter(currentMonth)) {
            throw PaymentDomainException.paymentPeriodInFuture(period);
        }

        YearMonth threeMonthsAgo = currentMonth.minusMonths(3);
        if (period.isBefore(threeMonthsAgo)) {
            throw PaymentDomainException.invalidPaymentPeriod(period);
        }
    }

    public boolean isForCurrentPeriod() {
        if (period == null) {
            return false;
        }
        YearMonth currentMonth = YearMonth.now();
        return period.equals(currentMonth);
    }

    public boolean isOnTime() {
        if (paymentDate == null || period == null) {
            return false;
        }
        LocalDate dueDate = period.atEndOfMonth();
        return !paymentDate.isAfter(dueDate);
    }

    public int getDaysLate() {
        if (!isOnTime() && paymentDate != null && period != null) {
            LocalDate dueDate = period.atEndOfMonth();
            return (int) ChronoUnit.DAYS.between(dueDate, paymentDate);
        }
        return 0;
    }

    public boolean isValid() {
        try {
            validateAmount();
            validatePeriod();
            return member != null && member.isActive();
        } catch (PaymentDomainException e) {
            return false;
        }
    }

    public void markAsProcessed() {
        if (paymentDate == null) {
            this.paymentDate = LocalDate.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public YearMonth getPeriod() {
        return period;
    }

    public void setPeriod(YearMonth period) {
        this.period = period;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = PaymentMethod.fromCode(paymentMethod);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}