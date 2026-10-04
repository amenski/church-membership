package io.github.membertracker.domain.exception;

/** Business rule violations about households. The messages never repeat what the client sent. */
public class HouseholdDomainException extends DomainException {

    public static final String HOUSEHOLD_NOT_FOUND = "HOUSEHOLD_001";
    public static final String HOUSEHOLD_HAS_PEOPLE = "HOUSEHOLD_002";

    private HouseholdDomainException(String message, String errorCode, String field) {
        super(message, errorCode, "Household", field);
    }

    /** The {@code householdId} of a member request names no household; reported as a field error on it (400). */
    public static HouseholdDomainException notFound() {
        return new HouseholdDomainException("The selected household does not exist.", HOUSEHOLD_NOT_FOUND, "householdId");
    }

    /** A delete would orphan people; the API answers 409 and nothing is changed. */
    public static HouseholdDomainException hasPeople() {
        return new HouseholdDomainException(
            "This household still has people. Move them to another household or remove them from it first.",
            HOUSEHOLD_HAS_PEOPLE, null);
    }

    @Override
    public boolean isConflict() {
        return HOUSEHOLD_HAS_PEOPLE.equals(getErrorCode());
    }
}
