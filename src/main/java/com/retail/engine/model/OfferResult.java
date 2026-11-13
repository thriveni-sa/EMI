package com.retail.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Value object representing the comprehensive offer evaluation result for a bill.
 * Aligns directly with the 17 columns required for offer_summary.csv.
 */
public final class OfferResult {

    private final String billId;
    private final String customerId;
    private final LocalDate billDate;
    private final String cardIssuer;
    private final BigDecimal grossAmount;
    private final BigDecimal bundleDiscount;
    private final BigDecimal billAmount;
    private final boolean emiAvailable;
    private final Integer tenureMonths;
    private final BigDecimal instalment;
    private final BigDecimal lastInstalment;
    private final BigDecimal processingFee;
    private final BigDecimal interestSaved;
    private final boolean cashbackEligible;
    private final BigDecimal cashbackAmount;
    private final OfferType recommendedOption;
    private final BigDecimal benefitAmount;
    private final String rationale;

    public OfferResult(String billId, String customerId, LocalDate billDate, String cardIssuer,
                       BigDecimal grossAmount, BigDecimal bundleDiscount, BigDecimal billAmount,
                       boolean emiAvailable, Integer tenureMonths,
                       BigDecimal instalment, BigDecimal lastInstalment, BigDecimal processingFee,
                       BigDecimal interestSaved, boolean cashbackEligible, BigDecimal cashbackAmount,
                       OfferType recommendedOption, BigDecimal benefitAmount, String rationale) {
        this.billId = Objects.requireNonNull(billId, "billId must not be null").trim();
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null").trim();
        this.billDate = Objects.requireNonNull(billDate, "billDate must not be null");
        this.cardIssuer = Objects.requireNonNull(cardIssuer, "cardIssuer must not be null").trim();
        this.grossAmount = scale(grossAmount);
        this.bundleDiscount = scale(bundleDiscount);
        this.billAmount = scale(billAmount);
        this.emiAvailable = emiAvailable;
        this.tenureMonths = tenureMonths;
        this.instalment = scale(instalment);
        this.lastInstalment = scale(lastInstalment);
        this.processingFee = scale(processingFee);
        this.interestSaved = scale(interestSaved);
        this.cashbackEligible = cashbackEligible;
        this.cashbackAmount = scale(cashbackAmount);
        this.recommendedOption = Objects.requireNonNull(recommendedOption, "recommendedOption must not be null");
        this.benefitAmount = scale(benefitAmount);
        this.rationale = (rationale == null) ? "" : rationale.trim();
    }

    public OfferResult(String billId, String customerId, LocalDate billDate, String cardIssuer,
                       BigDecimal grossAmount, BigDecimal bundleDiscount, BigDecimal billAmount,
                       boolean emiAvailable, Integer tenureMonths,
                       BigDecimal instalment, BigDecimal lastInstalment, BigDecimal processingFee,
                       BigDecimal interestSaved, boolean cashbackEligible, BigDecimal cashbackAmount,
                       OfferType recommendedOption, BigDecimal benefitAmount) {
        this(billId, customerId, billDate, cardIssuer, grossAmount, bundleDiscount, billAmount,
                emiAvailable, tenureMonths, instalment, lastInstalment, processingFee,
                interestSaved, cashbackEligible, cashbackAmount, recommendedOption, benefitAmount, "");
    }

    private static BigDecimal scale(BigDecimal val) {
        return (val == null) ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : val.setScale(2, RoundingMode.HALF_UP);
    }

    public String getBillId() { return billId; }
    public String getCustomerId() { return customerId; }
    public LocalDate getBillDate() { return billDate; }
    public String getCardIssuer() { return cardIssuer; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getBundleDiscount() { return bundleDiscount; }
    public BigDecimal getBillAmount() { return billAmount; }
    public BigDecimal getNetBillAmount() { return billAmount; }
    public boolean isEmiAvailable() { return emiAvailable; }
    public Integer getTenureMonths() { return tenureMonths; }
    public BigDecimal getInstalment() { return instalment; }
    public BigDecimal getLastInstalment() { return lastInstalment; }
    public BigDecimal getProcessingFee() { return processingFee; }
    public BigDecimal getInterestSaved() { return interestSaved; }
    public boolean isCashbackEligible() { return cashbackEligible; }
    public BigDecimal getCashbackAmount() { return cashbackAmount; }
    public OfferType getRecommendedOption() { return recommendedOption; }
    public BigDecimal getBenefitAmount() { return benefitAmount; }
    public String getRationale() { return rationale; }

    @Override
    public String toString() {
        return "OfferResult{" +
                "billId='" + billId + '\'' +
                ", recommendedOption=" + recommendedOption +
                ", benefitAmount=" + benefitAmount +
                ", billAmount=" + billAmount +
                '}';
    }
}

