package com.retail.engine;

import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.Bill;
import com.retail.engine.model.CardIssuer;
import com.retail.engine.model.CashbackTransaction;
import com.retail.engine.repository.CustomerLedgerRepository;
import com.retail.engine.service.CashbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Instant Cashback Engine Deep-Dive Tests")
public class CashbackEngineTest {

    private OfferConfiguration config;
    private CustomerLedgerRepository ledgerRepository;
    private CashbackService cashbackService;

    private CardIssuer hdfcCard;
    private CardIssuer axisCard;
    private CardIssuer sbiCard;
    private CardIssuer amexCard;
    private CardIssuer iciciCard;

    @BeforeEach
    void setUp() {
        Path configPath = Paths.get("offer_config.csv").toAbsolutePath().normalize();
        config = ConfigLoader.load(configPath);
        ledgerRepository = new CustomerLedgerRepository();
        cashbackService = new CashbackService(ledgerRepository);

        hdfcCard = new CardIssuer("HDFC", true, true);
        axisCard = new CardIssuer("AXIS", false, true);   // Cashback only
        sbiCard = new CardIssuer("SBI", true, false);      // EMI only
        iciciCard = new CardIssuer("ICICI", true, true);
        amexCard = new CardIssuer("AMEX", false, false);   // Ineligible
    }

    @Test
    @DisplayName("BR-02 & TRAP-04, TRAP-06: Non-eligible card receives zero cashback (AMEX, SBI)")
    void testIneligibleCardCashback() {
        // AMEX: cashback_eligible = false
        Bill amexBill = new Bill("BILL-TEST-AMEX", "CUST-001", LocalDate.of(2026, 10, 1), "AMEX", null);
        CashbackService.CashbackResult amexResult = cashbackService.evaluateCashback(amexBill, new BigDecimal("10000.00"), amexCard, config);
        assertFalse(amexResult.eligible());
        assertEquals(BigDecimal.ZERO, amexResult.grossCashback());
        assertEquals(BigDecimal.ZERO, amexResult.awardedCashback());

        // SBI: cashback_eligible = false
        Bill sbiBill = new Bill("BILL-TEST-SBI", "CUST-001", LocalDate.of(2026, 10, 1), "SBI", null);
        CashbackService.CashbackResult sbiResult = cashbackService.evaluateCashback(sbiBill, new BigDecimal("10000.00"), sbiCard, config);
        assertFalse(sbiResult.eligible());
        assertEquals(BigDecimal.ZERO, sbiResult.grossCashback());
        assertEquals(BigDecimal.ZERO, sbiResult.awardedCashback());
    }

    @Test
    @DisplayName("BR-11: Standard 10% instant cashback on post-bundle bill amount")
    void testStandardTenPercentCashback() {
        Bill bill = new Bill("BILL-TEST-1", "CUST-001", LocalDate.of(2026, 10, 1), "HDFC", null);
        CashbackService.CashbackResult result = cashbackService.evaluateCashback(bill, new BigDecimal("8000.00"), hdfcCard, config);

        assertTrue(result.eligible());
        assertEquals(new BigDecimal("800.00"), result.grossCashback());
        assertEquals(new BigDecimal("800.00"), result.awardedCashback());
    }

