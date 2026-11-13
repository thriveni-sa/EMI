package com.retail.engine.service;

import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.BillItem;
import com.retail.engine.model.BundleDiscount;
import com.retail.engine.model.ProductCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Domain calculation engine evaluating item bundle discounts prior to financing or cashback.
 *
 * Business Rules Enforced:
 * - BR-13 (TV+Soundbar): Purchasing TV and SOUNDBAR on the same bill qualifies for a flat Rs 2,000.00 discount.
 * - BR-14 (Phone+Case): Purchasing PHONE and CASE on the same bill awards 50% discount on each CASE line item.
 * - BR-15 (Pre-Financing Baseline): Bundle discounts apply before No-Cost EMI fee waiver, interest subvention, or cashback calculations.
 */
public class BundleDiscountService {

    public BundleDiscount calculateBundleDiscount(List<BillItem> items, OfferConfiguration config) {
        if (items == null || items.isEmpty() || config == null) {
            return BundleDiscount.ZERO;
        }

        BigDecimal grossBillAmount = BigDecimal.ZERO;
        boolean hasTv = false;
        boolean hasSoundbar = false;
        boolean hasPhone = false;

        BigDecimal tvSoundbarCombinedPrice = BigDecimal.ZERO;
        Map<Integer, BigDecimal> lineDiscounts = new LinkedHashMap<>();

        // First pass: detect presence of bundle trigger categories and sum prices
        for (BillItem item : items) {
            grossBillAmount = grossBillAmount.add(item.getPrice());
            ProductCategory cat = item.getCategory();

            if (cat == ProductCategory.TV) {
                hasTv = true;
                tvSoundbarCombinedPrice = tvSoundbarCombinedPrice.add(item.getPrice());
            } else if (cat == ProductCategory.SOUNDBAR) {
                hasSoundbar = true;
                tvSoundbarCombinedPrice = tvSoundbarCombinedPrice.add(item.getPrice());
            } else if (cat == ProductCategory.PHONE) {
                hasPhone = true;
            }
        }

        // 1. Evaluate TV + Soundbar Bundle (Flat discount)
        BigDecimal tvSoundbarDiscount = BigDecimal.ZERO;
        if (hasTv && hasSoundbar) {
            BigDecimal configuredFlatOff = config.getBundleTvSoundbarOff();
            // Invariant: Discount can never exceed the total price of the bundle components
            tvSoundbarDiscount = configuredFlatOff.min(tvSoundbarCombinedPrice).setScale(2, RoundingMode.HALF_UP);
        }

        // 2. Evaluate Phone + Case Bundle (50% off each companion case line item)
        BigDecimal phoneCaseDiscount = BigDecimal.ZERO;
        if (hasPhone) {
            BigDecimal caseRate = config.getBundlePhoneCasePctOffCase()
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

            for (BillItem item : items) {
                if (item.getCategory() == ProductCategory.CASE) {
                    BigDecimal itemPrice = item.getPrice();
                    // 50% of the individual case price, capped at item price
                    BigDecimal lineDiscount = itemPrice.multiply(caseRate).setScale(2, RoundingMode.HALF_UP);
                    lineDiscount = lineDiscount.min(itemPrice);

                    phoneCaseDiscount = phoneCaseDiscount.add(lineDiscount);
                    lineDiscounts.put(item.getLineNo(), lineDiscount);
                }
            }
        }

        // Overall bill ceiling invariant: Total bundle discount cannot exceed gross bill amount
        BigDecimal totalPotentialDiscount = tvSoundbarDiscount.add(phoneCaseDiscount);
        if (totalPotentialDiscount.compareTo(grossBillAmount) > 0) {
            // Cap at gross bill amount
            BigDecimal excess = totalPotentialDiscount.subtract(grossBillAmount);
            if (tvSoundbarDiscount.compareTo(excess) >= 0) {
                tvSoundbarDiscount = tvSoundbarDiscount.subtract(excess);
            } else {
                phoneCaseDiscount = phoneCaseDiscount.subtract(excess.subtract(tvSoundbarDiscount));
                tvSoundbarDiscount = BigDecimal.ZERO;
            }
        }

        return new BundleDiscount(tvSoundbarDiscount, phoneCaseDiscount, lineDiscounts);
    }
}
