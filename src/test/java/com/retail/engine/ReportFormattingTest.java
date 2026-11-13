package com.retail.engine;

import com.retail.engine.model.*;
import com.retail.engine.service.ReportExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Output & Reporting Module Format Tests")
public class ReportFormattingTest {

    private ReportExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new ReportExportService();
    }

    @Test
    @DisplayName("offer_summary.csv: Exact 17 headers, format, and no currency symbols")
    void testOfferSummaryHeaderAndFormatting(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("offer_summary.csv");

        OfferResult result = new OfferResult(
                "BILL-TEST-01",
                "CUST-001",
                LocalDate.of(2026, 10, 1),
                "HDFC",
                new BigDecimal("25000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("23000.00"),
                true,
                6,
                new BigDecimal("3834.00"),
                new BigDecimal("3830.00"),
                new BigDecimal("199.00"),
                new BigDecimal("1725.00"),
                true,
                new BigDecimal("1500.00"),
                OfferType.EMI,
                new BigDecimal("1725.00"),
                "EMI recommended"
        );
        exportService.recordOfferResult(result);
        exportService.exportOfferSummary(reportFile);

        assertTrue(Files.exists(reportFile));
        List<String> lines = Files.readAllLines(reportFile);
        assertEquals(2, lines.size(), "Must contain exactly header and 1 data row");

        String expectedHeader = "bill_id,customer_id,bill_date,card_issuer,gross_amount,bundle_discount,bill_amount," +
                "emi_available,tenure_months,instalment,last_instalment,processing_fee,interest_saved," +
                "cashback_eligible,cashback_amount,recommended_option,benefit_amount";
        assertEquals(expectedHeader, lines.get(0));

        String expectedRow = "BILL-TEST-01,CUST-001,2026-10-01,HDFC,25000.00,2000.00,23000.00,true,6,3834.00,3830.00,199.00,1725.00,true,1500.00,EMI,1725.00";
        assertEquals(expectedRow, lines.get(1));

        // Ensure zero currency symbols (Rs, $) or explanatory text
        for (String line : lines) {
            assertFalse(line.contains("Rs"));
            assertFalse(line.contains("$"));
            assertFalse(line.contains("₹"));
        }
    }

    @Test
    @DisplayName("offer_summary.csv: Unrequested / blank tenure formats properly as ,,")
    void testOfferSummaryBlankTenureFormatting(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("offer_summary.csv");

        OfferResult result = new OfferResult(
                "BILL-TEST-BLANK",
                "CUST-002",
                LocalDate.of(2026, 10, 2),
                "HDFC",
                new BigDecimal("8000.00"),
                BigDecimal.ZERO,
                new BigDecimal("8000.00"),
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                new BigDecimal("800.00"),
                OfferType.CASHBACK,
                new BigDecimal("800.00")
        );
        exportService.recordOfferResult(result);
        exportService.exportOfferSummary(reportFile);

        List<String> lines = Files.readAllLines(reportFile);
        assertEquals(2, lines.size());
        String expectedRow = "BILL-TEST-BLANK,CUST-002,2026-10-02,HDFC,8000.00,0.00,8000.00,false,,0.00,0.00,0.00,0.00,true,800.00,CASHBACK,800.00";
        assertEquals(expectedRow, lines.get(1));
    }

    @Test
    @DisplayName("emi_schedule.csv: Exact 3 headers and amount includes processing fee on Month 1")
    void testEmiScheduleHeaderAndFormatting(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("emi_schedule.csv");

        List<EmiSchedule> schedule = List.of(
                new EmiSchedule("BILL-TEST-01", 1, new BigDecimal("3533.00")),
                new EmiSchedule("BILL-TEST-01", 2, new BigDecimal("3334.00")),
                new EmiSchedule("BILL-TEST-01", 3, new BigDecimal("3332.00"))
        );
        exportService.recordEmiSchedules(schedule);
        exportService.exportEmiSchedule(reportFile);

        assertTrue(Files.exists(reportFile));
        List<String> lines = Files.readAllLines(reportFile);
        assertEquals(4, lines.size(), "Must contain header and 3 instalment rows");

        assertEquals("bill_id,instalment_no,amount", lines.get(0));
        assertEquals("BILL-TEST-01,1,3533.00", lines.get(1));
        assertEquals("BILL-TEST-01,2,3334.00", lines.get(2));
        assertEquals("BILL-TEST-01,3,3332.00", lines.get(3));
    }

    @Test
    @DisplayName("returns_log.csv: Exact 7 headers, boolean lowercase, and command order")
    void testReturnsLogHeaderAndFormatting(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("returns_log.csv");

        ReturnLogRecord r1 = new ReturnLogRecord("RET-001", "BILL-001", 1, LocalDate.of(2026, 10, 5), 2, true, new BigDecimal("800.00"));
        ReturnLogRecord r2 = new ReturnLogRecord("RET-002", "BILL-002", 1, LocalDate.of(2026, 10, 11), 8, false, new BigDecimal("0.00"));

        exportService.recordReturnLog(r1);
        exportService.recordReturnLog(r2);
        exportService.exportReturnsLog(reportFile);

        assertTrue(Files.exists(reportFile));
        List<String> lines = Files.readAllLines(reportFile);
        assertEquals(3, lines.size());

        assertEquals("return_id,bill_id,line_no,return_date,days_after_bill,within_window,cashback_reversed", lines.get(0));
        assertEquals("RET-001,BILL-001,1,2026-10-05,2,true,800.00", lines.get(1));
        assertEquals("RET-002,BILL-002,1,2026-10-11,8,false,0.00", lines.get(2));
    }

    @Test
    @DisplayName("cashback_report.csv: Exact 7 headers, month format YYYY-MM, net liability calculation")
    void testCashbackReportHeaderAndFormatting(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("cashback_report.csv");

        List<CashbackReport> reports = List.of(
                new CashbackReport("2026-10", "CUST-003", "AXIS", new BigDecimal("1500.00"), new BigDecimal("0.00"), new BigDecimal("1500.00"), new BigDecimal("0.00")),
                new CashbackReport("2026-10", "CUST-047", "HDFC", new BigDecimal("1500.00"), new BigDecimal("640.10"), new BigDecimal("859.90"), new BigDecimal("640.10"))
        );

        exportService.exportCashbackReport(reportFile, reports);

        assertTrue(Files.exists(reportFile));
        List<String> lines = Files.readAllLines(reportFile);
        assertEquals(3, lines.size());

        assertEquals("month,customer_id,card_issuer,cashback_granted,cashback_reversed,net_liability,cap_remaining", lines.get(0));
        assertEquals("2026-10,CUST-003,AXIS,1500.00,0.00,1500.00,0.00", lines.get(1));
        assertEquals("2026-10,CUST-047,HDFC,1500.00,640.10,859.90,640.10", lines.get(2));
    }

    @Test
    @DisplayName("Deterministic overwrite: Rerunning export on same file does NOT append duplicate rows")
    void testDeterministicOverwriteNoAppendedDuplicates(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("offer_summary.csv");

        OfferResult result = new OfferResult(
                "BILL-DUP-TEST", "CUST-1", LocalDate.of(2026, 10, 1), "HDFC",
                new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("10000.00"),
                false, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                true, new BigDecimal("1000.00"), OfferType.CASHBACK, new BigDecimal("1000.00")
        );

        exportService.recordOfferResult(result);
        exportService.exportOfferSummary(reportFile);
        assertEquals(2, Files.readAllLines(reportFile).size());

        // Export again to the same file without clearing -> should overwrite, not append
        exportService.exportOfferSummary(reportFile);
        assertEquals(2, Files.readAllLines(reportFile).size(), "Subsequent export must overwrite without duplicating rows");

        // Clear and add new result -> size remains exact
        exportService.clear();
        assertEquals(0, exportService.getOfferResults().size());
    }
}

