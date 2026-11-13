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

@DisplayName("ECE-035 No-Cost EMI Engine Deep-Dive Tests")
public class EmiEngineTest {

    private OfferConfiguration config;
    private EmiCalculatorService emiService;
    private BundleDiscountService bundleService;
    private CashbackService cashbackService;
    private OfferArbitrationEngine arbitrationEngine;

    private CardIssuer hdfcCard;
    private CardIssuer amexCard;
    private CardIssuer sbiCard;
    private CardIssuer iciciCard;
    private CardIssuer axisCard;

    @BeforeEach
    void setUp() {
        Path configPath = Paths.get("offer_config.csv").toAbsolutePath().normalize();
        config = ConfigLoader.load(configPath);
        emiService = new EmiCalculatorService();
        bundleService = new BundleDiscountService();
        cashbackService = new CashbackService(new CustomerLedgerRepository());
        arbitrationEngine = new OfferArbitrationEngine(bundleService, emiService, cashbackService);

        hdfcCard = new CardIssuer("HDFC", true, true);
        iciciCard = new CardIssuer("ICICI", true, true);
        sbiCard = new CardIssuer("SBI", true, false);      // EMI only
        axisCard = new CardIssuer("AXIS", false, true);    // Cashback only
        amexCard = new CardIssuer("AMEX", false, false);   // Ineligible
    }

    @Test
    @DisplayName("BR-02 & TRAP-04, TRAP-06: EMI is available only for cards with emi_eligible=true")
    void testCardNotEligibleForEmi() {
        // AXIS: emi_eligible = false
        Bill axisBill = new Bill("BILL-TEST-AXIS", "CUST-1", LocalDate.of(2026, 3, 1), "AXIS", 6);
        EmiCalculatorService.EmiResult axisResult = emiService.evaluateEmi(axisBill, new BigDecimal("15000.00"), axisCard, config);
        assertFalse(axisResult.available());
        assertEquals(new BigDecimal("0.00"), axisResult.interestSaved());

        // AMEX: emi_eligible = false
        Bill amexBill = new Bill("BILL-TEST-AMEX", "CUST-1", LocalDate.of(2026, 3, 1), "AMEX", 6);
        EmiCalculatorService.EmiResult amexResult = emiService.evaluateEmi(amexBill, new BigDecimal("15000.00"), amexCard, config);
        assertFalse(amexResult.available());

        // Null card
        Bill nullCardBill = new Bill("BILL-TEST-NULL", "CUST-1", LocalDate.of(2026, 3, 1), "UNKNOWN", 6);
        EmiCalculatorService.EmiResult nullCardResult = emiService.evaluateEmi(nullCardBill, new BigDecimal("15000.00"), null, config);
        assertFalse(nullCardResult.available());
    }

    @Test
    @DisplayName("BR-04: Blank / unrequested tenure results in emi_available=false and zeroed EMI fields")
    void testNoTenureRequested() {
        Bill bill = new Bill("BILL-NO-TENURE", "CUST-1", LocalDate.of(2026, 3, 1), "HDFC", null);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("15000.00"), hdfcCard, config);

