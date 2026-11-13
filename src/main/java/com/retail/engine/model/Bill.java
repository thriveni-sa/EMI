package com.retail.engine.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable entity representing a retail purchase bill header.
 */
public final class Bill {

    private final String billId;
    private final String customerId;
    private final LocalDate billDate;
    private final String cardIssuer;
    private final Integer tenureMonths; // null if customer did not opt for EMI

    public Bill(String billId, String customerId, LocalDate billDate, String cardIssuer, Integer tenureMonths) {
        this.billId = Objects.requireNonNull(billId, "billId must not be null").trim();
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null").trim();
        this.billDate = Objects.requireNonNull(billDate, "billDate must not be null");
        this.cardIssuer = Objects.requireNonNull(cardIssuer, "cardIssuer must not be null").trim();
        this.tenureMonths = tenureMonths;
    }

    public String getBillId() {
        return billId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public String getCardIssuer() {
        return cardIssuer;
    }

    public Optional<Integer> getTenureMonths() {
        return Optional.ofNullable(tenureMonths);
    }

    public boolean hasTenureRequested() {
        return tenureMonths != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bill bill)) return false;
        return billId.equals(bill.billId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(billId);
    }

    @Override
    public String toString() {
        return "Bill{" +
                "billId='" + billId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", billDate=" + billDate +
                ", cardIssuer='" + cardIssuer + '\'' +
                ", tenureMonths=" + tenureMonths +
                '}';
    }
}

