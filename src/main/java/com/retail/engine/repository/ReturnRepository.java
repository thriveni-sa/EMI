package com.retail.engine.repository;

import com.retail.engine.exception.ConfigurationException;
import com.retail.engine.exception.ValidationException;
import com.retail.engine.model.Bill;
import com.retail.engine.model.ReturnTransaction;
import com.retail.engine.validation.DataValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * In-memory repository for item returns with referential integrity verification against bills.
 */
public class ReturnRepository {

    private static final String FILE_NAME = "returns.csv";
    private static final String[] EXPECTED_HEADER = {"return_id", "bill_id", "line_no", "return_date"};

    private final Map<String, ReturnTransaction> returns = new LinkedHashMap<>();
    private final Map<String, Integer> lineNumbers = new HashMap<>();

    public void load(Path path) {
        if (!Files.exists(path)) {
            throw new ConfigurationException(String.format("[%s] File not found: %s", FILE_NAME, path.toAbsolutePath()));
        }

        returns.clear();
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

                String returnId = tokens[0].trim();
                DataValidator.validateReturnId(FILE_NAME, lineNo, returnId);

                String billId = tokens[1].trim();
                DataValidator.validateBillId(FILE_NAME, lineNo, billId);

                int lineItemNo;
                try {
                    lineItemNo = Integer.parseInt(tokens[2].trim());
                    if (lineItemNo < 1) {
                        throw new ValidationException(String.format(
                                "[%s Line %d] line_no must be >= 1, found %d",
                                FILE_NAME, lineNo, lineItemNo
                        ));
                    }
                } catch (NumberFormatException e) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Non-integer line_no '%s'",
                            FILE_NAME, lineNo, tokens[2]
                    ));
                }

                LocalDate returnDate = DataValidator.validateAndParseDate(FILE_NAME, lineNo, tokens[3]);

                if (returns.containsKey(returnId)) {
                    throw new ValidationException(String.format(
                            "[%s Line %d] Duplicate return_id '%s' (originally declared on line %d)",
                            FILE_NAME, lineNo, returnId, lineNumbers.get(returnId)
                    ));
                }

                returns.put(returnId, new ReturnTransaction(returnId, billId, lineItemNo, returnDate));
                lineNumbers.put(returnId, lineNo);
            }
        } catch (IOException e) {
            throw new ConfigurationException(String.format("[%s] Failed to read returns: %s", FILE_NAME, e.getMessage()), e);
        }
    }

    public void validateReferentialIntegrity(BillRepository billRepo) {
        for (Map.Entry<String, ReturnTransaction> entry : returns.entrySet()) {
            ReturnTransaction ret = entry.getValue();
            int lineNo = lineNumbers.get(ret.getReturnId());

            Bill bill = billRepo.findBillById(ret.getBillId()).orElseThrow(() ->
                    new ValidationException(String.format(
                            "[%s Line %d] Referential integrity error: Return '%s' references non-existent bill_id '%s'",
                            FILE_NAME, lineNo, ret.getReturnId(), ret.getBillId()
                    ))
            );

            if (billRepo.getItem(ret.getBillId(), ret.getLineNo()).isEmpty()) {
                throw new ValidationException(String.format(
                        "[%s Line %d] Referential integrity error: Return '%s' references non-existent line_no %d on bill '%s'",
                        FILE_NAME, lineNo, ret.getReturnId(), ret.getLineNo(), ret.getBillId()
                ));
            }

            DataValidator.validateChronology(FILE_NAME, lineNo, bill.getBillDate(), ret.getReturnDate());
        }
    }

    public Optional<ReturnTransaction> findById(String returnId) {
        if (returnId == null) return Optional.empty();
        return Optional.ofNullable(returns.get(returnId.trim()));
    }

    public int count() { return returns.size(); }
}
