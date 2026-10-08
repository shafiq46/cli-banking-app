package com.bank.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Input validation for type, range and business rules. */
public final class Validator {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{4,30}$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z .'-]{1,99}$");
    private static final Pattern ACCOUNT_NO = Pattern.compile("^[0-9]{10}$");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    private Validator() {}

    public static boolean isValidUsername(String s) { return s != null && USERNAME.matcher(s).matches(); }
    public static boolean isValidName(String s) { return s != null && NAME.matcher(s.trim()).matches(); }
    public static boolean isValidAccountNo(String s) { return s != null && ACCOUNT_NO.matcher(s).matches(); }

    /** At least 8 chars with an upper-case, lower-case letter and a digit. */
    public static boolean isStrongPassword(char[] p) {
        if (p == null || p.length < 8) return false;
        boolean up = false, low = false, dig = false;
        for (char c : p) {
            if (Character.isUpperCase(c)) up = true;
            else if (Character.isLowerCase(c)) low = true;
            else if (Character.isDigit(c)) dig = true;
        }
        return up && low && dig;
    }

    /** Parses a monetary amount: positive, max 2 decimals, within limit. Returns null if invalid. */
    public static BigDecimal parseAmount(String s) {
        try {
            BigDecimal a = new BigDecimal(s.trim());
            if (a.signum() <= 0 || a.scale() > 2 || a.compareTo(MAX_AMOUNT) > 0) return null;
            return a.setScale(2);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
