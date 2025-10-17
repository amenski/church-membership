package io.github.membertracker.infrastructure.dto;

public class UpdateUserProfileRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String bio;

    public UpdateUserProfileRequest() {}

    public UpdateUserProfileRequest(String firstName, String lastName, String phone, String bio) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.bio = bio;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
