package com.retail.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable entity representing a line item in an electronics transaction bill.
 */
public final class BillItem {

    private final String billId;
    private final int lineNo;
    private final ProductCategory category;
    private final String itemName;
    private final BigDecimal price;

    public BillItem(String billId, int lineNo, ProductCategory category, String itemName, BigDecimal price) {
        this.billId = Objects.requireNonNull(billId, "billId must not be null").trim();
        if (lineNo < 1) {
            throw new IllegalArgumentException("lineNo must be >= 1");
        }
        this.lineNo = lineNo;
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.itemName = Objects.requireNonNull(itemName, "itemName must not be null").trim();
        Objects.requireNonNull(price, "price must not be null");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be strictly positive: " + price);
        }
        this.price = price.setScale(2, RoundingMode.HALF_UP);
    }

    public String getBillId() {
        return billId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BillItem billItem)) return false;
        return lineNo == billItem.lineNo && billId.equals(billItem.billId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(billId, lineNo);
    }

    @Override
    public String toString() {
        return "BillItem{" +
                "billId='" + billId + '\'' +
                ", lineNo=" + lineNo +
                ", category=" + category +
                ", itemName='" + itemName + '\'' +
                ", price=" + price +
                '}';
    }
}

