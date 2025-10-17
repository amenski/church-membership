-- liquibase formatted sql

-- changeset aman:schema-creation
-- Create Member table
CREATE TABLE member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    join_date DATE NOT NULL,
    last_payment_date DATE,
    consecutive_months_missed INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create index on email for faster lookups and ensuring uniqueness
CREATE UNIQUE INDEX idx_member_email ON member(email);

-- Create Payment table
CREATE TABLE payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    period VARCHAR(7) NOT NULL, -- Format: YYYY-MM
    payment_date DATE NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
);

-- Create indexes on payment table for faster lookups
CREATE INDEX idx_payment_member ON payment(member_id);
CREATE INDEX idx_payment_period ON payment(period);
CREATE INDEX idx_payment_date ON payment(payment_date);

-- Create Communication table
CREATE TABLE communication (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    message_content TEXT NOT NULL,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_date TIMESTAMP,
    type VARCHAR(20) NOT NULL, -- REMINDER, ANNOUNCEMENT, PERSONAL
    sent_to_all_members BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create Message Delivery table (for tracking which communications were sent to which members)
CREATE TABLE message_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    communication_id BIGINT NOT NULL,
    delivery_time TIMESTAMP,
    status VARCHAR(20) NOT NULL, -- PENDING, SENT, FAILED, DELIVERED
    channel VARCHAR(20) NOT NULL, -- EMAIL, SMS, WHATSAPP
    response_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_member FOREIGN KEY (recipient_id) REFERENCES member(id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_communication FOREIGN KEY (communication_id) REFERENCES communication(id) ON DELETE CASCADE
);

-- Create indexes on message_delivery table
CREATE INDEX idx_delivery_recipient ON message_delivery(recipient_id);
CREATE INDEX idx_delivery_communication ON message_delivery(communication_id);
CREATE INDEX idx_delivery_status ON message_delivery(status);

-- Create Activity Log table for tracking system events
CREATE TABLE activity_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    entity_type VARCHAR(50), -- MEMBER, PAYMENT, COMMUNICATION
    entity_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on activity_log
CREATE INDEX idx_activity_type ON activity_log(activity_type);
CREATE INDEX idx_activity_entity ON activity_log(entity_type, entity_id);

-- Create Users table with role and security fields
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN DEFAULT TRUE,
    account_non_locked BOOLEAN DEFAULT TRUE,
    credentials_non_expired BOOLEAN DEFAULT TRUE,
    last_password_change TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    failed_login_attempts INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create index for faster lookups on users table
CREATE INDEX idx_users_email ON users(email);