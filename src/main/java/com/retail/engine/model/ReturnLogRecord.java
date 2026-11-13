package com.retail.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Value object representing an audited return transaction log entry.
 * Aligns directly with the 7 columns of returns_log.csv.
 */
public final class ReturnLogRecord {

    private final String returnId;
    private final String billId;
    private final int lineNo;
    private final LocalDate returnDate;
    private final int daysAfterBill;
    private final boolean withinWindow;
    private final BigDecimal cashbackReversed;

    public ReturnLogRecord(String returnId, String billId, int lineNo, LocalDate returnDate,
                           int daysAfterBill, boolean withinWindow, BigDecimal cashbackReversed) {
        this.returnId = Objects.requireNonNull(returnId, "returnId must not be null").trim();
        this.billId = Objects.requireNonNull(billId, "billId must not be null").trim();
        this.lineNo = lineNo;
        this.returnDate = Objects.requireNonNull(returnDate, "returnDate must not be null");
        this.daysAfterBill = daysAfterBill;
        this.withinWindow = withinWindow;
        this.cashbackReversed = (cashbackReversed == null) ?
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) :
                cashbackReversed.setScale(2, RoundingMode.HALF_UP);
    }

    public String getReturnId() { return returnId; }
    public String getBillId() { return billId; }
    public int getLineNo() { return lineNo; }
    public LocalDate getReturnDate() { return returnDate; }
    public int getDaysAfterBill() { return daysAfterBill; }
    public boolean isWithinWindow() { return withinWindow; }
    public BigDecimal getCashbackReversed() { return cashbackReversed; }

    @Override
    public String toString() {
        return "ReturnLogRecord{" +
                "returnId='" + returnId + '\'' +
                ", billId='" + billId + '\'' +
                ", daysAfterBill=" + daysAfterBill +
                ", withinWindow=" + withinWindow +
                ", cashbackReversed=" + cashbackReversed +
                '}';
    }
}

