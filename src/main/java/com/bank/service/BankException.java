package com.bank.service;

/** Business-rule or validation failure with a user-safe message. */
public class BankException extends Exception {
    public BankException(String message) { super(message); }
    public BankException(String message, Throwable cause) { super(message, cause); }
}
