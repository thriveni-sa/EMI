package com.retail.engine;

import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.*;
import com.retail.engine.repository.CustomerLedgerRepository;
import com.retail.engine.service.ReturnProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Return Processing & Clawback Engine Tests")
public class ReturnProcessingEngineTest {

    private OfferConfiguration config;
    private CustomerLedgerRepository ledgerRepository;
    private ReturnProcessingService returnService;

    @BeforeEach
    void setUp() {
        Path configPath = Paths.get("offer_config.csv").toAbsolutePath().normalize();
        config = ConfigLoader.load(configPath);
        ledgerRepository = new CustomerLedgerRepository();
        returnService = new ReturnProcessingService(ledgerRepository);
    }

    private OfferResult createMockOfferResult(String billId, String custId, LocalDate date, String card, OfferType recommended, BigDecimal cashbackAwarded) {
        return new OfferResult(
                billId,
                custId,
                date,
                card,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                true,
                6,
                new BigDecimal("1667.00"),
                new BigDecimal("1665.00"),
                new BigDecimal("199.00"),
                new BigDecimal("750.00"),
                true,
                cashbackAwarded,
                recommended,
                recommended == OfferType.CASHBACK ? cashbackAwarded : new BigDecimal("750.00")
        );
    }

    @Test
    @DisplayName("BR-19, BR-20 & EC-05: Return on Day 7 is within window and reverses cashback (RET-004)")
    void testReturnOnDay7ReversesCashback() {
        // Bill date: 2026-10-03, Return date: 2026-10-10 (7 calendar days)
        LocalDate billDate = LocalDate.of(2026, 10, 3);
        LocalDate returnDate = LocalDate.of(2026, 10, 10);
        Bill bill = new Bill("BILL-0015", "CUST-015", billDate, "HDFC", null);
        ReturnTransaction returnTx = new ReturnTransaction("RET-004", "BILL-0015", 1, returnDate);

        OfferResult evaluatedBill = createMockOfferResult("BILL-0015", "CUST-015", billDate, "HDFC", OfferType.CASHBACK, new BigDecimal("450.00"));

        ReturnLogRecord record = returnService.processReturn(returnTx, bill, evaluatedBill, config);

        assertEquals(7, record.getDaysAfterBill());
        assertTrue(record.isWithinWindow());
        assertEquals(new BigDecimal("450.00"), record.getCashbackReversed());
    }

    @Test
    @DisplayName("BR-21 & EC-05: Return on Day 8 is outside window and rejects reversal (RET-005)")
    void testReturnOnDay8RejectsReversal() {
        // Bill date: 2026-10-03, Return date: 2026-10-11 (8 calendar days)
        LocalDate billDate = LocalDate.of(2026, 10, 3);
        LocalDate returnDate = LocalDate.of(2026, 10, 11);
        Bill bill = new Bill("BILL-0019", "CUST-019", billDate, "HDFC", null);
        ReturnTransaction returnTx = new ReturnTransaction("RET-005", "BILL-0019", 1, returnDate);

        OfferResult evaluatedBill = createMockOfferResult("BILL-0019", "CUST-019", billDate, "HDFC", OfferType.CASHBACK, new BigDecimal("1000.00"));

        ReturnLogRecord record = returnService.processReturn(returnTx, bill, evaluatedBill, config);

        assertEquals(8, record.getDaysAfterBill());
        assertFalse(record.isWithinWindow());
        assertEquals(new BigDecimal("0.00"), record.getCashbackReversed());
    }

    @Test
    @DisplayName("BR-21 & TRAP-08: Late return after 20 days is rejected (RET-014)")
    void testLateReturn20Days() {
        // Bill date: 2026-10-02, Return date: 2026-10-22 (20 calendar days)
        LocalDate billDate = LocalDate.of(2026, 10, 2);
        LocalDate returnDate = LocalDate.of(2026, 10, 22);
        Bill bill = new Bill("BILL-0012", "CUST-003", billDate, "AXIS", null);
        ReturnTransaction returnTx = new ReturnTransaction("RET-014", "BILL-0012", 1, returnDate);

        OfferResult evaluatedBill = createMockOfferResult("BILL-0012", "CUST-003", billDate, "AXIS", OfferType.CASHBACK, new BigDecimal("900.00"));

        ReturnLogRecord record = returnService.processReturn(returnTx, bill, evaluatedBill, config);

        assertEquals(20, record.getDaysAfterBill());
        assertFalse(record.isWithinWindow());
        assertEquals(new BigDecimal("0.00"), record.getCashbackReversed());
    }

