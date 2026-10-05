-- liquibase formatted sql

-- The UI language the user chose: 'am' (Amharic) or 'en'. The default matches the app's own default,
-- so an account that never chose keeps behaving as before.

-- changeset aman:user-language
ALTER TABLE users ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'am';
--rollback ALTER TABLE users DROP COLUMN language;