    @Test
    @DisplayName("BR-12: No previous usage grants full 10% up to Rs 1,500.00 cap")
    void testNoPreviousUsage() {
        Bill bill = new Bill("BILL-FRESH", "CUST-FRESH", LocalDate.of(2026, 10, 1), "HDFC", null);
        CashbackService.CashbackResult result = cashbackService.evaluateCashback(bill, new BigDecimal("6000.00"), hdfcCard, config);

        assertTrue(result.eligible());
        assertEquals(new BigDecimal("600.00"), result.awardedCashback());

        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-FRESH", "HDFC", "2026-10");
        assertEquals(new BigDecimal("1500.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
    }

    @Test
    @DisplayName("BR-12: Partial usage leaves remaining balance available for subsequent purchases")
    void testPartialUsage() {
        Bill bill1 = new Bill("BILL-P1", "CUST-PART", LocalDate.of(2026, 10, 1), "HDFC", null);
        CashbackService.CashbackResult res1 = cashbackService.evaluateCashback(bill1, new BigDecimal("6000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("600.00"), res1.awardedCashback());
        cashbackService.commitCashback(bill1, res1.awardedCashback());

        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-PART", "HDFC", "2026-10");
        assertEquals(new BigDecimal("900.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        Bill bill2 = new Bill("BILL-P2", "CUST-PART", LocalDate.of(2026, 10, 5), "HDFC", null);
        CashbackService.CashbackResult res2 = cashbackService.evaluateCashback(bill2, new BigDecimal("4000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("400.00"), res2.awardedCashback());
        cashbackService.commitCashback(bill2, res2.awardedCashback());

        assertEquals(new BigDecimal("500.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
    }

    @Test
    @DisplayName("BR-12: Single purchase exceeding cap awards exactly Rs 1,500.00")
    void testExactlyRs1500InSingleTransaction() {
        // Rs 20,000 bill: 10% = 2,000.00 -> capped at 1,500.00
        Bill bill = new Bill("BILL-EXACT-1500", "CUST-MAX", LocalDate.of(2026, 10, 2), "HDFC", null);
        CashbackService.CashbackResult result = cashbackService.evaluateCashback(bill, new BigDecimal("20000.00"), hdfcCard, config);

        assertTrue(result.eligible());
        assertEquals(new BigDecimal("2000.00"), result.grossCashback());
        assertEquals(new BigDecimal("1500.00"), result.awardedCashback());
        cashbackService.commitCashback(bill, result.awardedCashback());

        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-MAX", "HDFC", "2026-10");
        assertEquals(new BigDecimal("1500.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("0.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
    }

    @Test
    @DisplayName("BR-12 & EC-02: Teacher's planted scenario for CUST-003 on AXIS (BILL-0012, BILL-0062, BILL-0102)")
    void testTeacherScenarioEC2AxisCust003() {
        // BILL-0012: 9000.00 -> 900.00 cashback
        Bill bill1 = new Bill("BILL-0012", "CUST-003", LocalDate.of(2026, 10, 2), "AXIS", null);
        CashbackService.CashbackResult res1 = cashbackService.evaluateCashback(bill1, new BigDecimal("9000.00"), axisCard, config);
        assertTrue(res1.eligible());
        assertEquals(new BigDecimal("900.00"), res1.grossCashback());
        assertEquals(new BigDecimal("900.00"), res1.awardedCashback());
        cashbackService.commitCashback(bill1, res1.awardedCashback());

        // BILL-0062: 5000.00 -> 500.00 cashback (used = 1400.00, remaining = 100.00)
        Bill bill2 = new Bill("BILL-0062", "CUST-003", LocalDate.of(2026, 10, 9), "AXIS", null);
        CashbackService.CashbackResult res2 = cashbackService.evaluateCashback(bill2, new BigDecimal("5000.00"), axisCard, config);
        assertTrue(res2.eligible());
        assertEquals(new BigDecimal("500.00"), res2.grossCashback());
        assertEquals(new BigDecimal("500.00"), res2.awardedCashback());
        cashbackService.commitCashback(bill2, res2.awardedCashback());

        // BILL-0102: 3000.00 -> gross 300.00, but cap remaining is 100.00 -> awarded 100.00!
        Bill bill3 = new Bill("BILL-0102", "CUST-003", LocalDate.of(2026, 10, 15), "AXIS", null);
        CashbackService.CashbackResult res3 = cashbackService.evaluateCashback(bill3, new BigDecimal("3000.00"), axisCard, config);
        assertTrue(res3.eligible());
        assertEquals(new BigDecimal("300.00"), res3.grossCashback());
        assertEquals(new BigDecimal("100.00"), res3.awardedCashback());
        cashbackService.commitCashback(bill3, res3.awardedCashback());

        // Account is now fully saturated (1500/1500)
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-003", "AXIS", "2026-10");
        assertEquals(new BigDecimal("1500.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("0.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Subsequent purchase in same month gets 0.00
        Bill bill4 = new Bill("BILL-EXTRA", "CUST-003", LocalDate.of(2026, 10, 20), "AXIS", null);
        CashbackService.CashbackResult res4 = cashbackService.evaluateCashback(bill4, new BigDecimal("4000.00"), axisCard, config);
        assertEquals(new BigDecimal("0.00"), res4.awardedCashback());
    }

    @Test
    @DisplayName("Prevent duplicate grants for the same billId")
    void testPreventDuplicateGrantsSameBill() {
        Bill bill = new Bill("BILL-DUP-TEST", "CUST-DUP", LocalDate.of(2026, 10, 5), "HDFC", null);

        cashbackService.commitCashback(bill, new BigDecimal("500.00"));
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-DUP", "HDFC", "2026-10");
        assertEquals(new BigDecimal("500.00"), account.getCashbackGranted());

        // Attempting to commit the same bill again must be ignored
        cashbackService.commitCashback(bill, new BigDecimal("500.00"));
        assertEquals(new BigDecimal("500.00"), account.getCashbackGranted(), "Duplicate grant must be rejected");
        assertEquals(1, ledgerRepository.getTransactionHistory().size());
    }

    @Test
    @DisplayName("BR-12: Monthly cap resets in next calendar month")
    void testMonthlyCapResetNextMonth() {
        // October: exhaust full Rs 1,500.00
        Bill billOct = new Bill("BILL-OCT", "CUST-MONTH", LocalDate.of(2026, 10, 15), "HDFC", null);
        CashbackService.CashbackResult resOct = cashbackService.evaluateCashback(billOct, new BigDecimal("15000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("1500.00"), resOct.awardedCashback());
        cashbackService.commitCashback(billOct, resOct.awardedCashback());

        // November: cap must be refreshed to fresh Rs 1,500.00
        Bill billNov = new Bill("BILL-NOV", "CUST-MONTH", LocalDate.of(2026, 11, 1), "HDFC", null);
        CashbackService.CashbackResult resNov = cashbackService.evaluateCashback(billNov, new BigDecimal("10000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("1000.00"), resNov.awardedCashback());
    }

    @Test
    @DisplayName("BR-12: Cap is isolated across different card issuers for same customer")
    void testCapIsolationAcrossCards() {
        // Customer uses HDFC and exhausts Rs 1,500.00
        Bill billHdfc = new Bill("BILL-HDFC", "CUST-MULTI", LocalDate.of(2026, 10, 5), "HDFC", null);
        CashbackService.CashbackResult resHdfc = cashbackService.evaluateCashback(billHdfc, new BigDecimal("15000.00"), hdfcCard, config);
        cashbackService.commitCashback(billHdfc, resHdfc.awardedCashback());

        // Same customer uses ICICI in same month -> should have full Rs 1,500.00 cap available
        Bill billIcici = new Bill("BILL-ICICI", "CUST-MULTI", LocalDate.of(2026, 10, 6), "ICICI", null);
        CashbackService.CashbackResult resIcici = cashbackService.evaluateCashback(billIcici, new BigDecimal("10000.00"), iciciCard, config);
        assertEquals(new BigDecimal("1000.00"), resIcici.awardedCashback());
    }

    @Test
    @DisplayName("BR-12: Cap is isolated across different customers for same card")
    void testCapIsolationAcrossCustomers() {
        // CUST-001 exhausts Rs 1,500.00
        Bill bill1 = new Bill("BILL-C1", "CUST-A", LocalDate.of(2026, 10, 5), "HDFC", null);
        CashbackService.CashbackResult res1 = cashbackService.evaluateCashback(bill1, new BigDecimal("15000.00"), hdfcCard, config);
        cashbackService.commitCashback(bill1, res1.awardedCashback());

        // CUST-002 makes a purchase on HDFC -> gets fresh cap
        Bill bill2 = new Bill("BILL-C2", "CUST-B", LocalDate.of(2026, 10, 5), "HDFC", null);
        CashbackService.CashbackResult res2 = cashbackService.evaluateCashback(bill2, new BigDecimal("12000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("1200.00"), res2.awardedCashback());
    }

    @Test
    @DisplayName("BR-21: Return transaction reverses cashback and restores cap for subsequent purchases")
    void testReturnRestoresCapForNextPurchase() {
        String custId = "CUST-RETURN-TEST";
        String card = "HDFC";

        // Initial purchase: Rs 15,000 -> Rs 1,500 cashback (cap exhausted)
        Bill bill1 = new Bill("BILL-RET-1", custId, LocalDate.of(2026, 10, 2), card, null);
        CashbackService.CashbackResult res1 = cashbackService.evaluateCashback(bill1, new BigDecimal("15000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("1500.00"), res1.awardedCashback());
        cashbackService.commitCashback(bill1, res1.awardedCashback());

        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount(custId, card, "2026-10");
        assertEquals(new BigDecimal("0.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Merchandise return reverses Rs 500.00
        CashbackTransaction reversalTx = new CashbackTransaction(
                "BILL-RET-1", custId, card, LocalDate.of(2026, 10, 6),
                CashbackTransaction.TransactionType.REVERSAL, new BigDecimal("500.00")
        );
        ledgerRepository.recordReversal(reversalTx);

        // Cap must now have Rs 500.00 restored!
        assertEquals(new BigDecimal("500.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Subsequent purchase in same month can now earn Rs 500.00 cashback!
        Bill bill2 = new Bill("BILL-RET-2", custId, LocalDate.of(2026, 10, 10), card, null);
        CashbackService.CashbackResult res2 = cashbackService.evaluateCashback(bill2, new BigDecimal("8000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("500.00"), res2.awardedCashback());
    }

    @Test
    @DisplayName("Transaction History & Ledger Reconstruction: Replaying transaction stream yields exact ledger accounts")
    void testTransactionHistoryReconstruction() {
        // Simulate a sequence of 3 grants and 1 reversal across 2 customers
        CashbackTransaction tx1 = new CashbackTransaction("B-1", "C-1", "HDFC", LocalDate.of(2026, 10, 1), CashbackTransaction.TransactionType.GRANT, new BigDecimal("500.00"));
        CashbackTransaction tx2 = new CashbackTransaction("B-2", "C-1", "HDFC", LocalDate.of(2026, 10, 5), CashbackTransaction.TransactionType.GRANT, new BigDecimal("600.00"));
        CashbackTransaction tx3 = new CashbackTransaction("B-3", "C-2", "ICICI", LocalDate.of(2026, 10, 6), CashbackTransaction.TransactionType.GRANT, new BigDecimal("800.00"));
        CashbackTransaction tx4 = new CashbackTransaction("B-1", "C-1", "HDFC", LocalDate.of(2026, 10, 8), CashbackTransaction.TransactionType.REVERSAL, new BigDecimal("500.00"));

        ledgerRepository.recordGrant(tx1);
        ledgerRepository.recordGrant(tx2);
        ledgerRepository.recordGrant(tx3);
        ledgerRepository.recordReversal(tx4);

        assertEquals(4, ledgerRepository.getTransactionHistory().size());

        // Reconstruct from transaction history
        Map<CustomerLedgerRepository.LedgerKey, CustomerLedgerRepository.LedgerAccount> reconstructed =
                CustomerLedgerRepository.reconstructFromTransactions(ledgerRepository.getTransactionHistory());

        CustomerLedgerRepository.LedgerKey key1 = new CustomerLedgerRepository.LedgerKey("C-1", "HDFC", "2026-10");
        CustomerLedgerRepository.LedgerAccount acc1 = reconstructed.get(key1);
        assertNotNull(acc1);
        assertEquals(new BigDecimal("1100.00"), acc1.getCashbackGranted());
        assertEquals(new BigDecimal("500.00"), acc1.getCashbackReversed());
        assertEquals(new BigDecimal("600.00"), acc1.getNetLiability());
        assertEquals(new BigDecimal("900.00"), acc1.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        CustomerLedgerRepository.LedgerKey key2 = new CustomerLedgerRepository.LedgerKey("C-2", "ICICI", "2026-10");
        CustomerLedgerRepository.LedgerAccount acc2 = reconstructed.get(key2);
        assertNotNull(acc2);
        assertEquals(new BigDecimal("800.00"), acc2.getCashbackGranted());
        assertEquals(new BigDecimal("0.00"), acc2.getCashbackReversed());
        assertEquals(new BigDecimal("800.00"), acc2.getNetLiability());
    }
}
