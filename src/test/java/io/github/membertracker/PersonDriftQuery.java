package io.github.membertracker;

/**
 * The drift check of the person/membership plan (section 6), as plain SQL both MySQL 8 and H2 accept. It returns one
 * row per problem and must return no rows: a member whose name, email or phone differs from its person's (DRIFT), a
 * member without a person row (NO_PERSON), a person without a member (NO_MEMBER). Null-safe by hand because H2 and
 * MySQL do not share a null-safe operator.
 */
final class PersonDriftQuery {

    static final String SQL = """
        SELECT m.id AS member_id, m.person_id AS person_id, 'DRIFT' AS problem
          FROM member m JOIN person p ON p.id = m.person_id
         WHERE NOT (m.name = p.name
                AND (m.email = p.email OR (m.email IS NULL AND p.email IS NULL))
                AND (m.phone = p.phone OR (m.phone IS NULL AND p.phone IS NULL)))
        UNION ALL
        SELECT m.id, m.person_id, 'NO_PERSON'
          FROM member m LEFT JOIN person p ON p.id = m.person_id WHERE p.id IS NULL
        UNION ALL
        SELECT NULL, p.id, 'NO_MEMBER'
          FROM person p LEFT JOIN member m ON m.person_id = p.id WHERE m.id IS NULL""";

    private PersonDriftQuery() {
    }
}
