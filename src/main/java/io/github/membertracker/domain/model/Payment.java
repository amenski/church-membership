package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.exception.PaymentDomainException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.time.YearMonth;

public class Payment {

    /** Older months are refused as a probable typo (e.g. 2204 for 2024). */
    static final int MAX_YEARS_BACK = 10;

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
            throw PaymentDomainException.invalidPaymentAmount(amount);
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

        YearMonth earliest = currentMonth.minusYears(MAX_YEARS_BACK);
        if (period.isBefore(earliest)) {
            throw PaymentDomainException.paymentPeriodTooOld(period, earliest);
        }
    }

    public void validatePaymentDate() {
        if (paymentDate != null && paymentDate.isAfter(LocalDate.now())) {
            throw PaymentDomainException.paymentDateInFuture(paymentDate);
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