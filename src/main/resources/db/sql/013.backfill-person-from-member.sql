-- liquibase formatted sql

-- Backfill: one person per member (person/membership plan, step 8), then link each member to its person.
-- Ids are preserved (person.id = member.id), so no payment or message_delivery row changes. Archived members get a person too.
-- All statements are safe on an empty member table. One statement per changeset: MySQL cannot roll a failed DDL
-- statement back, so a half-applied file stays half applied.
-- Auto-increment: InnoDB (MySQL 8) raises the person counter past an explicit id by itself; H2 in MySQL mode does the
-- same. Both are asserted (PersonBackfillMigrationTest on H2, plan section 6 on MySQL).
-- Dialect: plain INSERT ... SELECT, ADD COLUMN, UPDATE, MODIFY, CREATE UNIQUE INDEX and "DROP CONSTRAINT <name>"
-- (H2 and MySQL 8.0.19+, as in 011) work as written on both.
-- Manual rollback order is the reverse of the file; the foreign key goes before its index (MySQL error 1553).

-- changeset aman:person-backfill
INSERT INTO person (id, name, email, phone, created_at)
    SELECT id, name, email, phone, created_at FROM member;
--rollback DELETE FROM person;

-- changeset aman:member-person-id
ALTER TABLE member ADD COLUMN person_id BIGINT NULL;
--rollback ALTER TABLE member DROP COLUMN person_id;

-- changeset aman:member-person-link
-- updated_at = updated_at keeps ON UPDATE CURRENT_TIMESTAMP from touching every member row.
UPDATE member SET person_id = id, updated_at = updated_at;
--rollback UPDATE member SET person_id = NULL, updated_at = updated_at;

-- changeset aman:member-person-not-null
ALTER TABLE member MODIFY person_id BIGINT NOT NULL;
--rollback ALTER TABLE member MODIFY person_id BIGINT NULL;

-- The unique index comes first so the foreign key uses it (MySQL would otherwise add a second, plain index).
-- changeset aman:member-person-unique
CREATE UNIQUE INDEX idx_member_person ON member(person_id);
--rollback DROP INDEX idx_member_person ON member;

-- changeset aman:member-person-fk
ALTER TABLE member ADD CONSTRAINT fk_member_person FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE RESTRICT;
--rollback ALTER TABLE member DROP CONSTRAINT fk_member_person;
