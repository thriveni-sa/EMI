package com.retail.engine;

import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.*;
import com.retail.engine.repository.CustomerLedgerRepository;
import com.retail.engine.service.BundleDiscountService;
import com.retail.engine.service.CashbackService;
import com.retail.engine.service.EmiCalculatorService;
import com.retail.engine.service.OfferArbitrationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Offer Recommendation & Arbitration Engine Tests")
public class OfferArbitrationTest {

    private OfferConfiguration config;
    private CustomerLedgerRepository ledgerRepository;
    private BundleDiscountService bundleService;
    private EmiCalculatorService emiService;
    private CashbackService cashbackService;
    private OfferArbitrationEngine arbitrationEngine;

    private CardIssuer hdfcCard;
    private CardIssuer amexCard;
    private CardIssuer sbiCard;
    private CardIssuer axisCard;
    private CardIssuer kotakCard;

    @BeforeEach
    void setUp() {
        Path configPath = Paths.get("offer_config.csv").toAbsolutePath().normalize();
        config = ConfigLoader.load(configPath);
        ledgerRepository = new CustomerLedgerRepository();
        bundleService = new BundleDiscountService();
        emiService = new EmiCalculatorService();
        cashbackService = new CashbackService(ledgerRepository);
        arbitrationEngine = new OfferArbitrationEngine(bundleService, emiService, cashbackService);

        hdfcCard = new CardIssuer("HDFC", true, true);
        iciciCard = new CardIssuer("ICICI", true, true);
        sbiCard = new CardIssuer("SBI", true, false);     // EMI only
        axisCard = new CardIssuer("AXIS", false, true);   // Cashback only
        kotakCard = new CardIssuer("KOTAK", true, true);
        amexCard = new CardIssuer("AMEX", false, false);  // Ineligible
    }

    private CardIssuer iciciCard;

