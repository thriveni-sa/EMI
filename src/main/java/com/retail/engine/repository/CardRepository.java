package com.retail.engine.repository;

import com.retail.engine.exception.ConfigurationException;
import com.retail.engine.exception.ValidationException;
import com.retail.engine.model.CardIssuer;
import com.retail.engine.validation.DataValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * In-memory repository for card issuers, enforcing strict CSV schema and duplicate rejection.
 */
public class CardRepository {

    private static final String FILE_NAME = "cards.csv";
    private static final String[] EXPECTED_HEADER = {"issuer_code", "issuer_name", "emi_eligible", "cashback_eligible"};

    private final Map<String, CardIssuer> cards = new LinkedHashMap<>();
    private final Map<String, Integer> lineNumbers = new HashMap<>();

    public void load(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigurationException(String.format("[%s] Cards catalog file not found: %s", FILE_NAME, path.toAbsolutePath()));
        }

        cards.clear();
        lineNumbers.clear();

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
                DataValidator.validateColumnCount(FILE_NAME, lineNo, tokens.length, 4);

                String code = tokens[0].trim().toUpperCase();
                String name = tokens[1].trim();
                boolean emi = DataValidator.validateAndParseBoolean(FILE_NAME, lineNo, tokens[2]);
                boolean cb = DataValidator.validateAndParseBoolean(FILE_NAME, lineNo, tokens[3]);

                if (cards.containsKey(code)) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Duplicate card issuer_code '%s' (originally declared on line %d)",
                            FILE_NAME, lineNo, code, lineNumbers.get(code)
                    ));
                }

                cards.put(code, new CardIssuer(code, name, emi, cb));
                lineNumbers.put(code, lineNo);
            }
        } catch (IOException e) {
            throw new ConfigurationException(String.format("[%s] Failed to read cards catalog: %s", FILE_NAME, e.getMessage()), e);
        }
    }

    public Optional<CardIssuer> findByCode(String issuerCode) {
        if (issuerCode == null) return Optional.empty();
        return Optional.ofNullable(cards.get(issuerCode.trim().toUpperCase()));
    }

    public boolean isRegistered(String issuerCode) {
        return issuerCode != null && cards.containsKey(issuerCode.trim().toUpperCase());
    }

    public Map<String, CardIssuer> getAllCards() {
        return Collections.unmodifiableMap(cards);
    }

    public int count() {
        return cards.size();
    }
}
