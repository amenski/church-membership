-- liquibase formatted sql

-- Person and household tables, created empty and not used by any code yet (person/membership plan, step 7).
-- Step 8 backfills person from member. household comes first because person references it.
-- One statement per changeset: MySQL cannot roll a failed DDL statement back, so a half-applied file stays half applied.
-- Dialect: plain CREATE TABLE / CREATE INDEX, accepted as written by MySQL 8 and by H2 in MySQL mode.
-- Manual rollback order (reverse): drop the foreign key, the two indexes, then DROP TABLE person, DROP TABLE household.

-- changeset aman:household-create
CREATE TABLE household (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address_line1 VARCHAR(100),
    address_line2 VARCHAR(100),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
--rollback DROP TABLE household;

-- changeset aman:person-create
CREATE TABLE person (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    birth_date DATE NULL,
    household_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
--rollback DROP TABLE person;

-- changeset aman:person-email-index
CREATE INDEX idx_person_email ON person(email);
--rollback DROP INDEX idx_person_email ON person;

-- changeset aman:person-household-index
CREATE INDEX idx_person_household ON person(household_id);
--rollback DROP INDEX idx_person_household ON person;

-- The foreign key comes after its index: MySQL refuses to drop an index a foreign key needs, so on rollback the
-- constraint must go first. Dialect: "DROP CONSTRAINT <name>" works on H2 and MySQL 8.0.19+ (as in 011).
-- changeset aman:person-household-fk
ALTER TABLE person ADD CONSTRAINT fk_person_household FOREIGN KEY (household_id) REFERENCES household(id) ON DELETE SET NULL;
--rollback ALTER TABLE person DROP CONSTRAINT fk_person_household;
