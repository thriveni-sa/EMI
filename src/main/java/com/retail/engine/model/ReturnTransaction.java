package com.retail.engine.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable entity representing a customer item return transaction.
 */
public final class ReturnTransaction {

    private final String returnId;
    private final String billId;
    private final int lineNo;
    private final LocalDate returnDate;

    public ReturnTransaction(String returnId, String billId, int lineNo, LocalDate returnDate) {
        this.returnId = Objects.requireNonNull(returnId, "returnId must not be null").trim();
        this.billId = Objects.requireNonNull(billId, "billId must not be null").trim();
        if (lineNo < 1) {
            throw new IllegalArgumentException("lineNo must be >= 1");
        }
        this.lineNo = lineNo;
        this.returnDate = Objects.requireNonNull(returnDate, "returnDate must not be null");
    }

    public String getReturnId() {
        return returnId;
    }

    public String getBillId() {
        return billId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReturnTransaction that)) return false;
        return returnId.equals(that.returnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnId);
    }

    @Override
    public String toString() {
        return "ReturnTransaction{" +
                "returnId='" + returnId + '\'' +
                ", billId='" + billId + '\'' +
                ", lineNo=" + lineNo +
                ", returnDate=" + returnDate +
                '}';
    }
}

