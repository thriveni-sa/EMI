package com.retail.engine.service;

import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.Bill;
import com.retail.engine.model.CardIssuer;
import com.retail.engine.model.CashbackTransaction;
import com.retail.engine.repository.CustomerLedgerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain service evaluating instant card cashback discounts and managing rolling monthly caps.
 */
public class CashbackService {

    private final CustomerLedgerRepository ledgerRepository;

    public CashbackService(CustomerLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    public record CashbackResult(
            boolean eligible,
            BigDecimal grossCashback,
            BigDecimal awardedCashback
    ) {
        public static CashbackResult ineligible() {
            return new CashbackResult(false, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public CashbackResult evaluateCashback(Bill bill, BigDecimal netBillAmount, CardIssuer card, OfferConfiguration config) {
        // BR-02: Card must be cashback eligible
        if (card == null || !card.isCashbackEligible()) {
            return CashbackResult.ineligible();
        }

        // BR-11: 10% of post-bundle bill amount
        BigDecimal rate = config.getCashbackPct().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal grossCashback = netBillAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        // BR-12 & EC-02: Monthly cap per customer, card, month
        String yearMonth = String.format("%04d-%02d", bill.getBillDate().getYear(), bill.getBillDate().getMonthValue());
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount(
                bill.getCustomerId(),
                bill.getCardIssuer(),
                yearMonth
        );

        BigDecimal remainingCap = account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth());
        BigDecimal awardedCashback = grossCashback.min(remainingCap).setScale(2, RoundingMode.HALF_UP);

        return new CashbackResult(true, grossCashback, awardedCashback);
    }

    public void commitCashback(Bill bill, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            CashbackTransaction tx = new CashbackTransaction(
                    bill.getBillId(),
                    bill.getCustomerId(),
                    bill.getCardIssuer(),
                    bill.getBillDate(),
                    CashbackTransaction.TransactionType.GRANT,
                    amount
            );
            ledgerRepository.recordGrant(tx);
        }
    }
}

