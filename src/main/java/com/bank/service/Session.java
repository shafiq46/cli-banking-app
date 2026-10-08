package com.bank.service;

/** Authenticated session state. Discarded on logout. */
public record Session(int userId, int accountId, String name, String accountNo, String role) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
