package com.retail.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Value object encapsulating detailed bundle discount breakdown for a transaction.
 * Preserves gross item values and generates a separate item-by-item discount breakdown.
 */
public final class BundleDiscount {

    public static final BundleDiscount ZERO = new BundleDiscount(
            BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyMap()
    );

    private final BigDecimal tvSoundbarDiscount;
    private final BigDecimal phoneCaseDiscount;
    private final BigDecimal totalDiscount;
    private final Map<Integer, BigDecimal> lineItemDiscounts;

    public BundleDiscount(BigDecimal tvSoundbarDiscount, BigDecimal phoneCaseDiscount) {
        this(tvSoundbarDiscount, phoneCaseDiscount, Collections.emptyMap());
    }

    public BundleDiscount(BigDecimal tvSoundbarDiscount, BigDecimal phoneCaseDiscount, Map<Integer, BigDecimal> lineItemDiscounts) {
        this.tvSoundbarDiscount = Objects.requireNonNull(tvSoundbarDiscount, "tvSoundbarDiscount must not be null")
                .setScale(2, RoundingMode.HALF_UP);
        this.phoneCaseDiscount = Objects.requireNonNull(phoneCaseDiscount, "phoneCaseDiscount must not be null")
                .setScale(2, RoundingMode.HALF_UP);
        this.totalDiscount = this.tvSoundbarDiscount.add(this.phoneCaseDiscount).setScale(2, RoundingMode.HALF_UP);
        this.lineItemDiscounts = (lineItemDiscounts == null) ?
                Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(lineItemDiscounts));
    }

    public BigDecimal getTvSoundbarDiscount() {
        return tvSoundbarDiscount;
    }

    public BigDecimal getPhoneCaseDiscount() {
        return phoneCaseDiscount;
    }

    public BigDecimal getTotalDiscount() {
        return totalDiscount;
    }

    public Map<Integer, BigDecimal> getLineItemDiscounts() {
        return lineItemDiscounts;
    }

    public BigDecimal getDiscountForLine(int lineNo) {
        return lineItemDiscounts.getOrDefault(lineNo, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    public boolean hasDiscount() {
        return totalDiscount.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public String toString() {
        return "BundleDiscount{" +
                "tvSoundbarDiscount=" + tvSoundbarDiscount +
                ", phoneCaseDiscount=" + phoneCaseDiscount +
                ", totalDiscount=" + totalDiscount +
                ", lineItemDiscounts=" + lineItemDiscounts +
                '}';
    }
}
