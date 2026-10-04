package io.github.membertracker.domain.enumeration;

/**
 * Where a person stands with the church. Only {@link #MEMBER} pays dues, is reminded, gets messages
 * and can have a payment recorded; the other statuses keep the record but switch all of that off.
 */
public enum MemberStatus {
    MEMBER,
    INACTIVE,
    DECEASED,
    TRANSFERRED,
    ARCHIVED;

    /** Dues counter, reminders and recorded payments apply. */
    public boolean countsForDues() {
        return this == MEMBER;
    }

    public boolean canReceiveMessages() {
        return this == MEMBER;
    }

    /** Shown in the member list without an extra filter. */
    public boolean listedByDefault() {
        return this != ARCHIVED;
    }
}
