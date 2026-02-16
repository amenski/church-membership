package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.PaymentDomainException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public class Payment {

    private Long id;
    
    @NotNull(message = "Member is required")
    private Member member;
    
    @NotNull(message = "Payment period is required")
    private YearMonth period;
    
    @PastOrPresent(message = "Payment date cannot be in the future")
    private LocalDate paymentDate;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private Double amount;
    
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
    
    private String notes;

    public Payment() {
    }

    public Payment(Member member, YearMonth period, Double amount, PaymentMethod paymentMethod) {
        this.member = member;
        this.period = period;
        this.paymentDate = LocalDate.now();
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }

    public void validateAmount() {
        if (amount == null || amount <= 0.0) {
            throw PaymentDomainException.invalidPaymentAmount(amount, 10.0);
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

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
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