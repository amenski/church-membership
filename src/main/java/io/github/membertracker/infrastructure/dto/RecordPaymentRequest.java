package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;

/** Body of POST /api/payments. The member is referenced by id and loaded server-side. */
public class RecordPaymentRequest {

    @NotNull(message = "Member id is required")
    @Positive(message = "Member id must be positive")
    private Long memberId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private Double amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    /** Optional, JSON "YYYY-MM"; absent means the current month. */
    private YearMonth period;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    public RecordPaymentRequest() {}

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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

    public YearMonth getPeriod() {
        return period;
    }

    public void setPeriod(YearMonth period) {
        this.period = period;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