    @Test
    @DisplayName("BR-16 & BR-17: EMI wins when interest saved > cashback amount")
    void testEmiWinsHigherBenefit() {
        // Rs 40,000 purchase with 9 months tenure
        // Interest saved = 40000 * 15 * 9 / 1200 = 4500.00
        // Cashback = 10% of 40000 = 4000.00 capped at 1500.00
        // EMI (4500.00) > Cashback (1500.00) -> Recommend EMI
        Bill bill = new Bill("BILL-EMI-WINS", "CUST-001", LocalDate.of(2026, 10, 1), "HDFC", 9);
        List<BillItem> items = List.of(
                new BillItem("BILL-EMI-WINS", 1, ProductCategory.LAPTOP, "Laptop", new BigDecimal("40000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertEquals(OfferType.EMI, result.getRecommendedOption());
        assertEquals(new BigDecimal("4500.00"), result.getBenefitAmount());
        assertEquals(new BigDecimal("4500.00"), result.getInterestSaved());
        assertEquals(new BigDecimal("1500.00"), result.getCashbackAmount());
        assertTrue(result.getRationale().contains("EMI recommended"));

        // Verify that because EMI was chosen, cashback cap was NOT consumed
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-001", "HDFC", "2026-10");
        assertEquals(new BigDecimal("1500.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
        assertEquals(0, ledgerRepository.getTransactionHistory().size());
    }

    @Test
    @DisplayName("BR-16 & BR-17: Cashback wins when cashback amount > interest saved")
    void testCashbackWinsHigherBenefit() {
        // Rs 8,000 purchase with 3 months tenure
        // Interest saved = 8000 * 15 * 3 / 1200 = 300.00
        // Cashback = 10% of 8000 = 800.00 (under 1500 cap)
        // Cashback (800.00) > EMI (300.00) -> Recommend CASHBACK
        Bill bill = new Bill("BILL-CB-WINS", "CUST-001", LocalDate.of(2026, 10, 1), "HDFC", 3);
        List<BillItem> items = List.of(
                new BillItem("BILL-CB-WINS", 1, ProductCategory.TABLET, "Tablet", new BigDecimal("8000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertEquals(OfferType.CASHBACK, result.getRecommendedOption());
        assertEquals(new BigDecimal("800.00"), result.getBenefitAmount());
        assertEquals(new BigDecimal("300.00"), result.getInterestSaved());
        assertEquals(new BigDecimal("800.00"), result.getCashbackAmount());
        assertTrue(result.getRationale().contains("CASHBACK recommended"));

        // Verify cashback cap WAS consumed
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-001", "HDFC", "2026-10");
        assertEquals(new BigDecimal("700.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
        assertEquals(1, ledgerRepository.getTransactionHistory().size());
    }

    @Test
    @DisplayName("BR-18 & EC-04: Exact benefit tie breaks strictly to CASHBACK (BILL-0016)")
    void testExactTieBreaksToCashbackBill0016() {
        // KOTAK card, 6-month tenure, Rs 20,000 bill
        // Interest saved = 20000 * 15 * 6 / 1200 = 1500.00
        // Cashback = 10% of 20000 = 2000.00 capped at 1500.00
        // Interest saved (1500.00) == Awarded cashback (1500.00)
        // Deterministic Tie-Breaker -> CASHBACK
        Bill bill = new Bill("BILL-0016", "CUST-016", LocalDate.of(2026, 10, 3), "KOTAK", 6);
        List<BillItem> items = List.of(
                new BillItem("BILL-0016", 1, ProductCategory.TV, "Smart TV", new BigDecimal("20000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, kotakCard, config);

        assertEquals(new BigDecimal("1500.00"), result.getInterestSaved());
        assertEquals(new BigDecimal("1500.00"), result.getCashbackAmount());
        assertEquals(OfferType.CASHBACK, result.getRecommendedOption(), "Tie must break in favor of CASHBACK per BR-18");
        assertEquals(new BigDecimal("1500.00"), result.getBenefitAmount());
        assertTrue(result.getRationale().contains("exact benefit tie"));

        // Verify cap was consumed
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-016", "KOTAK", "2026-10");
        assertEquals(new BigDecimal("0.00"), account.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));
        assertEquals(1, ledgerRepository.getTransactionHistory().size());
    }

    @Test
    @DisplayName("TRAP-04: Non-partner AMEX card recommends NONE (BILL-0088)")
    void testNeitherEligibleAmexBill0088() {
        Bill bill = new Bill("BILL-0088", "CUST-088", LocalDate.of(2026, 10, 12), "AMEX", 6);
        List<BillItem> items = List.of(
                new BillItem("BILL-0088", 1, ProductCategory.PHONE, "Flagship Phone", new BigDecimal("52000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, amexCard, config);

        assertFalse(result.isEmiAvailable());
        assertFalse(result.isCashbackEligible());
        assertEquals(OfferType.NONE, result.getRecommendedOption());
        assertEquals(new BigDecimal("0.00"), result.getBenefitAmount());
        assertTrue(result.getRationale().contains("neither promotional option qualifies"));
    }

    @Test
    @DisplayName("Only EMI eligible card (SBI) recommends EMI even for small amounts")
    void testOnlyEmiEligibleCardSbi() {
        // SBI card has emi_eligible = true, cashback_eligible = false
        Bill bill = new Bill("BILL-SBI-ONLY", "CUST-004", LocalDate.of(2026, 10, 7), "SBI", 3);
        List<BillItem> items = List.of(
                new BillItem("BILL-SBI-ONLY", 1, ProductCategory.TABLET, "Tablet", new BigDecimal("10000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, sbiCard, config);

        assertTrue(result.isEmiAvailable());
        assertFalse(result.isCashbackEligible());
        assertEquals(new BigDecimal("375.00"), result.getInterestSaved());
        assertEquals(new BigDecimal("0.00"), result.getCashbackAmount());
        assertEquals(OfferType.EMI, result.getRecommendedOption());
        assertEquals(new BigDecimal("375.00"), result.getBenefitAmount());
        assertTrue(result.getRationale().contains("sole qualifying promotion"));
    }

    @Test
    @DisplayName("Only EMI eligible when customer's monthly cashback cap is exhausted")
    void testOnlyEmiEligibleWhenCashbackCapExhausted() {
        // Customer on HDFC exhausts Rs 1,500.00 cap first
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-CAP-OUT", "HDFC", "2026-10");
        account.addGrant(new BigDecimal("1500.00"));

        Bill bill = new Bill("BILL-EXHAUSTED", "CUST-CAP-OUT", LocalDate.of(2026, 10, 15), "HDFC", 3);
        List<BillItem> items = List.of(
                new BillItem("BILL-EXHAUSTED", 1, ProductCategory.PHONE, "Phone", new BigDecimal("10000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertTrue(result.isEmiAvailable());
        assertTrue(result.isCashbackEligible());
        assertEquals(new BigDecimal("0.00"), result.getCashbackAmount(), "Cashback cap is exhausted");
        assertEquals(OfferType.EMI, result.getRecommendedOption());
        assertEquals(new BigDecimal("375.00"), result.getBenefitAmount());
    }

    @Test
    @DisplayName("TRAP-06: Only Cashback eligible card (AXIS) awards CASHBACK even when tenure requested (BILL-0099)")
    void testOnlyCashbackEligibleCardAxis() {
        // AXIS card has emi_eligible = false, cashback_eligible = true
        Bill bill = new Bill("BILL-0099", "CUST-013", LocalDate.of(2026, 10, 14), "AXIS", 3);
        List<BillItem> items = List.of(
                new BillItem("BILL-0099", 1, ProductCategory.LAPTOP, "Laptop", new BigDecimal("28000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, axisCard, config);

        assertFalse(result.isEmiAvailable(), "AXIS cannot do EMI");
        assertTrue(result.isCashbackEligible());
        assertEquals(OfferType.CASHBACK, result.getRecommendedOption());
        assertEquals(new BigDecimal("1500.00"), result.getBenefitAmount());
    }

    @Test
    @DisplayName("Only Cashback eligible when customer did not request tenure (blank tenure)")
    void testOnlyCashbackEligibleWhenNoTenureRequested() {
        Bill bill = new Bill("BILL-NO-T", "CUST-1", LocalDate.of(2026, 10, 1), "HDFC", null);
        List<BillItem> items = List.of(
                new BillItem("BILL-NO-T", 1, ProductCategory.TV, "Smart TV", new BigDecimal("30000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertFalse(result.isEmiAvailable(), "Blank tenure means unrequested EMI");
        assertTrue(result.isCashbackEligible());
        assertEquals(OfferType.CASHBACK, result.getRecommendedOption());
        assertEquals(new BigDecimal("1500.00"), result.getBenefitAmount());
    }

    @Test
    @DisplayName("TRAP-01: Only Cashback eligible when amount is below Rs 5,000.00 threshold (BILL-0030)")
    void testOnlyCashbackEligibleWhenBelowMinBill() {
        Bill bill = new Bill("BILL-0030", "CUST-008", LocalDate.of(2026, 10, 5), "HDFC", 3);
        List<BillItem> items = List.of(
                new BillItem("BILL-0030", 1, ProductCategory.CASE, "Case", new BigDecimal("4999.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertFalse(result.isEmiAvailable(), "Amount 4999 < 5000 min threshold");
        assertTrue(result.isCashbackEligible());
        assertEquals(OfferType.CASHBACK, result.getRecommendedOption());
        assertEquals(new BigDecimal("499.90"), result.getBenefitAmount());
    }

    @Test
    @DisplayName("TRAP-05: Invalid tenure 12 on SBI card rejects EMI and recommends NONE (BILL-0096)")
    void testInvalidTenureRejection() {
        Bill bill = new Bill("BILL-0096", "CUST-012", LocalDate.of(2026, 10, 13), "SBI", 12);
        List<BillItem> items = List.of(
                new BillItem("BILL-0096", 1, ProductCategory.LAPTOP, "Laptop", new BigDecimal("36000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, sbiCard, config);

        assertFalse(result.isEmiAvailable(), "Tenure 12 is invalid");
        assertFalse(result.isCashbackEligible(), "SBI is not cashback eligible");
        assertEquals(OfferType.NONE, result.getRecommendedOption());
        assertEquals(new BigDecimal("0.00"), result.getBenefitAmount());
    }

    @Test
    @DisplayName("Capped cashback arbitration: 10% of 60,000 is 6,000 capped to 1500 vs 6,750 interest saved -> EMI wins")
    void testCappedCashbackArbitration() {
        // 60,000 on HDFC with 9 months tenure:
        // Interest saved = 60000 * 15 * 9 / 1200 = 6750.00
        // Gross cashback = 6000.00 capped at 1500.00
        // EMI (6750.00) > Cashback (1500.00) -> EMI wins
        Bill bill = new Bill("BILL-CAP-COMP", "CUST-CAP", LocalDate.of(2026, 10, 1), "HDFC", 9);
        List<BillItem> items = List.of(
                new BillItem("BILL-CAP-COMP", 1, ProductCategory.LAPTOP, "High-End Laptop", new BigDecimal("60000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertEquals(OfferType.EMI, result.getRecommendedOption());
        assertEquals(new BigDecimal("6750.00"), result.getBenefitAmount());
        assertEquals(new BigDecimal("1500.00"), result.getCashbackAmount());
    }

    @Test
    @DisplayName("Processing fee is kept separate and NOT subtracted from EMI benefit during comparison")
    void testProcessingFeeKeptSeparateNotSubtractedFromEmiBenefit() {
        // Principal = 10,000, 3 months tenure:
        // Interest saved = 10000 * 15 * 3 / 1200 = 375.00
        // Processing fee = Rs 199.00 (< 25000)
        // Pre-consume customer cap leaving exactly Rs 300.00
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-FEE-TEST", "HDFC", "2026-10");
        account.addGrant(new BigDecimal("1200.00")); // remaining = 300.00

        Bill bill = new Bill("BILL-FEE-TEST", "CUST-FEE-TEST", LocalDate.of(2026, 10, 5), "HDFC", 3);
        List<BillItem> items = List.of(
                new BillItem("BILL-FEE-TEST", 1, ProductCategory.TABLET, "Tablet", new BigDecimal("10000.00"))
        );

        OfferResult result = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertEquals(new BigDecimal("375.00"), result.getInterestSaved());
        assertEquals(new BigDecimal("199.00"), result.getProcessingFee());
        assertEquals(new BigDecimal("300.00"), result.getCashbackAmount());

        // CRITICAL INVARIANT: If fee were improperly subtracted: 375 - 199 = 176 < 300 -> Cashback would win.
        // But per specification, fee is NOT subtracted from interest saved.
        // 375.00 > 300.00 -> EMI MUST WIN!
        assertEquals(OfferType.EMI, result.getRecommendedOption(), "Processing fee must not be subtracted from EMI comparison benefit");
        assertEquals(new BigDecimal("375.00"), result.getBenefitAmount());
    }

    @Test
    @DisplayName("Transparent Recommendation Breakdown: Verify breakdown record and rationale details")
    void testTransparentRecommendationBreakdown() {
        Bill bill = new Bill("BILL-BREAKDOWN", "CUST-B", LocalDate.of(2026, 10, 2), "KOTAK", 6);
        List<BillItem> items = List.of(
                new BillItem("BILL-BREAKDOWN", 1, ProductCategory.TV, "TV", new BigDecimal("20000.00"))
        );

        OfferArbitrationEngine.RecommendationBreakdown breakdown =
                arbitrationEngine.getRecommendationBreakdown(bill, items, kotakCard, config);

        assertNotNull(breakdown);
        assertEquals("BILL-BREAKDOWN", breakdown.billId());
        assertEquals(new BigDecimal("20000.00"), breakdown.grossAmount());
        assertEquals(new BigDecimal("0.00"), breakdown.bundleDiscount());
        assertEquals(new BigDecimal("20000.00"), breakdown.netBillAmount());
        assertTrue(breakdown.emiAvailable());
        assertEquals(new BigDecimal("1500.00"), breakdown.interestSaved());
        assertEquals(new BigDecimal("199.00"), breakdown.processingFee());
        assertTrue(breakdown.cashbackEligible());
        assertEquals(new BigDecimal("1500.00"), breakdown.awardedCashback());
        assertEquals(OfferType.CASHBACK, breakdown.recommendedOption());
        assertEquals(new BigDecimal("1500.00"), breakdown.benefitAmount());
        assertFalse(breakdown.rationale().isEmpty());
    }
}
