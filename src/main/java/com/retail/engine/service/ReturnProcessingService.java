package com.retail.engine.service;

import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.*;
import com.retail.engine.repository.CustomerLedgerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Domain service processing merchandise returns, statutory window enforcement,
 * promotional clawbacks, and customer ledger cap restoration.
 * Guarantees duplicate return prevention, safe handling of multi-item returns,
 * and bucket restoration into the original bill's calendar month.
 */
public class ReturnProcessingService {

    private final CustomerLedgerRepository ledgerRepository;
    private final Set<String> processedReturnIds = new HashSet<>();
    private final Map<String, BigDecimal> remainingReversibleByBill = new HashMap<>();
    private final List<ReturnLogRecord> processedLog = new ArrayList<>();

    public ReturnProcessingService(CustomerLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    public ReturnLogRecord processReturn(ReturnTransaction returnTx, Bill bill, OfferResult evaluatedBill, OfferConfiguration config) {
        // Prevent duplicate return command execution
        if (processedReturnIds.contains(returnTx.getReturnId())) {
            // Find previously recorded record
            return processedLog.stream()
                    .filter(r -> r.getReturnId().equals(returnTx.getReturnId()))
                    .findFirst()
                    .orElseGet(() -> new ReturnLogRecord(
                            returnTx.getReturnId(),
                            returnTx.getBillId(),
                            returnTx.getLineNo(),
                            returnTx.getReturnDate(),
                            (int) ChronoUnit.DAYS.between(bill.getBillDate(), returnTx.getReturnDate()),
                            ChronoUnit.DAYS.between(bill.getBillDate(), returnTx.getReturnDate()) <= config.getReturnWindowDays(),
                            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    ));
        }

        // BR-19: Elapsed duration: return_date - bill_date
        long daysBetween = ChronoUnit.DAYS.between(bill.getBillDate(), returnTx.getReturnDate());
        int daysAfterBill = (int) daysBetween;

        // BR-21: Statutory window check (<= 7 days)
        boolean withinWindow = daysAfterBill <= config.getReturnWindowDays();

        // Track reversible cashback pool per bill to prevent double clawback on multi-item returns
        BigDecimal availableToReverse = remainingReversibleByBill.computeIfAbsent(
                bill.getBillId(),
                k -> (evaluatedBill.getRecommendedOption() == OfferType.CASHBACK)
                        ? evaluatedBill.getCashbackAmount()
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        );

        BigDecimal cashbackReversed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (withinWindow && evaluatedBill.getRecommendedOption() == OfferType.CASHBACK && availableToReverse.compareTo(BigDecimal.ZERO) > 0) {
            cashbackReversed = availableToReverse;
            // Absorb available pool so subsequent returns on this bill reverse 0.00
            remainingReversibleByBill.put(bill.getBillId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            // BR-20: Restore monthly cap usage in the ORIGINAL BILL'S customer/card/month bucket!
            String billYearMonth = String.format("%04d-%02d", bill.getBillDate().getYear(), bill.getBillDate().getMonthValue());

            CashbackTransaction reversalTx = new CashbackTransaction(
                    returnTx.getBillId(),
                    bill.getCustomerId(),
                    bill.getCardIssuer(),
                    returnTx.getReturnDate(), // event occurrence date
                    billYearMonth,            // target ledger bucket = original bill's month
                    CashbackTransaction.TransactionType.REVERSAL,
                    cashbackReversed
            );
            ledgerRepository.recordReversal(reversalTx);
        }

        processedReturnIds.add(returnTx.getReturnId());

        ReturnLogRecord record = new ReturnLogRecord(
                returnTx.getReturnId(),
                returnTx.getBillId(),
                returnTx.getLineNo(),
                returnTx.getReturnDate(),
                daysAfterBill,
                withinWindow,
                cashbackReversed
        );
        processedLog.add(record);
        return record;
    }

    public List<ReturnLogRecord> getProcessedLog() {
        return Collections.unmodifiableList(processedLog);
    }

    public void clear() {
        processedReturnIds.clear();
        remainingReversibleByBill.clear();
        processedLog.clear();
    }
}
