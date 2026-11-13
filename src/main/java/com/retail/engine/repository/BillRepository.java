package com.retail.engine.repository;

import com.retail.engine.exception.ConfigurationException;
import com.retail.engine.exception.ValidationException;
import com.retail.engine.model.Bill;
import com.retail.engine.model.BillItem;
import com.retail.engine.model.ProductCategory;
import com.retail.engine.validation.DataValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * In-memory repository for master bills and bill line items with rigorous integrity validation.
 */
public class BillRepository {

    private static final String BILLS_FILE = "bills.csv";
    private static final String[] BILLS_HEADER = {"bill_id", "customer_id", "bill_date", "card_issuer", "tenure_months"};

    private static final String ITEMS_FILE = "bill_items.csv";
    private static final String[] ITEMS_HEADER = {"bill_id", "line_no", "category", "item_name", "price"};

    private final Map<String, Bill> bills = new LinkedHashMap<>();
    private final Map<String, Integer> billLineNumbers = new HashMap<>();

    private final Map<String, List<BillItem>> billItems = new LinkedHashMap<>();
    private final Set<String> itemCompositeKeys = new HashSet<>();

    public void loadBills(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigurationException(String.format("[%s] File not found: %s", BILLS_FILE, path.toAbsolutePath()));
        }

        bills.clear();
        billLineNumbers.clear();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ConfigurationException(String.format("[%s] File is empty", BILLS_FILE));
            }
            String[] headerTokens = headerLine.split(",", -1);
            DataValidator.validateHeader(BILLS_FILE, headerTokens, BILLS_HEADER);

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] tokens = line.split(",", -1);
                DataValidator.validateColumnCount(BILLS_FILE, lineNo, tokens.length, 5);

                String billId = tokens[0].trim();
                DataValidator.validateBillId(BILLS_FILE, lineNo, billId);

                String customerId = tokens[1].trim();
                DataValidator.validateCustomerId(BILLS_FILE, lineNo, customerId);

                LocalDate billDate = DataValidator.validateAndParseDate(BILLS_FILE, lineNo, tokens[2]);
                String cardIssuer = tokens[3].trim().toUpperCase();

                if (cardIssuer.isEmpty()) {
                    throw new ValidationException(String.format("[%s Line %d] card_issuer cannot be empty", BILLS_FILE, lineNo));
                }

                Integer tenure = null;
                String tenureRaw = tokens[4].trim();
                if (!tenureRaw.isEmpty()) {
                    try {
                        tenure = Integer.parseInt(tenureRaw);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(String.format(
                                "[%s Line %d] Non-integer tenure value '%s'",
                                BILLS_FILE, lineNo, tenureRaw
                        ));
                    }
                }

                if (bills.containsKey(billId)) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Duplicate bill_id '%s' (originally declared on line %d)",
                            BILLS_FILE, lineNo, billId, billLineNumbers.get(billId)
                    ));
                }

                bills.put(billId, new Bill(billId, customerId, billDate, cardIssuer, tenure));
                billLineNumbers.put(billId, lineNo);
            }
        } catch (IOException e) {
            throw new ConfigurationException(String.format("[%s] Failed to read bills: %s", BILLS_FILE, e.getMessage()), e);
        }
    }

    public void loadBillItems(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigurationException(String.format("[%s] File not found: %s", ITEMS_FILE, path.toAbsolutePath()));
        }

        billItems.clear();
        itemCompositeKeys.clear();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ConfigurationException(String.format("[%s] File is empty", ITEMS_FILE));
            }
            String[] headerTokens = headerLine.split(",", -1);
            DataValidator.validateHeader(ITEMS_FILE, headerTokens, ITEMS_HEADER);

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] tokens = line.split(",", -1);
                DataValidator.validateColumnCount(ITEMS_FILE, lineNo, tokens.length, 5);

                String billId = tokens[0].trim();
                DataValidator.validateBillId(ITEMS_FILE, lineNo, billId);

                // Referential integrity check against loaded bills
                if (!bills.containsKey(billId)) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Referential integrity error: Orphaned line item references non-existent bill_id '%s'",
                            ITEMS_FILE, lineNo, billId
                    ));
                }

                int itemLineNo;
                try {
                    itemLineNo = Integer.parseInt(tokens[1].trim());
                    if (itemLineNo < 1) {
                        throw new ValidationException(String.format(
                                "[%s Line %d] line_no must be >= 1, found %d",
                                ITEMS_FILE, lineNo, itemLineNo
                        ));
                    }
                } catch (NumberFormatException e) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Non-integer line_no: '%s'",
                            ITEMS_FILE, lineNo, tokens[1]
                    ));
                }

                String compositeKey = billId + ":" + itemLineNo;
                if (itemCompositeKeys.contains(compositeKey)) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Duplicate line item number %d for bill_id '%s'",
                            ITEMS_FILE, lineNo, itemLineNo, billId
                    ));
                }
                itemCompositeKeys.add(compositeKey);

                ProductCategory category = DataValidator.validateAndParseCategory(ITEMS_FILE, lineNo, tokens[2]);
                String itemName = tokens[3].trim();
                if (itemName.isEmpty()) {
                    throw new ValidationException(String.format("[%s Line %d] item_name cannot be empty", ITEMS_FILE, lineNo));
                }

                BigDecimal price = DataValidator.validateAndParsePrice(ITEMS_FILE, lineNo, tokens[4]);

                billItems.computeIfAbsent(billId, k -> new ArrayList<>())
                        .add(new BillItem(billId, itemLineNo, category, itemName, price));
            }
        } catch (IOException e) {
            throw new ConfigurationException(String.format("[%s] Failed to read bill items: %s", ITEMS_FILE, e.getMessage()), e);
        }
    }

    public Optional<Bill> findBillById(String billId) {
        if (billId == null) return Optional.empty();
        return Optional.ofNullable(bills.get(billId.trim()));
    }

    public List<BillItem> getItemsForBill(String billId) {
        if (billId == null) return Collections.emptyList();
        return billItems.getOrDefault(billId.trim(), Collections.emptyList());
    }

    public Optional<BillItem> getItem(String billId, int lineNo) {
        List<BillItem> items = getItemsForBill(billId);
        for (BillItem it : items) {
            if (it.getLineNo() == lineNo) {
                return Optional.of(it);
            }
        }
        return Optional.empty();
    }

    public int billCount() { return bills.size(); }
    public int itemCount() {
        return billItems.values().stream().mapToInt(List::size).sum();
    }
}
