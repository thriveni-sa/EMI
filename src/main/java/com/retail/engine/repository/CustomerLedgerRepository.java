package com.retail.engine.repository;

import com.retail.engine.model.CashbackReport;
import com.retail.engine.model.CashbackTransaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * In-memory state repository maintaining individual cashback transactions
 * and running monthly ledgers per customer and card issuer.
 * Ensures duplicate prevention, transactional auditability, and temporal reconstruction.
 */
public class CustomerLedgerRepository {

    public record LedgerKey(String customerId, String cardIssuer, String yearMonth) {
        public LedgerKey {
            customerId = customerId.trim();
            cardIssuer = cardIssuer.trim().toUpperCase();
            yearMonth = yearMonth.trim();
        }
    }

    public static final class LedgerAccount {
        private final LedgerKey key;
        private BigDecimal cashbackGranted = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal cashbackReversed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private final List<CashbackTransaction> transactions = new ArrayList<>();

        public LedgerAccount(LedgerKey key) {
            this.key = key;
        }

        public LedgerKey getKey() { return key; }
        public BigDecimal getCashbackGranted() { return cashbackGranted; }
        public BigDecimal getCashbackReversed() { return cashbackReversed; }
        public List<CashbackTransaction> getTransactions() { return Collections.unmodifiableList(transactions); }

        public BigDecimal getNetLiability() {
            return cashbackGranted.subtract(cashbackReversed).setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal getRemainingCap(BigDecimal monthlyCap) {
            BigDecimal remaining = monthlyCap.subtract(getNetLiability());
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return remaining.setScale(2, RoundingMode.HALF_UP);
        }

        public void addGrant(BigDecimal amount) {
            this.cashbackGranted = this.cashbackGranted.add(amount).setScale(2, RoundingMode.HALF_UP);
        }

        public void addGrant(CashbackTransaction tx) {
            this.transactions.add(tx);
            this.cashbackGranted = this.cashbackGranted.add(tx.getAmount()).setScale(2, RoundingMode.HALF_UP);
        }

        public void addReversal(BigDecimal amount) {
            this.cashbackReversed = this.cashbackReversed.add(amount).setScale(2, RoundingMode.HALF_UP);
        }

        public void addReversal(CashbackTransaction tx) {
            this.transactions.add(tx);
            this.cashbackReversed = this.cashbackReversed.add(tx.getAmount()).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private final Map<LedgerKey, LedgerAccount> accounts = new LinkedHashMap<>();
    private final List<CashbackTransaction> transactionHistory = new ArrayList<>();
    private final Set<String> grantedBillIds = new HashSet<>();

    public LedgerAccount getOrCreateAccount(String customerId, String cardIssuer, String yearMonth) {
        LedgerKey key = new LedgerKey(customerId, cardIssuer, yearMonth);
        return accounts.computeIfAbsent(key, LedgerAccount::new);
    }

    /**
     * Records a cashback grant transaction.
     * Prevents duplicate grants for the same billId.
     *
     * @param tx The cashback grant transaction
     * @return true if granted successfully, false if duplicate billId
     */
    public boolean recordGrant(CashbackTransaction tx) {
        if (grantedBillIds.contains(tx.getBillId())) {
            return false; // Prevent duplicate grant for the same bill
        }
        grantedBillIds.add(tx.getBillId());
        transactionHistory.add(tx);

        LedgerAccount account = getOrCreateAccount(tx.getCustomerId(), tx.getCardIssuer(), tx.getYearMonth());
        account.addGrant(tx);
        return true;
    }

    /**
     * Records a cashback reversal transaction (merchandise return clawback).
     *
     * @param tx The cashback reversal transaction
     */
    public void recordReversal(CashbackTransaction tx) {
        transactionHistory.add(tx);
        LedgerAccount account = getOrCreateAccount(tx.getCustomerId(), tx.getCardIssuer(), tx.getYearMonth());
        account.addReversal(tx);
    }

    public boolean hasBillBeenGranted(String billId) {
        return grantedBillIds.contains(billId);
    }

    public List<CashbackTransaction> getTransactionHistory() {
        return Collections.unmodifiableList(transactionHistory);
    }

    public Map<LedgerKey, LedgerAccount> getAccounts() {
        return Collections.unmodifiableMap(accounts);
    }

    public List<CashbackReport> generateMonthlyReport(String yearMonth, BigDecimal monthlyCap) {
        List<CashbackReport> reports = new ArrayList<>();
        for (LedgerAccount account : accounts.values()) {
            if (account.getKey().yearMonth().equals(yearMonth)) {
                // Include if there was cashback activity
                if (account.getCashbackGranted().compareTo(BigDecimal.ZERO) > 0 ||
                    account.getCashbackReversed().compareTo(BigDecimal.ZERO) > 0) {
                    reports.add(new CashbackReport(
                            yearMonth,
                            account.getKey().customerId(),
                            account.getKey().cardIssuer(),
                            account.getCashbackGranted(),
                            account.getCashbackReversed(),
                            account.getNetLiability(),
                            account.getRemainingCap(monthlyCap)
                    ));
                }
            }
        }
        return reports;
    }

    /**
     * Reconstructs monthly ledger accounts from an arbitrary sequence of transactions.
     * Demonstrates deterministic replayability from command/transaction event logs.
     */
    public static Map<LedgerKey, LedgerAccount> reconstructFromTransactions(List<CashbackTransaction> transactions) {
        Map<LedgerKey, LedgerAccount> reconstructed = new LinkedHashMap<>();
        for (CashbackTransaction tx : transactions) {
            LedgerKey key = new LedgerKey(tx.getCustomerId(), tx.getCardIssuer(), tx.getYearMonth());
            LedgerAccount account = reconstructed.computeIfAbsent(key, LedgerAccount::new);
            if (tx.getType() == CashbackTransaction.TransactionType.GRANT) {
                account.addGrant(tx);
            } else if (tx.getType() == CashbackTransaction.TransactionType.REVERSAL) {
                account.addReversal(tx);
            }
        }
        return reconstructed;
    }

    public void clear() {
        accounts.clear();
        transactionHistory.clear();
        grantedBillIds.clear();
    }
}
