package io.github.membertracker.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "member")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;
    private LocalDate joinDate;
    private LocalDate lastPaymentDate;
    private int consecutiveMonthsMissed;
    private boolean active;

    public MemberEntity() {
    }

    public MemberEntity(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.joinDate = LocalDate.now();
        this.active = true;
        this.consecutiveMonthsMissed = 0;
    }

    // Getters and Setters
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