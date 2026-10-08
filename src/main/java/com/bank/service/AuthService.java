package com.bank.service;

import com.bank.db.Database;
import com.bank.util.PasswordUtil;
import com.bank.util.Validator;

import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;

/** Registration, login (with lockout + audit) and password management. */
public class AuthService {
    private static final int MAX_FAILED = 5;
    private static final int LOCK_MINUTES = 15;
    private static final SecureRandom RNG = new SecureRandom();

    public String register(String name, String username, char[] password) throws BankException {
        if (!Validator.isValidName(name)) throw new BankException("Invalid name.");
        if (!Validator.isValidUsername(username))
            throw new BankException("Username must be 4-30 letters, digits or underscores.");
        if (!Validator.isStrongPassword(password))
            throw new BankException("Password needs 8+ characters with upper-case, lower-case and a digit.");

        String hash = PasswordUtil.hash(password);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int userId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users (name, username, password_hash) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, username);
                    ps.setString(3, hash);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); userId = k.getInt(1); }
                }
                String accountNo = createAccount(c, userId);
                c.commit();
                return accountNo;
            } catch (SQLIntegrityConstraintViolationException e) {
                c.rollback();
                throw new BankException("Username already taken.");
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BankException("Registration failed due to a database error.", e);
        }
    }

    private String createAccount(Connection c, int userId) throws SQLException {
        for (int attempt = 0; attempt < 10; attempt++) {
            long n = 1_000_000_000L + (long) (RNG.nextDouble() * 9_000_000_000L);
            String no = Long.toString(n);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO accounts (user_id, account_no) VALUES (?,?)")) {
                ps.setInt(1, userId);
                ps.setString(2, no);
                ps.executeUpdate();
                return no;
            } catch (SQLIntegrityConstraintViolationException dup) {
                // account number collision: retry
            }
        }
        throw new SQLException("Could not allocate account number");
    }

    /** Returns a Session on success; throws BankException with a generic message on failure. */
    public Session login(String username, char[] password) throws BankException {
        final String generic = "Invalid username or password.";
        try (Connection c = Database.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT u.user_id, u.name, u.password_hash, u.role, u.failed_attempts, u.locked_until, " +
                    "a.account_id, a.account_no FROM users u JOIN accounts a ON a.user_id = u.user_id " +
                    "WHERE u.username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        audit(c, username, false);
                        throw new BankException(generic);
                    }
                    int userId = rs.getInt("user_id");
                    Timestamp lock = rs.getTimestamp("locked_until");
                    if (lock != null && lock.toLocalDateTime().isAfter(LocalDateTime.now())) {
                        audit(c, username, false);
                        throw new BankException("Account temporarily locked. Try again later.");
                    }
                    if (!PasswordUtil.verify(password, rs.getString("password_hash"))) {
                        int failed = rs.getInt("failed_attempts") + 1;
                        recordFailure(c, userId, failed);
                        audit(c, username, false);
                        throw new BankException(generic);
                    }
                    try (PreparedStatement reset = c.prepareStatement(
                            "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?")) {
                        reset.setInt(1, userId);
                        reset.executeUpdate();
                    }
                    audit(c, username, true);
                    return new Session(userId, rs.getInt("account_id"), rs.getString("name"),
                            rs.getString("account_no"), rs.getString("role"));
                }
            }
        } catch (SQLException e) {
            throw new BankException("Login failed due to a database error.", e);
        }
    }

    private void recordFailure(Connection c, int userId, int failed) throws SQLException {
        boolean lock = failed >= MAX_FAILED;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = " +
                (lock ? "DATE_ADD(NOW(), INTERVAL " + LOCK_MINUTES + " MINUTE)" : "NULL") +
                " WHERE user_id = ?")) {
            ps.setInt(1, lock ? 0 : failed);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private void audit(Connection c, String username, boolean ok) throws SQLException {
        String safe = username == null ? "" : (username.length() > 30 ? username.substring(0, 30) : username);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO login_audit (username, success) VALUES (?,?)")) {
            ps.setString(1, safe);
            ps.setBoolean(2, ok);
            ps.executeUpdate();
        }
    }

    public void changePassword(Session s, char[] oldPw, char[] newPw) throws BankException {
        if (!Validator.isStrongPassword(newPw))
            throw new BankException("New password needs 8+ characters with upper-case, lower-case and a digit.");
        try (Connection c = Database.getConnection()) {
            String stored;
            try (PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?")) {
                ps.setInt(1, s.userId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new BankException("User not found.");
                    stored = rs.getString(1);
                }
            }
            if (!PasswordUtil.verify(oldPw, stored)) throw new BankException("Current password is incorrect.");
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                ps.setString(1, PasswordUtil.hash(newPw));
                ps.setInt(2, s.userId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new BankException("Password change failed due to a database error.", e);
        }
    }
}
