package io.github.membertracker.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Persistence only, no domain class yet (person/membership plan). Name, email and phone are written through from the
 * member (and read back, step 9); the household is the person's own; birth_date and the timestamps are left to the database.
 */
@Entity
@Table(name = "person")
public class PersonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    /**
     * The household (household_id, nullable, ON DELETE SET NULL). EAGER so a member's household name travels with it;
     * the list queries name "person.household" in their entity graph to keep a list one joined statement.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "household_id")
    private HouseholdEntity household;

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

    public HouseholdEntity getHousehold() {
        return household;
    }

    public void setHousehold(HouseholdEntity household) {
        this.household = household;
    }
}
