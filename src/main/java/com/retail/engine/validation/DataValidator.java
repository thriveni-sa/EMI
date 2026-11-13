package com.retail.engine.validation;

import com.retail.engine.exception.ValidationException;
import com.retail.engine.model.ProductCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Centralized data validator asserting CSV schema, types, domains, and relational invariants.
 * Preserves exact file names and line numbers in all validation diagnostics.
 */
public final class DataValidator {

    private static final Pattern BILL_ID_PATTERN = Pattern.compile("^BILL-\\d{4}$");
    private static final Pattern CUST_ID_PATTERN = Pattern.compile("^CUST-\\d{3}$");
    private static final Pattern RET_ID_PATTERN = Pattern.compile("^RET-\\d{3}$");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    private DataValidator() {}

    public static void validateHeader(String fileName, String[] actual, String[] expected) {
        if (actual == null || actual.length != expected.length) {
            throw new ValidationException(String.format(
                    "[%s Header Error] Expected %d columns %s but found %s",
                    fileName, expected.length, Arrays.toString(expected), Arrays.toString(actual)
            ));
        }
        for (int i = 0; i < expected.length; i++) {
            if (!actual[i].trim().equalsIgnoreCase(expected[i].trim())) {
                throw new ValidationException(String.format(
                        "[%s Header Error] Column %d: Expected '%s' but found '%s'",
                        fileName, i + 1, expected[i], actual[i]
                ));
            }
        }
    }

    public static void validateColumnCount(String fileName, int lineNo, int actual, int expected) {
        if (actual != expected) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Column count mismatch: Expected %d columns but found %d",
                    fileName, lineNo, expected, actual
            ));
        }
    }

    public static void validateBillId(String fileName, int lineNo, String billId) {
        if (billId == null || !BILL_ID_PATTERN.matcher(billId.trim()).matches()) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Malformed bill_id '%s'. Expected format: BILL-nnnn",
                    fileName, lineNo, billId
            ));
        }
    }

    public static void validateCustomerId(String fileName, int lineNo, String customerId) {
        if (customerId == null || !CUST_ID_PATTERN.matcher(customerId.trim()).matches()) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Malformed customer_id '%s'. Expected format: CUST-nnn",
                    fileName, lineNo, customerId
            ));
        }
    }

    public static void validateReturnId(String fileName, int lineNo, String returnId) {
        if (returnId == null || !RET_ID_PATTERN.matcher(returnId.trim()).matches()) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Malformed return_id '%s'. Expected format: RET-nnn",
                    fileName, lineNo, returnId
            ));
        }
    }

    public static LocalDate validateAndParseDate(String fileName, int lineNo, String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Date value is missing or empty",
                    fileName, lineNo
            ));
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Invalid date format '%s'. Expected ISO-8601 YYYY-MM-DD",
                    fileName, lineNo, dateStr
            ));
        }
    }

    public static void validateYearMonth(String commandName, int lineNo, String ymStr) {
        if (ymStr == null || !YEAR_MONTH_PATTERN.matcher(ymStr.trim()).matches()) {
            throw new ValidationException(String.format(
                    "[commands.txt Line %d] Invalid %s argument '%s'. Expected YYYY-MM",
                    lineNo, commandName, ymStr
            ));
        }
    }

    public static BigDecimal validateAndParsePrice(String fileName, int lineNo, String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Price is missing or empty",
                    fileName, lineNo
            ));
        }
        try {
            BigDecimal price = new BigDecimal(priceStr.trim());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(String.format(
                        "[%s Line %d] Price must be strictly positive (> 0.00): %s",
                        fileName, lineNo, priceStr
                ));
            }
            return price;
        } catch (NumberFormatException e) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Non-numeric price value: '%s'",
                    fileName, lineNo, priceStr
            ));
        }
    }

    public static ProductCategory validateAndParseCategory(String fileName, int lineNo, String categoryStr) {
        if (categoryStr == null || categoryStr.trim().isEmpty()) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Category is missing or empty",
                    fileName, lineNo
            ));
        }
        try {
            return ProductCategory.fromString(categoryStr);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Unrecognized product category '%s'. Allowed: %s",
                    fileName, lineNo, categoryStr, Arrays.toString(ProductCategory.values())
            ));
        }
    }

    public static boolean validateAndParseBoolean(String fileName, int lineNo, String boolStr) {
        if (boolStr == null) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Boolean flag is null", fileName, lineNo
            ));
        }
        String clean = boolStr.trim().toLowerCase();
        if ("true".equals(clean)) return true;
        if ("false".equals(clean)) return false;
        throw new ValidationException(String.format(
                "[%s Line %d] Invalid boolean value '%s'. Expected 'true' or 'false'",
                fileName, lineNo, boolStr
        ));
    }

    public static void validateChronology(String fileName, int lineNo, LocalDate billDate, LocalDate returnDate) {
        if (returnDate.isBefore(billDate)) {
            throw new ValidationException(String.format(
                    "[%s Line %d] Chronological anomaly: Return date (%s) is before bill date (%s)",
                    fileName, lineNo, returnDate, billDate
            ));
        }
    }
}
