package io.github.membertracker.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate joinDate;
    private LocalDate lastPaymentDate;
    private int consecutiveMonthsMissed;

    @Column(name = "last_missed_count_month", length = 7)
    private String lastMissedCountMonth;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "MEMBER";

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /**
     * The person behind this membership (person_id, NOT NULL, unique), the only home of name, email and phone.
     * Null only on the detached references the payment and delivery repositories build, which are never saved.
     */
    @OneToOne(optional = false, cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private PersonEntity person;

    public MemberEntity() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getLastMissedCountMonth() {
        return lastMissedCountMonth;
    }

    public void setLastMissedCountMonth(String lastMissedCountMonth) {
        this.lastMissedCountMonth = lastMissedCountMonth;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public PersonEntity getPerson() {
        return person;
    }

    public void setPerson(PersonEntity person) {
        this.person = person;
    }
}
