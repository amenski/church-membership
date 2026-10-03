-- liquibase formatted sql

-- changeset aman:add-user-locked-until
-- End of a temporary lock. NULL on a locked account means the lock is permanent (set by an admin or an old row).
ALTER TABLE users ADD COLUMN locked_until DATETIME NULL;
--rollback ALTER TABLE users DROP COLUMN locked_until;
