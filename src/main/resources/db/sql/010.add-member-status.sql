-- liquibase formatted sql

-- Membership status (MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED) beside the legacy `active` flag.
-- Expand step: the application keeps `active` equal to (status = 'MEMBER'); the column is dropped in a later migration.
-- One statement per changeset: MySQL cannot roll a failed DDL statement back.

-- changeset aman:member-status-columns
ALTER TABLE member ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'MEMBER';
--rollback ALTER TABLE member DROP COLUMN status;

-- changeset aman:member-archived-at
ALTER TABLE member ADD COLUMN archived_at DATETIME NULL;
--rollback ALTER TABLE member DROP COLUMN archived_at;

-- changeset aman:member-status-backfill
-- Today active=false covers inactive, transferred and deceased people; they all become INACTIVE and the owner re-labels by hand.
UPDATE member SET status = CASE WHEN active THEN 'MEMBER' ELSE 'INACTIVE' END;
--rollback UPDATE member SET active = (status = 'MEMBER');

-- changeset aman:member-status-index
CREATE INDEX idx_member_status ON member(status);
--rollback DROP INDEX idx_member_status ON member;
