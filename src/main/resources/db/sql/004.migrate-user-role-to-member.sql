-- liquibase formatted sql

-- changeset aman:migrate-user-role-to-member
-- USER was removed from UserRole; MEMBER is the new base role
UPDATE users SET role = 'MEMBER' WHERE role = 'USER';
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'MEMBER';
