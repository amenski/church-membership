package io.github.membertracker.domain.model;

/**
 * A family or a shared address: a name, an optional address and free notes. Nothing else: no head of household,
 * and no dues (they stay per membership). The people in it are found through {@code person.household_id}.
 */
public class Household {

    private Long id;
    private String name;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postalCode;
    private String notes;

    public Household() {
    }

    public Household(String name, String addressLine1, String addressLine2, String city, String postalCode,
                     String notes) {
        this.name = name;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.postalCode = postalCode;
        this.notes = notes;
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

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
