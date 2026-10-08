package com.bank.service;

import com.bank.db.Database;
import com.bank.util.Validator;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Core banking operations. Every balance change is a single DB transaction with row locks. */
public class BankService {

    public BigDecimal getBalance(Session s) throws BankException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT balance FROM accounts WHERE account_id = ?")) {
            ps.setInt(1, s.accountId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BankException("Account not found.");
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new BankException("Could not read balance.", e);
        }
    }

    public BigDecimal deposit(Session s, BigDecimal amount) throws BankException {
        requirePositive(amount);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                BigDecimal bal = lockBalance(c, s.accountId()).add(amount);
                setBalance(c, s.accountId(), bal);
                log(c, s.accountId(), "DEPOSIT", amount, null, bal);
                c.commit();
                return bal;
            } catch (SQLException | RuntimeException | BankException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BankException("Deposit failed due to a database error.", e);
        }
    }

    public BigDecimal withdraw(Session s, BigDecimal amount) throws BankException {
        requirePositive(amount);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                BigDecimal current = lockBalance(c, s.accountId());
                if (current.compareTo(amount) < 0) throw new BankException("Insufficient balance.");
                BigDecimal bal = current.subtract(amount);
                setBalance(c, s.accountId(), bal);
                log(c, s.accountId(), "WITHDRAWAL", amount, null, bal);
                c.commit();
                return bal;
            } catch (SQLException | RuntimeException | BankException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BankException("Withdrawal failed due to a database error.", e);
        }
    }

    public BigDecimal transfer(Session s, String destAccountNo, BigDecimal amount) throws BankException {
        requirePositive(amount);
        if (!Validator.isValidAccountNo(destAccountNo)) throw new BankException("Invalid destination account number.");
        if (destAccountNo.equals(s.accountNo())) throw new BankException("Cannot transfer to your own account.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int destId;
                try (PreparedStatement ps = c.prepareStatement("SELECT account_id FROM accounts WHERE account_no = ?")) {
                    ps.setString(1, destAccountNo);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BankException("Destination account does not exist.");
                        destId = rs.getInt(1);
                    }
                }
                // Lock in ascending id order to avoid deadlocks between concurrent transfers.
                BigDecimal srcBal, dstBal;
                if (s.accountId() < destId) {
                    srcBal = lockBalance(c, s.accountId());
                    dstBal = lockBalance(c, destId);
                } else {
                    dstBal = lockBalance(c, destId);
                    srcBal = lockBalance(c, s.accountId());
                }
                if (srcBal.compareTo(amount) < 0) throw new BankException("Insufficient balance.");
                BigDecimal newSrc = srcBal.subtract(amount);
                BigDecimal newDst = dstBal.add(amount);
                setBalance(c, s.accountId(), newSrc);
                setBalance(c, destId, newDst);
                log(c, s.accountId(), "TRANSFER_OUT", amount, destAccountNo, newSrc);
                log(c, destId, "TRANSFER_IN", amount, s.accountNo(), newDst);
                c.commit();
                return newSrc;
            } catch (SQLException | RuntimeException | BankException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new BankException("Transfer failed due to a database error; no money was moved.", e);
        }
    }

    public List<TransactionRecord> history(Session s, int limit) throws BankException {
        List<TransactionRecord> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT transaction_id, type, amount, counterparty_acct, balance_after, timestamp " +
                     "FROM transactions WHERE account_id = ? ORDER BY timestamp DESC, transaction_id DESC LIMIT ?")) {
            ps.setInt(1, s.accountId());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TransactionRecord(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3),
                            rs.getString(4), rs.getBigDecimal(5), rs.getTimestamp(6).toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new BankException("Could not load transaction history.", e);
        }
        return out;
    }

    /** ADMIN only: list all accounts (never exposes password hashes). */
    public List<String> listAllAccounts(Session s) throws BankException {
        if (!s.isAdmin()) throw new BankException("Access denied.");
        List<String> rows = new ArrayList<>();
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT u.username, u.name, a.account_no, a.balance FROM users u " +
                     "JOIN accounts a ON a.user_id = u.user_id ORDER BY a.account_id")) {
            while (rs.next()) {
                rows.add(String.format("%-15s %-25s %-12s %12s",
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)));
            }
        } catch (SQLException e) {
            throw new BankException("Could not list accounts.", e);
        }
        return rows;
    }

    // ---- helpers ----
    private void requirePositive(BigDecimal a) throws BankException {
        if (a == null || a.signum() <= 0) throw new BankException("Amount must be a positive number.");
    }

    private BigDecimal lockBalance(Connection c, int accountId) throws SQLException, BankException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM accounts WHERE account_id = ? FOR UPDATE")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BankException("Account not found.");
                return rs.getBigDecimal(1);
            }
        }
    }

    private void setBalance(Connection c, int accountId, BigDecimal bal) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = ? WHERE account_id = ?")) {
            ps.setBigDecimal(1, bal);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    private void log(Connection c, int accountId, String type, BigDecimal amt, String cp, BigDecimal after) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions (account_id, type, amount, counterparty_acct, balance_after) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, accountId);
            ps.setString(2, type);
            ps.setBigDecimal(3, amt);
            ps.setString(4, cp);
            ps.setBigDecimal(5, after);
            ps.executeUpdate();
        }
    }
}
