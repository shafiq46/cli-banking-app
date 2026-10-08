package com.bank.ui;

import com.bank.service.*;
import com.bank.util.Validator;

import java.io.Console;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/** Menu-driven CLI. Holds the Session only while the user is logged in. */
public class ConsoleUI {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Scanner in = new Scanner(System.in);
    private final AuthService auth = new AuthService();
    private final BankService bank = new BankService();

    public void run() {
        System.out.println("=== Secure Simple CLI Banking Application (educational prototype) ===");
        boolean running = true;
        while (running) {
            System.out.println("\n1. Register\n2. Login\n3. Exit");
            switch (prompt("Choose: ")) {
                case "1" -> register();
                case "2" -> {
                    Session s = login();
                    if (s != null) bankingMenu(s);
                }
                case "3" -> running = false;
                default -> System.out.println("Invalid choice.");
            }
        }
        System.out.println("Goodbye.");
    }

    private void register() {
        String name = prompt("Full name: ");
        String username = prompt("Username: ");
        char[] pw = readPassword("Password: ");
        char[] confirm = readPassword("Confirm password: ");
        try {
            if (!Arrays.equals(pw, confirm)) throw new BankException("Passwords do not match.");
            String acct = auth.register(name, username, pw);
            System.out.println("Registered. Your account number is " + acct);
        } catch (BankException e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            Arrays.fill(pw, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    private Session login() {
        String username = prompt("Username: ");
        char[] pw = readPassword("Password: ");
        try {
            Session s = auth.login(username, pw);
            System.out.println("Welcome, " + s.name() + "!");
            return s;
        } catch (BankException e) {
            System.out.println("Error: " + e.getMessage());
            return null;
        } finally {
            Arrays.fill(pw, '\0');
        }
    }

    private void bankingMenu(Session s) {
        while (true) {
            System.out.println("\n--- Banking Menu (account " + s.accountNo() + ") ---");
            System.out.println("1. Balance\n2. Deposit\n3. Withdraw\n4. Transfer\n5. Transaction history\n6. Change password");
            if (s.isAdmin()) System.out.println("7. [Admin] List all accounts");
            System.out.println("0. Logout");
            String choice = prompt("Choose: ");
            try {
                switch (choice) {
                    case "1" -> System.out.println("Balance: " + bank.getBalance(s));
                    case "2" -> {
                        BigDecimal a = amount();
                        if (a != null) System.out.println("Deposited. New balance: " + bank.deposit(s, a));
                    }
                    case "3" -> {
                        BigDecimal a = amount();
                        if (a != null) System.out.println("Withdrawn. New balance: " + bank.withdraw(s, a));
                    }
                    case "4" -> {
                        String dest = prompt("Destination account number: ");
                        BigDecimal a = amount();
                        if (a != null) System.out.println("Transferred. New balance: " + bank.transfer(s, dest, a));
                    }
                    case "5" -> showHistory(bank.history(s, 20));
                    case "6" -> changePassword(s);
                    case "7" -> {
                        if (s.isAdmin()) bank.listAllAccounts(s).forEach(System.out::println);
                        else System.out.println("Invalid choice.");
                    }
                    case "0" -> {
                        System.out.println("Logged out.");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            } catch (BankException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void changePassword(Session s) throws BankException {
        char[] old = readPassword("Current password: ");
        char[] nw = readPassword("New password: ");
        try {
            auth.changePassword(s, old, nw);
            System.out.println("Password updated.");
        } finally {
            Arrays.fill(old, '\0');
            Arrays.fill(nw, '\0');
        }
    }

    private void showHistory(List<TransactionRecord> list) {
        if (list.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        System.out.printf("%-20s %-13s %12s %-12s %12s%n", "Time", "Type", "Amount", "Other acct", "Balance");
        for (TransactionRecord t : list) {
            System.out.printf("%-20s %-13s %12s %-12s %12s%n", t.time().format(FMT), t.type(),
                    t.amount(), t.counterparty() == null ? "-" : t.counterparty(), t.balanceAfter());
        }
    }

    private BigDecimal amount() {
        BigDecimal a = Validator.parseAmount(prompt("Amount: "));
        if (a == null) System.out.println("Invalid amount (positive, max 2 decimals, up to 1,000,000).");
        return a;
    }

    private String prompt(String msg) {
        System.out.print(msg);
        return in.hasNextLine() ? in.nextLine().trim() : "3";
    }

    private char[] readPassword(String msg) {
        Console console = System.console();
        if (console != null) return console.readPassword(msg);
        System.out.print(msg + "(input visible) ");
        return (in.hasNextLine() ? in.nextLine() : "").toCharArray();
    }
}
