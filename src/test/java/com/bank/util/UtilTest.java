package com.bank.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class UtilTest {
    @Test void hashVerifiesAndIsSalted() {
        String h1 = PasswordUtil.hash("Secret123".toCharArray());
        String h2 = PasswordUtil.hash("Secret123".toCharArray());
        assertNotEquals(h1, h2);
        assertTrue(PasswordUtil.verify("Secret123".toCharArray(), h1));
        assertFalse(PasswordUtil.verify("wrong".toCharArray(), h1));
    }
    @Test void amountValidation() {
        assertEquals(new BigDecimal("10.50"), Validator.parseAmount("10.5"));
        assertNull(Validator.parseAmount("-5"));
        assertNull(Validator.parseAmount("0"));
        assertNull(Validator.parseAmount("1.234"));
        assertNull(Validator.parseAmount("abc"));
    }
    @Test void fieldValidation() {
        assertTrue(Validator.isValidUsername("praveen_01"));
        assertFalse(Validator.isValidUsername("a b"));
        assertTrue(Validator.isValidAccountNo("1234567890"));
        assertFalse(Validator.isValidAccountNo("12345"));
        assertTrue(Validator.isStrongPassword("Abcdef12".toCharArray()));
        assertFalse(Validator.isStrongPassword("abcdefgh".toCharArray()));
    }
}
