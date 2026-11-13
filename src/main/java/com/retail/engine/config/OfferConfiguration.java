package com.retail.engine.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable configuration bag holding all 10 system offer parameters.
 */
public final class OfferConfiguration {

    private final BigDecimal emiMinBill;
    private final Set<Integer> emiTenures;
    private final BigDecimal emiInterestPaPct;
    private final BigDecimal processingFee;
    private final BigDecimal processingFeeWaivedFrom;
    private final BigDecimal cashbackPct;
    private final BigDecimal cashbackCapPerCardCustomerMonth;
    private final BigDecimal bundleTvSoundbarOff;
    private final BigDecimal bundlePhoneCasePctOffCase;
    private final int returnWindowDays;

    public OfferConfiguration(BigDecimal emiMinBill,
                              Set<Integer> emiTenures,
                              BigDecimal emiInterestPaPct,
                              BigDecimal processingFee,
                              BigDecimal processingFeeWaivedFrom,
                              BigDecimal cashbackPct,
                              BigDecimal cashbackCapPerCardCustomerMonth,
                              BigDecimal bundleTvSoundbarOff,
                              BigDecimal bundlePhoneCasePctOffCase,
                              int returnWindowDays) {
        this.emiMinBill = Objects.requireNonNull(emiMinBill, "emiMinBill must not be null").setScale(2, RoundingMode.HALF_UP);
        this.emiTenures = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(emiTenures, "emiTenures must not be null")));
        this.emiInterestPaPct = Objects.requireNonNull(emiInterestPaPct, "emiInterestPaPct must not be null");
        this.processingFee = Objects.requireNonNull(processingFee, "processingFee must not be null").setScale(2, RoundingMode.HALF_UP);
        this.processingFeeWaivedFrom = Objects.requireNonNull(processingFeeWaivedFrom, "processingFeeWaivedFrom must not be null").setScale(2, RoundingMode.HALF_UP);
        this.cashbackPct = Objects.requireNonNull(cashbackPct, "cashbackPct must not be null");
        this.cashbackCapPerCardCustomerMonth = Objects.requireNonNull(cashbackCapPerCardCustomerMonth, "cashbackCapPerCardCustomerMonth must not be null").setScale(2, RoundingMode.HALF_UP);
        this.bundleTvSoundbarOff = Objects.requireNonNull(bundleTvSoundbarOff, "bundleTvSoundbarOff must not be null").setScale(2, RoundingMode.HALF_UP);
        this.bundlePhoneCasePctOffCase = Objects.requireNonNull(bundlePhoneCasePctOffCase, "bundlePhoneCasePctOffCase must not be null");
        if (returnWindowDays < 0) {
            throw new IllegalArgumentException("returnWindowDays cannot be negative: " + returnWindowDays);
        }
        this.returnWindowDays = returnWindowDays;
    }

    public BigDecimal getEmiMinBill() { return emiMinBill; }
    public Set<Integer> getEmiTenures() { return emiTenures; }
    public BigDecimal getEmiInterestPaPct() { return emiInterestPaPct; }
    public BigDecimal getProcessingFee() { return processingFee; }
    public BigDecimal getProcessingFeeWaivedFrom() { return processingFeeWaivedFrom; }
    public BigDecimal getCashbackPct() { return cashbackPct; }
    public BigDecimal getCashbackCapPerCardCustomerMonth() { return cashbackCapPerCardCustomerMonth; }
    public BigDecimal getBundleTvSoundbarOff() { return bundleTvSoundbarOff; }
    public BigDecimal getBundlePhoneCasePctOffCase() { return bundlePhoneCasePctOffCase; }
    public int getReturnWindowDays() { return returnWindowDays; }

    public boolean isTenurePermissible(int tenure) {
        return emiTenures.contains(tenure);
    }
}

