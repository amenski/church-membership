-- Add profile fields to users table
ALTER TABLE users
ADD COLUMN first_name VARCHAR(50),
ADD COLUMN last_name VARCHAR(50),
ADD COLUMN phone VARCHAR(20),
ADD COLUMN bio VARCHAR(500);
