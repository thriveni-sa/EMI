package com.retail.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable entity representing a single instalment entry in the EMI schedule report.
 */
public final class EmiSchedule {

    private final String billId;
    private final int instalmentNo;
    private final BigDecimal amount;

    public EmiSchedule(String billId, int instalmentNo, BigDecimal amount) {
        this.billId = Objects.requireNonNull(billId, "billId must not be null").trim();
        if (instalmentNo < 1) {
            throw new IllegalArgumentException("instalmentNo must be >= 1");
        }
        this.instalmentNo = instalmentNo;
        this.amount = Objects.requireNonNull(amount, "amount must not be null").setScale(2, RoundingMode.HALF_UP);
    }

    public String getBillId() {
        return billId;
    }

    public int getInstalmentNo() {
        return instalmentNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmiSchedule that)) return false;
        return instalmentNo == that.instalmentNo && billId.equals(that.billId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(billId, instalmentNo);
    }

    @Override
    public String toString() {
        return "EmiSchedule{" +
                "billId='" + billId + '\'' +
                ", instalmentNo=" + instalmentNo +
                ", amount=" + amount +
                '}';
    }
}

