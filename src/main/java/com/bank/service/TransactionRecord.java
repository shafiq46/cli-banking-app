package com.bank.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRecord(long id, String type, BigDecimal amount, String counterparty,
                                BigDecimal balanceAfter, LocalDateTime time) {}
