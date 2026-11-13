package com.retail.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Domain entity recording a customer cashback grant or reversal event.
 */
public final class CashbackTransaction {

    public enum TransactionType {
        GRANT,
        REVERSAL
    }

    private final String billId;
    private final String customerId;
    private final String cardIssuer;
    private final LocalDate transactionDate;
    private final String ledgerYearMonth;
    private final TransactionType type;
    private final BigDecimal amount;

    public CashbackTransaction(String billId, String customerId, String cardIssuer,
                               LocalDate transactionDate, String ledgerYearMonth,
                               TransactionType type, BigDecimal amount) {
        this.billId = Objects.requireNonNull(billId, "billId must not be null").trim();
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null").trim();
        this.cardIssuer = Objects.requireNonNull(cardIssuer, "cardIssuer must not be null").trim();
        this.transactionDate = Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        this.ledgerYearMonth = ledgerYearMonth != null ? ledgerYearMonth.trim() : String.format("%04d-%02d", transactionDate.getYear(), transactionDate.getMonthValue());
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null").setScale(2, RoundingMode.HALF_UP);
    }

    public CashbackTransaction(String billId, String customerId, String cardIssuer,
                               LocalDate transactionDate, TransactionType type, BigDecimal amount) {
        this(billId, customerId, cardIssuer, transactionDate, null, type, amount);
    }

    public String getBillId() {
        return billId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCardIssuer() {
        return cardIssuer;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getLedgerYearMonth() {
        return ledgerYearMonth;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getYearMonth() {
        return ledgerYearMonth;
    }

    @Override
    public String toString() {
        return "CashbackTransaction{" +
                "billId='" + billId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", cardIssuer='" + cardIssuer + '\'' +
                ", transactionDate=" + transactionDate +
                ", type=" + type +
                ", amount=" + amount +
                '}';
    }
}

