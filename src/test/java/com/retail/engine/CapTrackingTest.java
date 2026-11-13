package com.retail.engine;

import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.Bill;
import com.retail.engine.model.CardIssuer;
import com.retail.engine.repository.CustomerLedgerRepository;
import com.retail.engine.service.CashbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Customer Ledger & Monthly Cashback Cap Tracking Tests")
public class CapTrackingTest {

    private OfferConfiguration config;
    private CustomerLedgerRepository ledgerRepository;
    private CashbackService cashbackService;

    private CardIssuer hdfcCard;
    private CardIssuer axisCard;
    private CardIssuer iciciCard;

    @BeforeEach
    void setUp() {
        Path configPath = Paths.get("offer_config.csv").toAbsolutePath().normalize();
        config = ConfigLoader.load(configPath);
        ledgerRepository = new CustomerLedgerRepository();
        cashbackService = new CashbackService(ledgerRepository);

        hdfcCard = new CardIssuer("HDFC", true, true);
        axisCard = new CardIssuer("AXIS", false, true);
        iciciCard = new CardIssuer("ICICI", true, true);
    }

    @Test
    @DisplayName("Specific Requirement: Rs 1400 previous cashback usage followed by Rs 3000 purchase")
    void testRs1400PreviousUsageFollowedByRs3000Purchase() {
        String custId = "CUST-SPECIAL";
        String card = "HDFC";
        LocalDate date = LocalDate.of(2026, 10, 5);

        // Pre-seed ledger with 1400.00 usage in 2026-10
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount(custId, card, "2026-10");
        account.addGrant(new BigDecimal("1400.00"));

        assertEquals(new BigDecimal("1400.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("100.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Now customer makes Rs 3,000.00 purchase (10% is Rs 300.00)
        Bill bill = new Bill("BILL-SPEC-3000", custId, date, card, null);
        CashbackService.CashbackResult result = cashbackService.evaluateCashback(bill, new BigDecimal("3000.00"), hdfcCard, config);

        assertTrue(result.eligible());
        assertEquals(new BigDecimal("300.00"), result.grossCashback(), "Gross 10% of 3000 is 300.00");
        assertEquals(new BigDecimal("100.00"), result.awardedCashback(), "Awarded cashback must be capped at remaining Rs 100.00");

        // Commit transaction
        cashbackService.commitCashback(bill, result.awardedCashback());

        assertEquals(new BigDecimal("1500.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("0.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
        assertEquals(new BigDecimal("1500.00"), account.getNetLiability());
    }

    @Test
    @DisplayName("EC-02: Progressive cashback cap saturation: BILL-0012, BILL-0062, BILL-0102")
    void testEc02ProgressiveCapSaturation() {
        String custId = "CUST-003";
        String card = "AXIS";

        // Bill 1 (BILL-0012): Rs 9,000 on 2026-10-02 -> 10% = Rs 900.00
        Bill bill1 = new Bill("BILL-0012", custId, LocalDate.of(2026, 10, 2), card, null);
        CashbackService.CashbackResult res1 = cashbackService.evaluateCashback(bill1, new BigDecimal("9000.00"), axisCard, config);
        assertEquals(new BigDecimal("900.00"), res1.awardedCashback());
        cashbackService.commitCashback(bill1, res1.awardedCashback());

        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount(custId, card, "2026-10");
        assertEquals(new BigDecimal("900.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("600.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Bill 2 (BILL-0062): Rs 5,000 on 2026-10-09 -> 10% = Rs 500.00
        Bill bill2 = new Bill("BILL-0062", custId, LocalDate.of(2026, 10, 9), card, null);
        CashbackService.CashbackResult res2 = cashbackService.evaluateCashback(bill2, new BigDecimal("5000.00"), axisCard, config);
        assertEquals(new BigDecimal("500.00"), res2.awardedCashback());
        cashbackService.commitCashback(bill2, res2.awardedCashback());

        assertEquals(new BigDecimal("1400.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("100.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Bill 3 (BILL-0102): Rs 3,000 on 2026-10-16 -> 10% = Rs 300.00, but cap remaining = Rs 100.00
        Bill bill3 = new Bill("BILL-0102", custId, LocalDate.of(2026, 10, 16), card, null);
        CashbackService.CashbackResult res3 = cashbackService.evaluateCashback(bill3, new BigDecimal("3000.00"), axisCard, config);
        assertEquals(new BigDecimal("100.00"), res3.awardedCashback());
        cashbackService.commitCashback(bill3, res3.awardedCashback());

        assertEquals(new BigDecimal("1500.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("0.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Bill 4: Subsequent purchase in the same month earns Rs 0.00
        Bill bill4 = new Bill("BILL-0150", custId, LocalDate.of(2026, 10, 20), card, null);
        CashbackService.CashbackResult res4 = cashbackService.evaluateCashback(bill4, new BigDecimal("8000.00"), axisCard, config);
        assertEquals(new BigDecimal("0.00"), res4.awardedCashback(), "Cap exhausted: cashback must be 0.00");
    }

    @Test
    @DisplayName("BR-20: Cashback cap restoration after eligible reversal")
    void testCapRestorationAfterEligibleReversal() {
        String custId = "CUST-RESTORE";
        String card = "HDFC";
        LocalDate date = LocalDate.of(2026, 10, 10);

        // 1. Initial purchase earns full Rs 1,500.00 cap
        Bill bill = new Bill("BILL-FULL-CAP", custId, date, card, null);
        CashbackService.CashbackResult res = cashbackService.evaluateCashback(bill, new BigDecimal("15000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("1500.00"), res.awardedCashback());
        cashbackService.commitCashback(bill, res.awardedCashback());

        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount(custId, card, "2026-10");
        assertEquals(new BigDecimal("1500.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("0.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
        assertEquals(new BigDecimal("1500.00"), account.getNetLiability());

        // 2. Return processed within statutory window reverses Rs 1,500.00
        account.addReversal(new BigDecimal("1500.00"));

        assertEquals(new BigDecimal("1500.00"), account.getCashbackGranted());
        assertEquals(new BigDecimal("1500.00"), account.getCashbackReversed());
        assertEquals(new BigDecimal("0.00"), account.getNetLiability());
        assertEquals(new BigDecimal("1500.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()),
                "Full Rs 1500.00 cap must be restored after eligible reversal");

        // 3. Customer makes another purchase in the same month and successfully earns cashback again
        Bill nextBill = new Bill("BILL-POST-RETURN", custId, LocalDate.of(2026, 10, 15), card, null);
        CashbackService.CashbackResult nextRes = cashbackService.evaluateCashback(nextBill, new BigDecimal("6000.00"), hdfcCard, config);
        assertTrue(nextRes.eligible());
        assertEquals(new BigDecimal("600.00"), nextRes.awardedCashback(), "Customer can earn cashback again after cap restoration");
    }

    @Test
    @DisplayName("Cap Isolation: Multiple customers have independent Rs 1,500.00 caps")
    void testMultipleCustomersIsolation() {
        String card = "HDFC";
        String month = "2026-10";

        // Customer A hits cap
        CustomerLedgerRepository.LedgerAccount accA = ledgerRepository.getOrCreateAccount("CUST-A", card, month);
        accA.addGrant(new BigDecimal("1500.00"));
        assertEquals(new BigDecimal("0.00"), accA.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Customer B has completely fresh cap
        CustomerLedgerRepository.LedgerAccount accB = ledgerRepository.getOrCreateAccount("CUST-B", card, month);
        assertEquals(new BigDecimal("1500.00"), accB.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        Bill billB = new Bill("BILL-B", "CUST-B", LocalDate.of(2026, 10, 12), card, null);
        CashbackService.CashbackResult resB = cashbackService.evaluateCashback(billB, new BigDecimal("10000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("1000.00"), resB.awardedCashback());
    }

    @Test
    @DisplayName("Cap Isolation: Multiple issuers have independent Rs 1,500.00 caps for same customer")
    void testMultipleIssuersIsolation() {
        String cust = "CUST-MULTI-CARD";
        String month = "2026-10";

        // Hits cap on HDFC
        CustomerLedgerRepository.LedgerAccount accHdfc = ledgerRepository.getOrCreateAccount(cust, "HDFC", month);
        accHdfc.addGrant(new BigDecimal("1500.00"));
        assertEquals(new BigDecimal("0.00"), accHdfc.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Same customer has fresh cap on ICICI
        CustomerLedgerRepository.LedgerAccount accIcici = ledgerRepository.getOrCreateAccount(cust, "ICICI", month);
        assertEquals(new BigDecimal("1500.00"), accIcici.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        Bill billIcici = new Bill("BILL-ICICI", cust, LocalDate.of(2026, 10, 12), "ICICI", null);
        CashbackService.CashbackResult resIcici = cashbackService.evaluateCashback(billIcici, new BigDecimal("10000.00"), iciciCard, config);
        assertEquals(new BigDecimal("1000.00"), resIcici.awardedCashback());
    }

    @Test
    @DisplayName("Cap Isolation: Month boundaries reset the cap to fresh Rs 1,500.00")
    void testMonthBoundaryIsolation() {
        String cust = "CUST-MONTH";
        String card = "HDFC";

        // October 2026 hits cap
        CustomerLedgerRepository.LedgerAccount accOct = ledgerRepository.getOrCreateAccount(cust, card, "2026-10");
        accOct.addGrant(new BigDecimal("1500.00"));
        assertEquals(new BigDecimal("0.00"), accOct.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // November 2026 starts with fresh cap
        CustomerLedgerRepository.LedgerAccount accNov = ledgerRepository.getOrCreateAccount(cust, card, "2026-11");
        assertEquals(new BigDecimal("1500.00"), accNov.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        Bill billNov = new Bill("BILL-NOV", cust, LocalDate.of(2026, 11, 2), card, null);
        CashbackService.CashbackResult resNov = cashbackService.evaluateCashback(billNov, new BigDecimal("12000.00"), hdfcCard, config);
        assertEquals(new BigDecimal("1200.00"), resNov.awardedCashback());
    }
}
