-- liquibase formatted sql

-- Contract step (person/membership plan, step 12): the member columns that person and status replaced are dropped.
-- name, email and phone live on person; active is (status = 'MEMBER'). DESTRUCTIVE: take a dump first (plan section 6).
-- One statement per changeset: MySQL cannot roll a failed DDL statement back, so a half-applied file stays half applied.
-- Order: the index that uses email goes first, then the columns. No foreign key touches any of them (MySQL error 1553
-- only concerns an index a foreign key needs, and idx_member_person / fk_member_person stay).
-- Dialect: plain DROP INDEX ... ON and DROP COLUMN, accepted as written by MySQL 8 and by H2 in MySQL mode (as in 009).
--
-- Rollback: Liquibase undoes the changesets bottom to top. Each column's rollback re-adds the column and restores its
-- values from person (UPDATE ... JOIN, a MySQL statement; H2 has no multi-table UPDATE and never runs a rollback), so a
-- partial rollback (rollback-count) is safe too. Only name gets its NOT NULL back, after the values are in.
-- "m.updated_at = m.updated_at" keeps ON UPDATE CURRENT_TIMESTAMP from touching every member row (as in 013).
-- The last rolled-back changeset recreates idx_member_email_lookup, after email is back.

-- changeset aman:member-drop-email-lookup-index
DROP INDEX idx_member_email_lookup ON member;
--rollback CREATE INDEX idx_member_email_lookup ON member(email);

-- changeset aman:member-drop-name
ALTER TABLE member DROP COLUMN name;
--rollback ALTER TABLE member ADD COLUMN name VARCHAR(100) NULL;
--rollback UPDATE member m JOIN person p ON p.id = m.person_id SET m.name = p.name, m.updated_at = m.updated_at;
--rollback ALTER TABLE member MODIFY name VARCHAR(100) NOT NULL;

-- changeset aman:member-drop-email
ALTER TABLE member DROP COLUMN email;
--rollback ALTER TABLE member ADD COLUMN email VARCHAR(100) NULL;
--rollback UPDATE member m JOIN person p ON p.id = m.person_id SET m.email = p.email, m.updated_at = m.updated_at;

-- changeset aman:member-drop-phone
ALTER TABLE member DROP COLUMN phone;
--rollback ALTER TABLE member ADD COLUMN phone VARCHAR(20) NULL;
--rollback UPDATE member m JOIN person p ON p.id = m.person_id SET m.phone = p.phone, m.updated_at = m.updated_at;

-- changeset aman:member-drop-active
ALTER TABLE member DROP COLUMN active;
--rollback ALTER TABLE member ADD COLUMN active BOOLEAN DEFAULT TRUE;
--rollback UPDATE member SET active = (status = 'MEMBER'), updated_at = updated_at;
