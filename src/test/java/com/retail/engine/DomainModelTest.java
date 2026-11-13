package com.retail.engine;

import com.retail.engine.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Core Domain Entities Test")
public class DomainModelTest {

    @Test
    @DisplayName("CardIssuer entity encapsulates eligibility flags correctly")
    void testCardIssuerEntity() {
        CardIssuer hdfc = new CardIssuer("HDFC", "HDFC Bank Credit Card", true, true);
        assertEquals("HDFC", hdfc.getIssuerCode());
        assertTrue(hdfc.isEmiEligible());
        assertTrue(hdfc.isCashbackEligible());

        CardIssuer sbi = new CardIssuer("SBI", "SBI Card", true, false);
        assertTrue(sbi.isEmiEligible());
        assertFalse(sbi.isCashbackEligible());

        CardIssuer amex = new CardIssuer("AMEX", "American Express", false, false);
        assertFalse(amex.isEmiEligible());
        assertFalse(amex.isCashbackEligible());
    }

    @Test
    @DisplayName("BillItem validates line number and positive price")
    void testBillItemValidation() {
        BillItem item = new BillItem("BILL-0001", 1, ProductCategory.TV, "Smart TV", new BigDecimal("25000.00"));
        assertEquals("BILL-0001", item.getBillId());
        assertEquals(1, item.getLineNo());
        assertEquals(ProductCategory.TV, item.getCategory());
        assertEquals(new BigDecimal("25000.00"), item.getPrice());

        assertThrows(IllegalArgumentException.class, () ->
                new BillItem("BILL-0001", 0, ProductCategory.TV, "TV", new BigDecimal("1000.00")));

        assertThrows(IllegalArgumentException.class, () ->
                new BillItem("BILL-0001", 1, ProductCategory.TV, "TV", new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("Bill header handles optional tenure cleanly")
    void testBillHeader() {
        Bill billWithEmi = new Bill("BILL-0005", "CUST-053", LocalDate.of(2026, 10, 1), "KOTAK", 9);
        assertTrue(billWithEmi.hasTenureRequested());
        assertEquals(9, billWithEmi.getTenureMonths().orElse(0));

        Bill billWithoutEmi = new Bill("BILL-0001", "CUST-047", LocalDate.of(2026, 10, 1), "HDFC", null);
        assertFalse(billWithoutEmi.hasTenureRequested());
        assertTrue(billWithoutEmi.getTenureMonths().isEmpty());
    }

    @Test
    @DisplayName("BundleDiscount calculates combined total accurately")
    void testBundleDiscount() {
        BundleDiscount bundle = new BundleDiscount(new BigDecimal("2000.00"), new BigDecimal("900.00"));
        assertEquals(new BigDecimal("2000.00"), bundle.getTvSoundbarDiscount());
        assertEquals(new BigDecimal("900.00"), bundle.getPhoneCaseDiscount());
        assertEquals(new BigDecimal("2900.00"), bundle.getTotalDiscount());
        assertTrue(bundle.hasDiscount());
    }

    @Test
    @DisplayName("EmiSchedule holds instalment numbers and amounts")
    void testEmiSchedule() {
        EmiSchedule s = new EmiSchedule("BILL-0052", 1, new BigDecimal("3533.00"));
        assertEquals("BILL-0052", s.getBillId());
        assertEquals(1, s.getInstalmentNo());
        assertEquals(new BigDecimal("3533.00"), s.getAmount());
    }
}

