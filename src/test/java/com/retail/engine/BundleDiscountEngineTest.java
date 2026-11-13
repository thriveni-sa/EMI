package com.retail.engine;

import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.*;
import com.retail.engine.repository.BillRepository;
import com.retail.engine.repository.CardRepository;
import com.retail.engine.service.BundleDiscountService;
import com.retail.engine.service.EmiCalculatorService;
import com.retail.engine.service.OfferArbitrationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Bundle Discount Engine Tests")
public class BundleDiscountEngineTest {

    private OfferConfiguration config;
    private BundleDiscountService bundleService;
    private Path baseDir;

    @BeforeEach
    void setUp() {
        baseDir = Paths.get(".").toAbsolutePath().normalize();
        Path configPath = baseDir.resolve("offer_config.csv");
        if (!Files.exists(configPath)) {
            configPath = baseDir.resolve("data").resolve("offer_config.csv");
        }
        config = ConfigLoader.load(configPath);
        bundleService = new BundleDiscountService();
    }

    @Test
    @DisplayName("TV + Soundbar bundle awards flat Rs 2,000.00 discount")
    void testTvSoundbarBundle() {
        List<BillItem> items = List.of(
                new BillItem("BILL-TEST-1", 1, ProductCategory.TV, "43in Smart TV", new BigDecimal("35000.00")),
                new BillItem("BILL-TEST-1", 2, ProductCategory.SOUNDBAR, "2.1 Soundbar", new BigDecimal("8000.00"))
        );

        BundleDiscount discount = bundleService.calculateBundleDiscount(items, config);
        assertEquals(new BigDecimal("2000.00"), discount.getTvSoundbarDiscount());
        assertEquals(BigDecimal.ZERO.setScale(2), discount.getPhoneCaseDiscount());
        assertEquals(new BigDecimal("2000.00"), discount.getTotalDiscount());
    }

    @Test
    @DisplayName("Phone + Case bundle awards 50% discount on the case")
    void testPhoneCaseBundleSingleCase() {
        List<BillItem> items = List.of(
                new BillItem("BILL-TEST-2", 1, ProductCategory.PHONE, "Smartphone 128GB", new BigDecimal("25000.00")),
                new BillItem("BILL-TEST-2", 2, ProductCategory.CASE, "Silicone Case", new BigDecimal("1200.00"))
        );

        BundleDiscount discount = bundleService.calculateBundleDiscount(items, config);
        assertEquals(BigDecimal.ZERO.setScale(2), discount.getTvSoundbarDiscount());
        assertEquals(new BigDecimal("600.00"), discount.getPhoneCaseDiscount());
        assertEquals(new BigDecimal("600.00"), discount.getTotalDiscount());
        assertEquals(new BigDecimal("600.00"), discount.getDiscountForLine(2));
    }

