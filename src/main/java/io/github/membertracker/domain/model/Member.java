package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.MemberDomainException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public class Member {

    private Long id;
    
    @NotBlank(message = "Name is required")
    private String name;
    
    /** Optional: a child or a spouse may have no address, and two members may share one. */
    @Email(message = "Email should be valid")
    private String email;
    
    @Pattern(regexp = "^\\+?[0-9\\s\\-\\(\\)]{10,}$", message = "Phone number should be valid")
    private String phone;
    
    @PastOrPresent(message = "Join date cannot be in the future")
    private LocalDate joinDate;
    
    private LocalDate lastPaymentDate;
    private int consecutiveMonthsMissed;
    private YearMonth lastMissedCountMonth;
    private MemberStatus status = MemberStatus.MEMBER;
    private LocalDateTime archivedAt;

    public Member() {
    }

    public Member(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.joinDate = LocalDate.now();
        this.status = MemberStatus.MEMBER;
        this.consecutiveMonthsMissed = 0;
    }

    public void recordPayment(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }

        LocalDate paidOn = payment.getPaymentDate();
        if (paidOn != null && (lastPaymentDate == null || paidOn.isAfter(lastPaymentDate))) {
            this.lastPaymentDate = paidOn;
        }

        if (paymentCoversCurrentPeriod(payment)) {
            this.consecutiveMonthsMissed = 0;
        }
    }

    /**
     * Counts {@code month} as missed, at most once per month.
     *
     * @return true if the counter was raised, false if this month was already counted
     */
    public boolean markMissedFor(YearMonth month) {
        if (month.equals(lastMissedCountMonth)) {
            return false;
        }
        this.consecutiveMonthsMissed++;
        this.lastMissedCountMonth = month;
        return true;
    }

    /** Back to MEMBER from any other status (an archived member is restored); the months-behind counter starts again. */
    public void activate() {
        if (this.status == MemberStatus.MEMBER) {
            throw MemberDomainException.memberAlreadyActive(this.name);
        }
        this.status = MemberStatus.MEMBER;
        this.archivedAt = null;
        this.consecutiveMonthsMissed = 0;
    }

    /** To INACTIVE from any other status (an archived member is restored); the counter is frozen. */
    public void deactivate() {
        if (this.status == MemberStatus.INACTIVE) {
            throw MemberDomainException.memberAlreadyInactive(this.name);
        }
        this.status = MemberStatus.INACTIVE;
        this.archivedAt = null;
    }

    /** Hides the member from the lists; nothing else changes, the counter and all history are kept. */
    public void archive(LocalDateTime now) {
        this.status = MemberStatus.ARCHIVED;
        this.archivedAt = now;
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

    public YearMonth getLastMissedCountMonth() {
        return lastMissedCountMonth;
    }

    public void setLastMissedCountMonth(YearMonth lastMissedCountMonth) {
        this.lastMissedCountMonth = lastMissedCountMonth;
    }

    /** Derived from the status: true only for a member whose dues count. There is no setter. */
    public boolean isActive() {
        return status.countsForDues();
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
