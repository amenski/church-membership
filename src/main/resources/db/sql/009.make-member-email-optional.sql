-- liquibase formatted sql

-- A member may have no email (a child, a spouse) and two members may share one address.
-- One statement per changeset: MySQL cannot roll a failed DDL statement back.

-- changeset aman:member-email-drop-unique
DROP INDEX idx_member_email ON member;
--rollback CREATE UNIQUE INDEX idx_member_email ON member(email);

-- changeset aman:member-email-nullable
ALTER TABLE member MODIFY email VARCHAR(100) NULL;
--rollback ALTER TABLE member MODIFY email VARCHAR(100) NOT NULL;

-- changeset aman:member-email-lookup-index
CREATE INDEX idx_member_email_lookup ON member(email);
--rollback DROP INDEX idx_member_email_lookup ON member;