    @Test
    @DisplayName("BILL-0119 Multi-Case Bundle: 50% discount applied to EACH case line item")
    void testMultiCaseBundleBill0119() {
        // Exact items from teacher dataset BILL-0119 (bill_items.csv lines 139-141)
        List<BillItem> items = List.of(
                new BillItem("BILL-0119", 1, ProductCategory.PHONE, "Smartphone 256GB", new BigDecimal("30000.00")),
                new BillItem("BILL-0119", 2, ProductCategory.CASE, "Flip Cover", new BigDecimal("1000.00")),
                new BillItem("BILL-0119", 3, ProductCategory.CASE, "Silicone Case", new BigDecimal("800.00"))
        );

        BundleDiscount discount = bundleService.calculateBundleDiscount(items, config);
        assertEquals(BigDecimal.ZERO.setScale(2), discount.getTvSoundbarDiscount());

        // Case 1: 50% of 1000 = 500.00; Case 2: 50% of 800 = 400.00
        assertEquals(new BigDecimal("500.00"), discount.getDiscountForLine(2));
        assertEquals(new BigDecimal("400.00"), discount.getDiscountForLine(3));
        assertEquals(new BigDecimal("900.00"), discount.getPhoneCaseDiscount());
        assertEquals(new BigDecimal("900.00"), discount.getTotalDiscount());

        BigDecimal gross = items.stream().map(BillItem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(discount.getTotalDiscount());
        assertEquals(new BigDecimal("31800.00"), gross);
        assertEquals(new BigDecimal("30900.00"), net);
    }

    @Test
    @DisplayName("BILL-0066: Rs 20,000 TV + Rs 7,000 Soundbar -> Rs 25,000 net bill amount and fee waiver checked post-discount")
    void testBill0066ProcessingFeeWaiverPostDiscount() {
        // Teacher dataset BILL-0066 items (bill_items.csv lines 77-78)
        List<BillItem> items = List.of(
                new BillItem("BILL-0066", 1, ProductCategory.TV, "55in 4K LED TV", new BigDecimal("20000.00")),
                new BillItem("BILL-0066", 2, ProductCategory.SOUNDBAR, "5.1 Soundbar", new BigDecimal("7000.00"))
        );

        BundleDiscount discount = bundleService.calculateBundleDiscount(items, config);
        assertEquals(new BigDecimal("2000.00"), discount.getTotalDiscount());

        BigDecimal gross = new BigDecimal("27000.00");
        BigDecimal net = gross.subtract(discount.getTotalDiscount());
        assertEquals(new BigDecimal("25000.00"), net);

        // Verify that processing fee waiver is evaluated on Net Amount (25,000.00)
        EmiCalculatorService emiService = new EmiCalculatorService();
        Bill bill = new Bill("BILL-0066", "CUST-010", LocalDate.of(2026, 10, 10), "HDFC", 6);
        CardIssuer hdfc = new CardIssuer("HDFC", "HDFC Bank Credit Card", true, true);

        EmiCalculatorService.EmiResult emi = emiService.evaluateEmi(bill, net, hdfc, config);
        assertTrue(emi.available());
        assertEquals(BigDecimal.ZERO.setScale(2), emi.processingFee(), "Fee must be waived because Net Amount is exactly Rs 25,000.00");
    }

    @Test
    @DisplayName("Missing bundle components: TV only, Soundbar only, or Case only receives zero discount")
    void testMissingBundleComponents() {
        // TV only
        List<BillItem> tvOnly = List.of(
                new BillItem("B1", 1, ProductCategory.TV, "TV", new BigDecimal("45000.00"))
        );
        assertEquals(BigDecimal.ZERO.setScale(2), bundleService.calculateBundleDiscount(tvOnly, config).getTotalDiscount());

        // Soundbar only
        List<BillItem> soundbarOnly = List.of(
                new BillItem("B2", 1, ProductCategory.SOUNDBAR, "Soundbar", new BigDecimal("9000.00"))
        );
        assertEquals(BigDecimal.ZERO.setScale(2), bundleService.calculateBundleDiscount(soundbarOnly, config).getTotalDiscount());

        // Case only
        List<BillItem> caseOnly = List.of(
                new BillItem("B3", 1, ProductCategory.CASE, "Silicone Case", new BigDecimal("999.00"))
        );
        assertEquals(BigDecimal.ZERO.setScale(2), bundleService.calculateBundleDiscount(caseOnly, config).getTotalDiscount());
    }

    @Test
    @DisplayName("No-bundle categories (Laptop, Headphones, Tablet, AC, Fridge) receive zero discount")
    void testNoBundleBills() {
        List<BillItem> items = List.of(
                new BillItem("B4", 1, ProductCategory.LAPTOP, "Laptop", new BigDecimal("65000.00")),
                new BillItem("B4", 2, ProductCategory.HEADPHONES, "Headphones", new BigDecimal("5000.00")),
                new BillItem("B4", 3, ProductCategory.TABLET, "Tablet", new BigDecimal("20000.00")),
                new BillItem("B4", 4, ProductCategory.AC, "AC", new BigDecimal("35000.00")),
                new BillItem("B4", 5, ProductCategory.FRIDGE, "Fridge", new BigDecimal("28000.00"))
        );

        BundleDiscount discount = bundleService.calculateBundleDiscount(items, config);
        assertFalse(discount.hasDiscount());
        assertEquals(BigDecimal.ZERO.setScale(2), discount.getTotalDiscount());
    }

    @Test
    @DisplayName("Simultaneous bundles: TV+Soundbar and Phone+Case in same bill apply together without interference")
    void testSimultaneousBundles() {
        List<BillItem> items = List.of(
                new BillItem("B5", 1, ProductCategory.TV, "TV", new BigDecimal("40000.00")),
                new BillItem("B5", 2, ProductCategory.SOUNDBAR, "Soundbar", new BigDecimal("8000.00")),
                new BillItem("B5", 3, ProductCategory.PHONE, "Phone", new BigDecimal("50000.00")),
                new BillItem("B5", 4, ProductCategory.CASE, "Case", new BigDecimal("2000.00")),
                new BillItem("B5", 5, ProductCategory.MIXER, "Mixer", new BigDecimal("3000.00"))
        );

        BundleDiscount discount = bundleService.calculateBundleDiscount(items, config);
        assertEquals(new BigDecimal("2000.00"), discount.getTvSoundbarDiscount());
        assertEquals(new BigDecimal("1000.00"), discount.getPhoneCaseDiscount());
        assertEquals(new BigDecimal("3000.00"), discount.getTotalDiscount());
    }

    @Test
    @DisplayName("Defensive boundary: Bundle discount can never exceed item or bill amount")
    void testDefensiveDiscountCeiling() {
        // Low cost items where flat 2000 exceeds item total
        List<BillItem> cheapItems = List.of(
                new BillItem("B6", 1, ProductCategory.TV, "Mini TV", new BigDecimal("1000.00")),
                new BillItem("B6", 2, ProductCategory.SOUNDBAR, "Small Soundbar", new BigDecimal("500.00"))
        );

        BundleDiscount discount = bundleService.calculateBundleDiscount(cheapItems, config);
        // Gross is 1500.00, configured discount is 2000.00 -> discount clamped to 1500.00
        assertEquals(new BigDecimal("1500.00"), discount.getTotalDiscount());
        BigDecimal net = new BigDecimal("1500.00").subtract(discount.getTotalDiscount());
        assertEquals(BigDecimal.ZERO.setScale(2), net, "Net bill cannot be negative");
    }
}

