-- liquibase formatted sql

-- Payments and message deliveries must outlive any attempt to delete a member: the database itself now refuses
-- (audit C9). Members are archived instead; a permanent delete is an admin API that checks for history first.
-- One statement per changeset: MySQL cannot roll a failed DDL statement back, so a half-applied file stays half applied.
-- Dialect: "DROP CONSTRAINT <name>" is accepted by H2 and by MySQL 8.0.19 and later (it replaces the older
-- "DROP FOREIGN KEY <name>", which H2 rejects), so one form serves both. Verified on H2 by MemberDeleteRestrictMigrationTest
-- and on MySQL 8.4 against a copy of the demo database.

-- changeset aman:payment-fk-drop
ALTER TABLE payment DROP CONSTRAINT fk_payment_member;
--rollback ALTER TABLE payment ADD CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE;

-- changeset aman:payment-fk-restrict
ALTER TABLE payment ADD CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE RESTRICT;
--rollback ALTER TABLE payment DROP CONSTRAINT fk_payment_member;

-- changeset aman:delivery-fk-drop
ALTER TABLE message_delivery DROP CONSTRAINT fk_delivery_member;
--rollback ALTER TABLE message_delivery ADD CONSTRAINT fk_delivery_member FOREIGN KEY (recipient_id) REFERENCES member(id) ON DELETE CASCADE;

-- changeset aman:delivery-fk-restrict
ALTER TABLE message_delivery ADD CONSTRAINT fk_delivery_member FOREIGN KEY (recipient_id) REFERENCES member(id) ON DELETE RESTRICT;
--rollback ALTER TABLE message_delivery DROP CONSTRAINT fk_delivery_member;
