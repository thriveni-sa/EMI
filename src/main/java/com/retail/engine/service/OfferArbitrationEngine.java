package com.retail.engine.service;

import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fiduciary arbitration engine determining the optimal promotional offer for retail customers.
 * Enforces mutual exclusivity (BR-16), value-maximization (BR-17), deterministic tie-breaking (BR-18),
 * and transparent rationale breakdowns.
 */
public class OfferArbitrationEngine {

    public record RecommendationBreakdown(
            String billId,
            BigDecimal grossAmount,
            BigDecimal bundleDiscount,
            BigDecimal netBillAmount,
            boolean emiAvailable,
            BigDecimal interestSaved,
            BigDecimal processingFee,
            boolean cashbackEligible,
            BigDecimal awardedCashback,
            OfferType recommendedOption,
            BigDecimal benefitAmount,
            String rationale
    ) {}

    private final BundleDiscountService bundleService;
    private final EmiCalculatorService emiService;
    private final CashbackService cashbackService;

    public OfferArbitrationEngine(BundleDiscountService bundleService,
                                  EmiCalculatorService emiService,
                                  CashbackService cashbackService) {
        this.bundleService = bundleService;
        this.emiService = emiService;
        this.cashbackService = cashbackService;
    }

    public OfferResult evaluateBill(Bill bill, List<BillItem> items, CardIssuer card, OfferConfiguration config) {
        // Step 1: Gross Amount
        BigDecimal grossAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BillItem item : items) {
            grossAmount = grossAmount.add(item.getPrice()).setScale(2, RoundingMode.HALF_UP);
        }

        // Step 2 & 3: Bundle Discount & Net Amount (evaluated BEFORE promotional tracks)
        BundleDiscount bundleDiscount = bundleService.calculateBundleDiscount(items, config);
        BigDecimal netBillAmount = grossAmount.subtract(bundleDiscount.getTotalDiscount()).setScale(2, RoundingMode.HALF_UP);

        // Step 4: Evaluate EMI (independent calculation)
        EmiCalculatorService.EmiResult emi = emiService.evaluateEmi(bill, netBillAmount, card, config);

        // Step 5: Evaluate Cashback (independent calculation, considers monthly cap)
        CashbackService.CashbackResult cb = cashbackService.evaluateCashback(bill, netBillAmount, card, config);

        // Step 6: Arbitrate Recommendation (BR-16, BR-17, BR-18)
        OfferType recommendedOption;
        BigDecimal benefitAmount;
        String rationale;

        boolean emiAvailable = emi.available();
        boolean cbAvailable = cb.eligible() && cb.awardedCashback().compareTo(BigDecimal.ZERO) > 0;

        if (emiAvailable && cbAvailable) {
            // Note: Per specification, processing fee is kept separate and NOT subtracted from interest saved
            int comp = emi.interestSaved().compareTo(cb.awardedCashback());
            if (comp > 0) {
                // BR-17: EMI yields higher actual rupee benefit
                recommendedOption = OfferType.EMI;
                benefitAmount = emi.interestSaved();
                rationale = String.format("EMI recommended: interest saved (Rs %s) exceeds cashback (Rs %s)",
                        emi.interestSaved().toPlainString(), cb.awardedCashback().toPlainString());
            } else if (comp < 0) {
                // BR-17: Cashback yields higher actual rupee benefit
                recommendedOption = OfferType.CASHBACK;
                benefitAmount = cb.awardedCashback();
                cashbackService.commitCashback(bill, cb.awardedCashback());
                rationale = String.format("CASHBACK recommended: cashback (Rs %s) exceeds interest saved (Rs %s)",
                        cb.awardedCashback().toPlainString(), emi.interestSaved().toPlainString());
            } else {
                // BR-18 & EC-04: Exact tie breaks strictly in favor of CASHBACK
                recommendedOption = OfferType.CASHBACK;
                benefitAmount = cb.awardedCashback();
                cashbackService.commitCashback(bill, cb.awardedCashback());
                rationale = String.format("CASHBACK recommended: exact benefit tie (Rs %s) breaks to CASHBACK per BR-18",
                        cb.awardedCashback().toPlainString());
            }
        } else if (emiAvailable) {
            // Sole qualifying offer: EMI
            recommendedOption = OfferType.EMI;
            benefitAmount = emi.interestSaved();
            rationale = String.format("EMI recommended: sole qualifying promotion (interest saved Rs %s)",
                    emi.interestSaved().toPlainString());
        } else if (cbAvailable) {
            // Sole qualifying offer: CASHBACK
            recommendedOption = OfferType.CASHBACK;
            benefitAmount = cb.awardedCashback();
            cashbackService.commitCashback(bill, cb.awardedCashback());
            rationale = String.format("CASHBACK recommended: sole qualifying promotion (awarded cashback Rs %s)",
                    cb.awardedCashback().toPlainString());
        } else {
            // Fallback: Neither promotional track qualifies
            recommendedOption = OfferType.NONE;
            benefitAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            rationale = "NONE: neither promotional option qualifies";
        }

        return new OfferResult(
                bill.getBillId(),
                bill.getCustomerId(),
                bill.getBillDate(),
                bill.getCardIssuer(),
                grossAmount,
                bundleDiscount.getTotalDiscount(),
                netBillAmount,
                emi.available(),
                emi.tenureMonths(),
                emi.instalment(),
                emi.lastInstalment(),
                emi.processingFee(),
                emi.interestSaved(),
                cb.eligible(),
                cb.awardedCashback(),
                recommendedOption,
                benefitAmount,
                rationale
        );
    }

    public RecommendationBreakdown getRecommendationBreakdown(Bill bill, List<BillItem> items, CardIssuer card, OfferConfiguration config) {
        OfferResult result = evaluateBill(bill, items, card, config);
        return new RecommendationBreakdown(
                result.getBillId(),
                result.getGrossAmount(),
                result.getBundleDiscount(),
                result.getBillAmount(),
                result.isEmiAvailable(),
                result.getInterestSaved(),
                result.getProcessingFee(),
                result.isCashbackEligible(),
                result.getCashbackAmount(),
                result.getRecommendedOption(),
                result.getBenefitAmount(),
                result.getRationale()
        );
    }
}
