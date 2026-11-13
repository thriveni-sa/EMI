package com.retail.engine.model;

import java.util.Objects;

/**
 * Immutable entity representing a credit card issuer and its promotional eligibility flags.
 */
public final class CardIssuer {

    private final String issuerCode;
    private final String issuerName;
    private final boolean emiEligible;
    private final boolean cashbackEligible;

    public CardIssuer(String issuerCode, String issuerName, boolean emiEligible, boolean cashbackEligible) {
        this.issuerCode = Objects.requireNonNull(issuerCode, "issuerCode must not be null").trim();
        this.issuerName = Objects.requireNonNull(issuerName, "issuerName must not be null").trim();
        this.emiEligible = emiEligible;
        this.cashbackEligible = cashbackEligible;
    }

    public CardIssuer(String issuerCode, boolean emiEligible, boolean cashbackEligible) {
        this(issuerCode, issuerCode, emiEligible, cashbackEligible);
    }

    public String getIssuerCode() {
        return issuerCode;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public boolean isEmiEligible() {
        return emiEligible;
    }

    public boolean isCashbackEligible() {
        return cashbackEligible;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CardIssuer that)) return false;
        return emiEligible == that.emiEligible &&
               cashbackEligible == that.cashbackEligible &&
               issuerCode.equalsIgnoreCase(that.issuerCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuerCode.toUpperCase());
    }

    @Override
    public String toString() {
        return "CardIssuer{" +
                "issuerCode='" + issuerCode + '\'' +
                ", issuerName='" + issuerName + '\'' +
                ", emiEligible=" + emiEligible +
                ", cashbackEligible=" + cashbackEligible +
                '}';
    }
}

