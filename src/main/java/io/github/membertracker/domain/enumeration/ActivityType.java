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
    MESSAGE_SENT,
    // Written by the sample data (002.sample-data.sql) before the app recorded anything; nothing writes these now,
    // but they are in existing databases and must stay readable.
    SYSTEM_STARTUP,
    BULK_IMPORT,
    PAYMENT_REMINDER_SENT
}