    @Test
    @DisplayName("TRAP-09: Merchandise return on EMI purchase has zero cashback clawback (RET-003)")
    void testReturnOnEmiBillHasZeroClawback() {
        // Bill date: 2026-10-07, Return date: 2026-10-09 (2 days, but EMI purchase)
        LocalDate billDate = LocalDate.of(2026, 10, 7);
        LocalDate returnDate = LocalDate.of(2026, 10, 9);
        Bill bill = new Bill("BILL-0052", "CUST-004", billDate, "SBI", 3);
        ReturnTransaction returnTx = new ReturnTransaction("RET-003", "BILL-0052", 1, returnDate);

        OfferResult evaluatedBill = createMockOfferResult("BILL-0052", "CUST-004", billDate, "SBI", OfferType.EMI, BigDecimal.ZERO);

        ReturnLogRecord record = returnService.processReturn(returnTx, bill, evaluatedBill, config);

        assertEquals(2, record.getDaysAfterBill());
        assertTrue(record.isWithinWindow());
        assertEquals(new BigDecimal("0.00"), record.getCashbackReversed(), "Returns on EMI bills must have 0 cashback reversal");
    }

    @Test
    @DisplayName("Return on bill with zero cashback awarded produces zero reversal")
    void testReturnOnZeroCashbackAwarded() {
        LocalDate billDate = LocalDate.of(2026, 10, 5);
        LocalDate returnDate = LocalDate.of(2026, 10, 8);
        Bill bill = new Bill("BILL-ZERO-CB", "CUST-001", billDate, "HDFC", null);
        ReturnTransaction returnTx = new ReturnTransaction("RET-ZERO", "BILL-ZERO-CB", 1, returnDate);

        OfferResult evaluatedBill = createMockOfferResult("BILL-ZERO-CB", "CUST-001", billDate, "HDFC", OfferType.NONE, BigDecimal.ZERO);

        ReturnLogRecord record = returnService.processReturn(returnTx, bill, evaluatedBill, config);

        assertEquals(3, record.getDaysAfterBill());
        assertTrue(record.isWithinWindow());
        assertEquals(new BigDecimal("0.00"), record.getCashbackReversed());
    }

    @Test
    @DisplayName("Duplicate return command for same returnId is idempotently handled")
    void testDuplicateReturnProcessing() {
        LocalDate billDate = LocalDate.of(2026, 10, 2);
        LocalDate returnDate = LocalDate.of(2026, 10, 5);
        Bill bill = new Bill("BILL-DUP", "CUST-DUP", billDate, "HDFC", null);
        ReturnTransaction returnTx = new ReturnTransaction("RET-DUP", "BILL-DUP", 1, returnDate);

        OfferResult evaluatedBill = createMockOfferResult("BILL-DUP", "CUST-DUP", billDate, "HDFC", OfferType.CASHBACK, new BigDecimal("600.00"));

        ReturnLogRecord record1 = returnService.processReturn(returnTx, bill, evaluatedBill, config);
        assertEquals(new BigDecimal("600.00"), record1.getCashbackReversed());

        // Process same returnId a second time
        ReturnLogRecord record2 = returnService.processReturn(returnTx, bill, evaluatedBill, config);
        assertEquals(new BigDecimal("600.00"), record2.getCashbackReversed());

        // Verify ledger has only 1 reversal recorded
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-DUP", "HDFC", "2026-10");
        assertEquals(new BigDecimal("600.00"), account.getCashbackReversed());
    }

