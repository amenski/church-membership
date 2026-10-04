package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.enumeration.MemberStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of POST and PUT /api/members. Counters, last payment date and the monthly-job marker are
 * system-managed and deliberately absent: unknown JSON properties are ignored.
 */
public class MemberRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    /** Optional; trimmed, a blank value is stored as null. Not unique: two members may share an address. */
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    /** Optional; a blank value is stored as null. */
    @Pattern(regexp = "^\\+?[0-9\\s\\-\\(\\)]{10,}$", message = "Phone number should be valid")
    private String phone;

    /** Optional; absent means today on create and unchanged on update. */
    @PastOrPresent(message = "Join date cannot be in the future")
    private LocalDate joinDate;

    /** Optional. Absent means a new member is MEMBER and an edit leaves the status alone. */
    private MemberStatus status;

    /**
     * Optional household of the person. Absent leaves it unchanged on update; an explicit null removes the person from
     * its household; an id puts it there (an unknown id is a 400 with code HOUSEHOLD_001). {@link #isHouseholdIdSet()}
     * tells absent from null: the setter runs only when the property is in the JSON.
     */
    private Long householdId;
    private boolean householdIdSet;

    public MemberRequest() {}

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
        this.email = (email == null || email.isBlank()) ? null : email.trim();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = (phone == null || phone.isBlank()) ? null : phone;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public Long getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(Long householdId) {
        this.householdId = householdId;
        this.householdIdSet = true;
    }

    /** True when the body mentioned {@code householdId}, even as null. */
    public boolean isHouseholdIdSet() {
        return householdIdSet;
    }
}
