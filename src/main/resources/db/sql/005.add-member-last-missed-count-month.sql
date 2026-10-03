-- liquibase formatted sql

-- changeset aman:add-member-last-missed-count-month
-- Last month (YYYY-MM) for which consecutive_months_missed was raised; makes the monthly job idempotent
ALTER TABLE member ADD COLUMN last_missed_count_month CHAR(7) NULL;
--rollback ALTER TABLE member DROP COLUMN last_missed_count_month;
