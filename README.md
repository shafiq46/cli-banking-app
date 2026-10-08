# Secure Simple CLI Banking Application

Java + JDBC + MySQL implementation of the base paper *"A Secure Simple CLI Banking Application for Account and Transaction Management"*.
**Educational prototype only. Use synthetic data. Not for production banking.**

## Features
Registration, login, balance, deposit, withdrawal, fund transfer, transaction history, change password, logout,
and an admin-only account listing (role-based access).

## Security measures (per paper, Section VIII)
- Salted PBKDF2-HMAC-SHA256 password hashing (210k iterations, constant-time compare)
- Parameterized SQL (PreparedStatement) everywhere
- Input validation (usernames, names, account numbers, amounts, password strength)
- Atomic transfers: one DB transaction, `SELECT ... FOR UPDATE` row locks in a fixed order (no deadlocks / lost updates)
- DB constraints: `balance >= 0`, `amount > 0`, foreign keys
- Login audit log, generic error messages, lockout for 15 min after 5 failed logins
- Transaction log with `balance_after` as an audit trail
- Role column (`CUSTOMER` / `ADMIN`)

## Requirements
JDK 17+, Maven 3.8+, MySQL 8.0.16+ (CHECK constraints are enforced from 8.0.16).

## Setup
1. Create the schema:
   ```
   mysql -u root -p < sql/schema.sql
   ```
2. Configure the connection (preferred: environment variables):
   ```
   export BANK_DB_URL="jdbc:mysql://localhost:3306/banking_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   export BANK_DB_USER=root
   export BANK_DB_PASSWORD=your_password
   ```
   (or edit `src/main/resources/db.properties`; don't commit real passwords)
3. Build & run:
   ```
   mvn clean package
   java -jar target/cli-banking-app-1.0.0.jar
   ```
4. (Optional) make a user an admin:
   ```
   mysql> UPDATE banking_db.users SET role='ADMIN' WHERE username='your_username';
   ```

## Run unit tests
```
mvn test
```

## Manual test plan (paper Section XI)
| Test | Input | Expected |
|---|---|---|
| Login | valid credentials | banking menu |
| Login | wrong password | "Invalid username or password." |
| Deposit | positive amount | balance up, history row |
| Withdrawal | amount > balance | "Insufficient balance." |
| Transfer | valid destination + funds | debit and credit, two history rows |
| Transfer | unknown destination | rejected, no balance change |
| History | logged-in user | only own transactions |

## Project layout
```
sql/schema.sql                     MySQL schema
src/main/java/com/bank/Main.java   entry point
  db/Database.java                 JDBC connection
  service/AuthService.java         register, login, lockout, password change
  service/BankService.java         deposit, withdraw, transfer, history
  ui/ConsoleUI.java                menu-driven CLI
  util/PasswordUtil.java, Validator.java
```

## Future work
TOTP MFA, encryption in transit/at rest, REST API, web/mobile UI, anomaly detection.
