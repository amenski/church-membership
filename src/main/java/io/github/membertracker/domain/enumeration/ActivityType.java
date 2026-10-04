package io.github.membertracker.domain.enumeration;

/** What happened, as stored in activity_log.activity_type (the enum name). */
public enum ActivityType {
    SIGN_IN,
    PASSWORD_CHANGED,
    MEMBER_CREATED,
    MEMBER_UPDATED,
    MEMBER_ACTIVATED,
    MEMBER_DEACTIVATED,
    MEMBER_DELETED,
    MEMBERS_EXPORTED,
    PAYMENT_RECORDED,
    PAYMENTS_EXPORTED,
    MESSAGE_SENT
}
