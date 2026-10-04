package io.github.membertracker.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST and PUT /api/households. Limits follow the {@code household} table (migration 012); notes are capped at
 * 2000 characters. Optional text is trimmed and a blank value is stored as null. Who lives in the household is not
 * part of it: it is set from the member ({@code householdId} of a member request). Unknown properties are ignored.
 */
public class HouseholdRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 100, message = "Address line 1 must be at most 100 characters")
    private String addressLine1;

    @Size(max = 100, message = "Address line 2 must be at most 100 characters")
    private String addressLine2;

    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;

    @Size(max = 20, message = "Postal code must be at most 20 characters")
    private String postalCode;

    @Size(max = 2000, message = "Notes must be at most 2000 characters")
    private String notes;

    public HouseholdRequest() {
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = optional(addressLine1);
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = optional(addressLine2);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = optional(city);
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = optional(postalCode);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = optional(notes);
    }
}
