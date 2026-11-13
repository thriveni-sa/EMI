package com.retail.engine.config;

import com.retail.engine.exception.ConfigurationException;
import com.retail.engine.validation.DataValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads, validates, and parses key-value offer parameters from offer_config.csv.
 */
public final class ConfigLoader {

    private static final String FILE_NAME = "offer_config.csv";
    private static final String[] EXPECTED_HEADER = {"parameter", "value"};

    private static final Set<String> MANDATORY_KEYS = Set.of(
            "EMI_MIN_BILL",
            "EMI_TENURES",
            "EMI_INTEREST_PA_PCT",
            "PROCESSING_FEE",
            "PROCESSING_FEE_WAIVED_FROM",
            "CASHBACK_PCT",
            "CASHBACK_CAP_PER_CARD_CUSTOMER_MONTH",
            "BUNDLE_TV_SOUNDBAR_OFF",
            "BUNDLE_PHONE_CASE_PCT_OFF_CASE",
            "RETURN_WINDOW_DAYS"
    );

    public static OfferConfiguration load(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigurationException(String.format("[%s] File does not exist at: %s", FILE_NAME, path.toAbsolutePath()));
        }

        Map<String, String> rawParams = new LinkedHashMap<>();
        Map<String, Integer> keyLineNumbers = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ConfigurationException(String.format("[%s] File is empty", FILE_NAME));
            }
            String[] headerTokens = headerLine.split(",", -1);
            DataValidator.validateHeader(FILE_NAME, headerTokens, EXPECTED_HEADER);

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] tokens = line.split(",", -1);
                DataValidator.validateColumnCount(FILE_NAME, lineNo, tokens.length, 2);

                String key = tokens[0].trim();
                String value = tokens[1].trim();

                if (rawParams.containsKey(key)) {
                    throw new ConfigurationException(String.format(
                            "[%s Line %d] Duplicate parameter key: '%s' (originally declared on line %d)",
                            FILE_NAME, lineNo, key, keyLineNumbers.get(key)
                    ));
                }

                rawParams.put(key, value);
                keyLineNumbers.put(key, lineNo);
            }
        } catch (IOException e) {
            throw new ConfigurationException(String.format("[%s] I/O reading failure: %s", FILE_NAME, e.getMessage()), e);
        }

        // Validate presence of all mandatory keys
        Set<String> missingKeys = new HashSet<>(MANDATORY_KEYS);
        missingKeys.removeAll(rawParams.keySet());
        if (!missingKeys.isEmpty()) {
            throw new ConfigurationException(String.format(
                    "[%s] Missing mandatory configuration parameters: %s",
                    FILE_NAME, missingKeys
            ));
        }

        return parseParameters(rawParams, keyLineNumbers);
    }

    private static OfferConfiguration parseParameters(Map<String, String> raw, Map<String, Integer> lineNumbers) {
        try {
            BigDecimal emiMinBill = parseDecimal("EMI_MIN_BILL", raw, lineNumbers);

            String tenuresStr = raw.get("EMI_TENURES");
            int tenuresLine = lineNumbers.get("EMI_TENURES");
            Set<Integer> tenures = new HashSet<>();
            for (String part : tenuresStr.split(";")) {
                part = part.trim();
                if (!part.isEmpty()) {
                    try {
                        tenures.add(Integer.parseInt(part));
                    } catch (NumberFormatException e) {
                        throw new ConfigurationException(String.format(
                                "[%s Line %d] Non-integer tenure value in EMI_TENURES: '%s'",
                                FILE_NAME, tenuresLine, part
                        ));
                    }
                }
            }
            if (tenures.isEmpty()) {
                throw new ConfigurationException(String.format(
                        "[%s Line %d] EMI_TENURES must contain at least one valid integer tenure",
                        FILE_NAME, tenuresLine
                ));
            }

            BigDecimal emiInterestPaPct = parseDecimal("EMI_INTEREST_PA_PCT", raw, lineNumbers);
            BigDecimal processingFee = parseDecimal("PROCESSING_FEE", raw, lineNumbers);
            BigDecimal processingFeeWaivedFrom = parseDecimal("PROCESSING_FEE_WAIVED_FROM", raw, lineNumbers);
            BigDecimal cashbackPct = parseDecimal("CASHBACK_PCT", raw, lineNumbers);
            BigDecimal cashbackCap = parseDecimal("CASHBACK_CAP_PER_CARD_CUSTOMER_MONTH", raw, lineNumbers);
            BigDecimal bundleTvSoundbar = parseDecimal("BUNDLE_TV_SOUNDBAR_OFF", raw, lineNumbers);
            BigDecimal bundlePhoneCase = parseDecimal("BUNDLE_PHONE_CASE_PCT_OFF_CASE", raw, lineNumbers);

            int returnWindowLine = lineNumbers.get("RETURN_WINDOW_DAYS");
            int returnWindowDays;
            try {
                returnWindowDays = Integer.parseInt(raw.get("RETURN_WINDOW_DAYS").trim());
                if (returnWindowDays < 0) {
                    throw new ConfigurationException(String.format(
                            "[%s Line %d] RETURN_WINDOW_DAYS cannot be negative: %d",
                            FILE_NAME, returnWindowLine, returnWindowDays
                    ));
                }
            } catch (NumberFormatException e) {
                throw new ConfigurationException(String.format(
                        "[%s Line %d] Non-integer RETURN_WINDOW_DAYS: '%s'",
                        FILE_NAME, returnWindowLine, raw.get("RETURN_WINDOW_DAYS")
                ));
            }

            return new OfferConfiguration(
                    emiMinBill,
                    tenures,
                    emiInterestPaPct,
                    processingFee,
                    processingFeeWaivedFrom,
                    cashbackPct,
                    cashbackCap,
                    bundleTvSoundbar,
                    bundlePhoneCase,
                    returnWindowDays
            );
        } catch (ConfigurationException ce) {
            throw ce;
        } catch (Exception e) {
            throw new ConfigurationException(String.format("[%s] Parsing failure: %s", FILE_NAME, e.getMessage()), e);
        }
    }

    private static BigDecimal parseDecimal(String key, Map<String, String> raw, Map<String, Integer> lineNumbers) {
        String val = raw.get(key);
        int lineNo = lineNumbers.get(key);
        try {
            return new BigDecimal(val.trim());
        } catch (Exception e) {
            throw new ConfigurationException(String.format(
                    "[%s Line %d] Non-numeric value for %s: '%s'",
                    FILE_NAME, lineNo, key, val
            ));
        }
    }
}
