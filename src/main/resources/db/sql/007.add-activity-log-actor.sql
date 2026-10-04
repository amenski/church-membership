-- liquibase formatted sql

-- changeset aman:add-activity-log-actor
-- Who did it (the signed-in user's email, or "system" for the scheduler), and an index to list newest first.
ALTER TABLE activity_log ADD COLUMN actor VARCHAR(100) NULL;
--rollback ALTER TABLE activity_log DROP COLUMN actor;
CREATE INDEX idx_activity_created ON activity_log(created_at);
--rollback DROP INDEX idx_activity_created ON activity_log;
