package com.retail.engine.model;

/**
 * Enumeration of the 12 recognized retail consumer electronics product categories.
 */
public enum ProductCategory {
    TV,
    SOUNDBAR,
    PHONE,
    CASE,
    LAPTOP,
    HEADPHONES,
    TABLET,
    AC,
    FRIDGE,
    SPEAKER,
    SMARTWATCH,
    MIXER;

    public static ProductCategory fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Product category string cannot be null or empty");
        }
        return ProductCategory.valueOf(value.trim().toUpperCase());
    }
}

