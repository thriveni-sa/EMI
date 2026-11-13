package com.retail.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Entity representing a line item in the monthly cashback reconciliation liability report.
 * Aligns directly with the 7 columns of cashback_report.csv.
 */
public final class CashbackReport {

    private final String month; // YYYY-MM
    private final String customerId;
    private final String cardIssuer;
    private final BigDecimal cashbackGranted;
    private final BigDecimal cashbackReversed;
    private final BigDecimal netLiability;
    private final BigDecimal capRemaining;

    public CashbackReport(String month, String customerId, String cardIssuer,
                          BigDecimal cashbackGranted, BigDecimal cashbackReversed,
                          BigDecimal netLiability, BigDecimal capRemaining) {
        this.month = Objects.requireNonNull(month, "month must not be null").trim();
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null").trim();
        this.cardIssuer = Objects.requireNonNull(cardIssuer, "cardIssuer must not be null").trim();
        this.cashbackGranted = scale(cashbackGranted);
        this.cashbackReversed = scale(cashbackReversed);
        this.netLiability = scale(netLiability);
        this.capRemaining = scale(capRemaining);
    }

    private static BigDecimal scale(BigDecimal val) {
        return (val == null) ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : val.setScale(2, RoundingMode.HALF_UP);
    }

    public String getMonth() { return month; }
    public String getCustomerId() { return customerId; }
    public String getCardIssuer() { return cardIssuer; }
    public BigDecimal getCashbackGranted() { return cashbackGranted; }
    public BigDecimal getCashbackReversed() { return cashbackReversed; }
    public BigDecimal getNetLiability() { return netLiability; }
    public BigDecimal getCapRemaining() { return capRemaining; }

    @Override
    public String toString() {
        return "CashbackReport{" +
                "month='" + month + '\'' +
                ", customerId='" + customerId + '\'' +
                ", cardIssuer='" + cardIssuer + '\'' +
                ", netLiability=" + netLiability +
                ", capRemaining=" + capRemaining +
                '}';
    }
}

