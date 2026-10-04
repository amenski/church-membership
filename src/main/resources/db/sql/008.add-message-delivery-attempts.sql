-- liquibase formatted sql

-- changeset aman:add-message-delivery-attempts
-- How many send attempts were made for this delivery (the retries inside one send plus later manual retries).
ALTER TABLE message_delivery ADD COLUMN attempts INT NOT NULL DEFAULT 0;
--rollback ALTER TABLE message_delivery DROP COLUMN attempts;