        assertFalse(result.available(), "When tenure is blank, customer did not request EMI");
        assertNull(result.tenureMonths());
        assertEquals(new BigDecimal("0.00"), result.instalment());
        assertEquals(new BigDecimal("0.00"), result.lastInstalment());
        assertEquals(new BigDecimal("0.00"), result.processingFee());
        assertEquals(new BigDecimal("0.00"), result.interestSaved());
    }

    @Test
    @DisplayName("BR-04 & TRAP-05: Invalid tenure 12 months is rejected (BILL-0096)")
    void testInvalidTenure12Months() {
        Bill bill = new Bill("BILL-0096", "CUST-008", LocalDate.of(2026, 3, 15), "HDFC", 12);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("32000.00"), hdfcCard, config);
        assertFalse(result.available());
        assertNull(result.tenureMonths());
    }

    @Test
    @DisplayName("BR-04: Only tenures 3, 6, 9 are permissible; all other integer tenures rejected")
    void testPermissibleAndImpermissibleTenures() {
        for (int t : List.of(3, 6, 9)) {
            Bill bill = new Bill("BILL-T-" + t, "CUST-1", LocalDate.of(2026, 3, 1), "HDFC", t);
            EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("12000.00"), hdfcCard, config);
            assertTrue(result.available());
            assertEquals(t, result.tenureMonths());
        }

        for (int t : List.of(1, 2, 4, 5, 7, 8, 10, 11, 24, 36)) {
            Bill bill = new Bill("BILL-BAD-" + t, "CUST-1", LocalDate.of(2026, 3, 1), "HDFC", t);
            EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("12000.00"), hdfcCard, config);
            assertFalse(result.available(), "Tenure " + t + " must be rejected");
            assertNull(result.tenureMonths());
        }
    }

    @Test
    @DisplayName("BR-03 & TRAP-01: Below minimum threshold Rs 4,999.00 rejects EMI (BILL-0030)")
    void testBelowMinimumThreshold() {
        Bill bill = new Bill("BILL-0030", "CUST-030", LocalDate.of(2026, 3, 5), "HDFC", 3);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("4999.00"), hdfcCard, config);
        assertFalse(result.available());
        assertEquals(new BigDecimal("0.00"), result.interestSaved());
    }

    @Test
    @DisplayName("BR-03 & EC-01: Exact minimum threshold Rs 5,000.00 qualifies for EMI (BILL-0026)")
    void testExactMinimumThreshold() {
        Bill bill = new Bill("BILL-0026", "CUST-026", LocalDate.of(2026, 3, 5), "HDFC", 3);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("5000.00"), hdfcCard, config);
        assertTrue(result.available());
        assertEquals(3, result.tenureMonths());
        // 5000 * 15 * 3 / 1200 = 187.50
        assertEquals(new BigDecimal("187.50"), result.interestSaved());
        // < 25000, fee charged = 199.00
        assertEquals(new BigDecimal("199.00"), result.processingFee());
    }

    @Test
    @DisplayName("BR-09 & TRAP-02: Bill amount Rs 24,999.00 incurs Rs 199.00 processing fee (BILL-0053)")
    void testProcessingFeeBelow25000() {
        Bill bill = new Bill("BILL-0053", "CUST-053", LocalDate.of(2026, 3, 8), "HDFC", 6);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("24999.00"), hdfcCard, config);
        assertTrue(result.available());
        assertEquals(new BigDecimal("199.00"), result.processingFee());
        // 24999 * 15 * 6 / 1200 = 1874.925 -> 1874.93
        assertEquals(new BigDecimal("1874.93"), result.interestSaved());
    }

    @Test
    @DisplayName("BR-10 & EC-01: Exact threshold Rs 25,000.00 waives processing fee to Rs 0.00 (BILL-0039)")
    void testProcessingFeeWaiverAt25000() {
        Bill bill = new Bill("BILL-0039", "CUST-039", LocalDate.of(2026, 3, 6), "HDFC", 6);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("25000.00"), hdfcCard, config);
        assertTrue(result.available());
        assertEquals(new BigDecimal("0.00"), result.processingFee());
        // 25000 * 15 * 6 / 1200 = 1875.00
        assertEquals(new BigDecimal("1875.00"), result.interestSaved());
    }

    @Test
    @DisplayName("BR-05: Interest subvention formula: P * 15 * T / 1200 evaluated across all tenures")
    void testInterestSubventionFormulaAcrossTenures() {
        BigDecimal principal = new BigDecimal("40000.00");

        // 3 months: 40000 * 15 * 3 / 1200 = 1500.00
        Bill b3 = new Bill("B-3", "CUST-1", LocalDate.of(2026, 3, 1), "ICICI", 3);
        assertEquals(new BigDecimal("1500.00"), emiService.evaluateEmi(b3, principal, iciciCard, config).interestSaved());

        // 6 months: 40000 * 15 * 6 / 1200 = 3000.00
        Bill b6 = new Bill("B-6", "CUST-1", LocalDate.of(2026, 3, 1), "ICICI", 6);
        assertEquals(new BigDecimal("3000.00"), emiService.evaluateEmi(b6, principal, iciciCard, config).interestSaved());

        // 9 months: 40000 * 15 * 9 / 1200 = 4500.00
        Bill b9 = new Bill("B-9", "CUST-1", LocalDate.of(2026, 3, 1), "ICICI", 9);
        assertEquals(new BigDecimal("4500.00"), emiService.evaluateEmi(b9, principal, iciciCard, config).interestSaved());
    }

    @Test
    @DisplayName("BR-07, BR-08, BR-09, BR-10 & EC-03: Non-divisible EMI ceiling and remainder schedule (BILL-0052)")
    void testNonDivisibleEmiSchedule3Months() {
        // Rs 10,000.00 / 3 months = 3333.33...
        // Ceiling -> Base instalment = 3334.00
        // Last instalment = 10000 - (3334 * 2) = 10000 - 6668 = 3332.00
        // Net amount < 25000 -> Processing fee = 199.00
        // Instalment 1 = 3334.00 + 199.00 = 3533.00
        // Instalment 2 = 3334.00
        // Instalment 3 = 3332.00
        Bill bill = new Bill("BILL-0052", "CUST-052", LocalDate.of(2026, 3, 8), "SBI", 3);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("10000.00"), sbiCard, config);

        assertTrue(result.available());
        assertEquals(new BigDecimal("3334.00"), result.instalment());
        assertEquals(new BigDecimal("3332.00"), result.lastInstalment());
        assertEquals(new BigDecimal("199.00"), result.processingFee());

        List<EmiSchedule> schedule = emiService.generateSchedule("BILL-0052", result);
        assertEquals(3, schedule.size());

        assertEquals(1, schedule.get(0).getInstalmentNo());
        assertEquals(new BigDecimal("3533.00"), schedule.get(0).getAmount());

        assertEquals(2, schedule.get(1).getInstalmentNo());
        assertEquals(new BigDecimal("3334.00"), schedule.get(1).getAmount());

        assertEquals(3, schedule.get(2).getInstalmentNo());
        assertEquals(new BigDecimal("3332.00"), schedule.get(2).getAmount());

        // Total repaid across schedule = 3533 + 3334 + 3332 = 10199.00 (Principal + Fee)
        BigDecimal totalRepaid = schedule.stream()
                .map(EmiSchedule::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("10199.00"), totalRepaid);
    }

    @Test
    @DisplayName("BR-07, BR-08, BR-10: Non-divisible principal 6-month tenure (fee waived >= 25k)")
    void testNonDivisibleEmiSchedule6Months() {
        // Principal = 50,000.00, Tenure = 6
        // 50000 / 6 = 8333.333... -> Base = 8334.00
        // Months 1..5 = 8334.00 * 5 = 41670.00
        // Month 6 = 50000.00 - 41670.00 = 8330.00
        // Fee waived = 0.00 (>= 25000.00)
        Bill bill = new Bill("BILL-50K-6M", "CUST-1", LocalDate.of(2026, 3, 1), "ICICI", 6);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("50000.00"), iciciCard, config);

        assertTrue(result.available());
        assertEquals(new BigDecimal("8334.00"), result.instalment());
        assertEquals(new BigDecimal("8330.00"), result.lastInstalment());
        assertEquals(new BigDecimal("0.00"), result.processingFee());

        List<EmiSchedule> schedule = emiService.generateSchedule("BILL-50K-6M", result);
        assertEquals(6, schedule.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(new BigDecimal("8334.00"), schedule.get(i).getAmount());
        }
        assertEquals(new BigDecimal("8330.00"), schedule.get(5).getAmount());

        BigDecimal totalRepaid = schedule.stream()
                .map(EmiSchedule::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("50000.00"), totalRepaid);
    }

    @Test
    @DisplayName("BR-07, BR-08, BR-09: Non-divisible principal 9-month tenure (BILL-0053)")
    void testNonDivisibleEmiSchedule9Months() {
        // Principal = 24,999.00, Tenure = 9
        // 24999 / 9 = 2777.666... -> Base = 2778.00
        // Months 1..8 = 2778.00 * 8 = 22224.00
        // Month 9 = 24999.00 - 22224.00 = 2775.00
        // Fee = 199.00 (< 25000)
        // Schedule Month 1 = 2778.00 + 199.00 = 2977.00
        Bill bill = new Bill("BILL-0053", "CUST-053", LocalDate.of(2026, 3, 8), "ICICI", 9);
        EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, new BigDecimal("24999.00"), iciciCard, config);

        assertTrue(result.available());
        assertEquals(new BigDecimal("2778.00"), result.instalment());
        assertEquals(new BigDecimal("2775.00"), result.lastInstalment());
        assertEquals(new BigDecimal("199.00"), result.processingFee());

        List<EmiSchedule> schedule = emiService.generateSchedule("BILL-0053", result);
        assertEquals(9, schedule.size());

        assertEquals(new BigDecimal("2977.00"), schedule.get(0).getAmount());
        for (int i = 1; i < 8; i++) {
            assertEquals(new BigDecimal("2778.00"), schedule.get(i).getAmount());
        }
        assertEquals(new BigDecimal("2775.00"), schedule.get(8).getAmount());

        BigDecimal totalRepaid = schedule.stream()
                .map(EmiSchedule::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("25198.00"), totalRepaid); // Principal 24999 + Fee 199
    }

    @Test
    @DisplayName("TRAP-03 & BILL-0066: Fee waiver threshold evaluates strictly POST-bundle discount")
    void testProcessingFeeEvaluatedPostBundleDiscount() {
        // TV (20000) + Soundbar (7000) = Gross 27000.00
        // Bundle discount = 2000.00
        // Net applicable amount = 25000.00
        // Net 25000 >= 25000 -> Fee is WAIVED (0.00)
        Bill bill = new Bill("BILL-0066", "CUST-010", LocalDate.of(2026, 10, 10), "HDFC", 6);
        List<BillItem> items = List.of(
                new BillItem("BILL-0066", 1, ProductCategory.TV, "Smart TV", new BigDecimal("20000.00")),
                new BillItem("BILL-0066", 2, ProductCategory.SOUNDBAR, "Soundbar System", new BigDecimal("7000.00"))
        );

        OfferResult offerResult = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertEquals(new BigDecimal("27000.00"), offerResult.getGrossAmount());
        assertEquals(new BigDecimal("2000.00"), offerResult.getBundleDiscount());
        assertEquals(new BigDecimal("25000.00"), offerResult.getNetBillAmount());
        assertTrue(offerResult.isEmiAvailable());
        assertEquals(new BigDecimal("0.00"), offerResult.getProcessingFee(), "Fee must be waived post-discount");
    }

    @Test
    @DisplayName("Distinction between EMI availability and EMI recommendation (BILL-0026)")
    void testEmiAvailabilityNotConfusedWithRecommendation() {
        // Customer requested tenure 3 on Rs 5,000 HDFC purchase
        // EMI is available: Interest saved = 5000 * 15 * 3 / 1200 = 187.50
        // Cashback is available: 10% of 5000 = 500.00
        // Cashback (500.00) > EMI (187.50) -> Recommended is CASHBACK
        // But emi_available MUST STILL BE TRUE!
        Bill bill = new Bill("BILL-0026", "CUST-026", LocalDate.of(2026, 10, 4), "HDFC", 3);
        List<BillItem> items = List.of(
                new BillItem("BILL-0026", 1, ProductCategory.PHONE, "Budget Smartphone", new BigDecimal("5000.00"))
        );

        OfferResult offerResult = arbitrationEngine.evaluateBill(bill, items, hdfcCard, config);

        assertTrue(offerResult.isEmiAvailable(), "EMI is available because customer requested valid tenure and met threshold");
        assertEquals(3, offerResult.getTenureMonths());
        assertEquals(new BigDecimal("1667.00"), offerResult.getInstalment());
        assertEquals(new BigDecimal("1666.00"), offerResult.getLastInstalment());
        assertEquals(new BigDecimal("199.00"), offerResult.getProcessingFee());
        assertEquals(new BigDecimal("187.50"), offerResult.getInterestSaved());

        assertTrue(offerResult.isCashbackEligible());
        assertEquals(new BigDecimal("500.00"), offerResult.getCashbackAmount());

        assertEquals(OfferType.CASHBACK, offerResult.getRecommendedOption(), "Recommendation must arbitrate to highest benefit");
        assertEquals(new BigDecimal("500.00"), offerResult.getBenefitAmount());
    }

    @Test
    @DisplayName("Invariance Verification: EMI schedule sum strictly equals Principal + Processing Fee for all tenures and amounts")
    void testEmiScheduleSumMatchesPrincipalPlusProcessingFeeAcrossAllPermutations() {
        BigDecimal[] testAmounts = {
                new BigDecimal("5000.00"),
                new BigDecimal("9999.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("24999.00"),
                new BigDecimal("25000.00"),
                new BigDecimal("30900.00"),
                new BigDecimal("73499.00"),
                new BigDecimal("100000.00")
        };
        int[] tenures = {3, 6, 9};

        for (BigDecimal principal : testAmounts) {
            for (int tenure : tenures) {
                Bill bill = new Bill("B-INV-" + tenure, "CUST-INV", LocalDate.of(2026, 3, 1), "HDFC", tenure);
                EmiCalculatorService.EmiResult result = emiService.evaluateEmi(bill, principal, hdfcCard, config);
                assertTrue(result.available());

                List<EmiSchedule> schedule = emiService.generateSchedule(bill.getBillId(), result);
                assertEquals(tenure, schedule.size(), "Schedule size must match tenure months");

                BigDecimal expectedSum = principal.add(result.processingFee());
                BigDecimal actualSum = schedule.stream()
                        .map(EmiSchedule::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                assertEquals(expectedSum, actualSum,
                        String.format("Failed for Principal=%s, Tenure=%d. Expected %s but was %s",
                                principal, tenure, expectedSum, actualSum));

                // Verify first instalment includes fee
                BigDecimal baseInstalment = result.instalment();
                BigDecimal expectedFirst = baseInstalment.add(result.processingFee());
                assertEquals(expectedFirst, schedule.get(0).getAmount());

                // Verify intermediate instalments equal base instalment
                for (int m = 1; m < tenure - 1; m++) {
                    assertEquals(baseInstalment, schedule.get(m).getAmount());
                }

                // Verify last instalment equals result.lastInstalment()
                assertEquals(result.lastInstalment(), schedule.get(tenure - 1).getAmount());
            }
        }
    }
}