    @Test
    @DisplayName("Multiple return items on same bill: first return reverses cashback, subsequent item return reverses 0.00")
    void testMultipleReturnedItemsOnSameBill() {
        LocalDate billDate = LocalDate.of(2026, 10, 2);
        Bill bill = new Bill("BILL-MULTI-ITEM", "CUST-M", billDate, "HDFC", null);
        OfferResult evaluatedBill = createMockOfferResult("BILL-MULTI-ITEM", "CUST-M", billDate, "HDFC", OfferType.CASHBACK, new BigDecimal("800.00"));

        // Return Item 1
        ReturnTransaction retLine1 = new ReturnTransaction("RET-ITEM-1", "BILL-MULTI-ITEM", 1, LocalDate.of(2026, 10, 5));
        ReturnLogRecord rec1 = returnService.processReturn(retLine1, bill, evaluatedBill, config);
        assertEquals(new BigDecimal("800.00"), rec1.getCashbackReversed());

        // Return Item 2 on the same bill
        ReturnTransaction retLine2 = new ReturnTransaction("RET-ITEM-2", "BILL-MULTI-ITEM", 2, LocalDate.of(2026, 10, 6));
        ReturnLogRecord rec2 = returnService.processReturn(retLine2, bill, evaluatedBill, config);
        assertEquals(new BigDecimal("0.00"), rec2.getCashbackReversed(), "Subsequent return on same bill must not double-reverse cashback");

        // Total reversal in ledger must be exactly 800.00
        CustomerLedgerRepository.LedgerAccount account = ledgerRepository.getOrCreateAccount("CUST-M", "HDFC", "2026-10");
        assertEquals(new BigDecimal("800.00"), account.getCashbackReversed());
    }

    @Test
    @DisplayName("Cross-month return restores cap in the ORIGINAL BILL'S month bucket, not the return's month")
    void testCrossMonthReturnRestoresOriginalBillMonthBucket() {
        String custId = "CUST-CROSS";
        String card = "HDFC";

        // Bill purchased near end of month: 2026-10-30
        LocalDate billDate = LocalDate.of(2026, 10, 30);
        Bill bill = new Bill("BILL-CROSS", custId, billDate, card, null);

        // Pre-populate customer ledger in October with 1500 grant (cap exhausted in October)
        CustomerLedgerRepository.LedgerAccount octAccount = ledgerRepository.getOrCreateAccount(custId, card, "2026-10");
        octAccount.addGrant(new BigDecimal("1500.00"));
        assertEquals(new BigDecimal("0.00"), octAccount.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()));

        // Return occurs in November: 2026-11-03 (4 calendar days -> within 7-day window)
        LocalDate returnDate = LocalDate.of(2026, 11, 3);
        ReturnTransaction returnTx = new ReturnTransaction("RET-CROSS", "BILL-CROSS", 1, returnDate);

        OfferResult evaluatedBill = createMockOfferResult("BILL-CROSS", custId, billDate, card, OfferType.CASHBACK, new BigDecimal("700.00"));

        ReturnLogRecord record = returnService.processReturn(returnTx, bill, evaluatedBill, config);

        assertEquals(4, record.getDaysAfterBill());
        assertTrue(record.isWithinWindow());
        assertEquals(new BigDecimal("700.00"), record.getCashbackReversed());

        // CRITICAL CHECK: The reversal MUST be recorded in the OCTOBER (2026-10) bucket!
        assertEquals(new BigDecimal("700.00"), octAccount.getCashbackReversed(), "Reversal must restore original bill's month");
        assertEquals(new BigDecimal("700.00"), octAccount.getRemainingCap(config.getCashbackCapPerCardCustomerMonth()), "Cap in October must be restored to 700.00");

        // The November bucket should have NO reversal activity from this return!
        CustomerLedgerRepository.LedgerAccount novAccount = ledgerRepository.getAccounts().get(
                new CustomerLedgerRepository.LedgerKey(custId, card, "2026-11")
        );
        if (novAccount != null) {
            assertEquals(new BigDecimal("0.00"), novAccount.getCashbackReversed());
        }
    }
}
