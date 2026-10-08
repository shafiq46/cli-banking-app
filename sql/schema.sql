-- Secure Simple CLI Banking Application - database schema (MySQL 8+)
CREATE DATABASE IF NOT EXISTS banking_db CHARACTER SET utf8mb4;
USE banking_db;

CREATE TABLE IF NOT EXISTS users (
    user_id         INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    username        VARCHAR(30)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            ENUM('CUSTOMER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until    DATETIME NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS accounts (
    account_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NOT NULL UNIQUE,
    account_no VARCHAR(12) NOT NULL UNIQUE,
    balance    DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT chk_balance_nonneg CHECK (balance >= 0),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id        INT NOT NULL,
    type              ENUM('DEPOSIT','WITHDRAWAL','TRANSFER_OUT','TRANSFER_IN') NOT NULL,
    amount            DECIMAL(15,2) NOT NULL,
    counterparty_acct VARCHAR(12) NULL,
    balance_after     DECIMAL(15,2) NOT NULL,
    timestamp         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_amount_pos CHECK (amount > 0),
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    INDEX idx_tx_account_time (account_id, timestamp)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS login_audit (
    audit_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(30) NOT NULL,
    success      BOOLEAN NOT NULL,
    attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
