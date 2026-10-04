package io.github.membertracker.infrastructure.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of POST and PUT /api/people. A person may have no membership (a child, a dependent): only the name is required.
 * The limits follow the {@code person} table (migration 012) and match {@code MemberRequest} where a field is on both
 * (name, email, phone). The membership is not part of it: {@code POST /api/people/{id}/membership} starts one. Unknown
 * properties are ignored.
 */
public class PersonRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    /** Optional; trimmed, a blank value is stored as null. Not unique: dependents and members may share an address. */
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    /** Optional; a blank value is stored as null. The same rule as a member's phone. */
    @Size(max = 20, message = "Phone must be at most 20 characters")
    @Pattern(regexp = "^\\+?[0-9\\s\\-\\(\\)]{10,}$", message = "Phone number should be valid")
    private String phone;

    /** Optional. A birthday is always in the past. */
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    /**
     * Optional household of the person. Absent leaves it unchanged on update; an explicit null removes the person from
     * its household; an id puts it there (an unknown id is a 400 with code HOUSEHOLD_001). {@link #isHouseholdIdSet()}
     * tells absent from null: the setter runs only when the property is in the JSON.
     */
    private Long householdId;
    private boolean householdIdSet;

    public PersonRequest() {
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
        this.email = (email == null || email.isBlank()) ? null : email.trim();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = (phone == null || phone.isBlank()) ? null : phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
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
