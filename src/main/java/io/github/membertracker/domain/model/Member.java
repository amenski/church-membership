package io.github.membertracker.domain.model;

import io.github.membertracker.domain.exception.MemberDomainException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public class Member {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private LocalDate joinDate;
    private LocalDate lastPaymentDate;
    private int consecutiveMonthsMissed;
    private boolean active;

    public Member() {
    }

    public Member(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.joinDate = LocalDate.now();
        this.active = true;
        this.consecutiveMonthsMissed = 0;
    }

    public void recordPayment(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }

        if (paymentCoversCurrentPeriod(payment)) {
            this.lastPaymentDate = payment.getPaymentDate();
            this.consecutiveMonthsMissed = 0;
        } else {
            this.lastPaymentDate = payment.getPaymentDate();
        }
    }

    public void markPaymentMissed() {
        this.consecutiveMonthsMissed++;
    }

    public void activate() {
        if (this.active) {
            throw MemberDomainException.memberAlreadyActive(this.name);
        }
        this.active = true;
        this.consecutiveMonthsMissed = 0;
    }

    public void deactivate() {
        if (!this.active) {
            throw MemberDomainException.memberAlreadyInactive(this.name);
        }
        this.active = false;
    }

    public boolean isPaymentOverdue() {
        return consecutiveMonthsMissed > 0;
    }

    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() &&
               joinDate != null && !joinDate.isAfter(LocalDate.now());
    }

    public long getMembershipDurationInMonths() {
        if (joinDate == null) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(joinDate, LocalDate.now());
    }

    private boolean paymentCoversCurrentPeriod(Payment payment) {
        if (payment.getPeriod() == null) {
            return false;
        }
        YearMonth currentMonth = YearMonth.now();
        return payment.getPeriod().equals(currentMonth);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public LocalDate getLastPaymentDate() {
        return lastPaymentDate;
    }

    public void setLastPaymentDate(LocalDate lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }

    public int getConsecutiveMonthsMissed() {
        return consecutiveMonthsMissed;
    }

    public void setConsecutiveMonthsMissed(int consecutiveMonthsMissed) {
        this.consecutiveMonthsMissed = consecutiveMonthsMissed;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}