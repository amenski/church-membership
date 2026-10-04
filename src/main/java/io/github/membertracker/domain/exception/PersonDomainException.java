package io.github.membertracker.domain.exception;

/** Business rule violations about people and their membership. The messages never repeat what the client sent. */
public class PersonDomainException extends DomainException {

    public static final String PERSON_ALREADY_MEMBER = "PERSON_001";
    public static final String PERSON_HAS_MEMBERSHIP = "PERSON_002";

    private PersonDomainException(String message, String errorCode) {
        super(message, errorCode, "Person");
    }

    /** The person already has a membership; the API answers 409. */
    public static PersonDomainException alreadyMember() {
        return new PersonDomainException("This person already has a membership.", PERSON_ALREADY_MEMBER);
    }

    /** A person who has a membership cannot be deleted as a person; the API answers 409. */
    public static PersonDomainException hasMembership() {
        return new PersonDomainException(
            "This person has a membership. Archive the membership, or delete it permanently if it was made by mistake, first.",
            PERSON_HAS_MEMBERSHIP);
    }

    @Override
    public boolean isConflict() {
        return true;
    }
}
