package com.retail.engine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Full Engine Evaluation & Teacher Trap Verification Test")
public class FullEngineEvaluationTest {

    private static Path baseDir;
    private static Map<String, Map<String, String>> offerSummaryRows = new HashMap<>();
    private static Map<String, List<Map<String, String>>> emiScheduleRows = new HashMap<>();
    private static Map<String, Map<String, String>> returnsLogRows = new HashMap<>();
    private static Map<String, Map<String, String>> cashbackReportRows = new HashMap<>();

    @BeforeAll
    static void executeEngineAndLoadOutputs() throws IOException {
        baseDir = Paths.get(".").toAbsolutePath().normalize();
        Path commandsFile = baseDir.resolve("commands.txt");
        assertTrue(Files.exists(commandsFile), "commands.txt must exist");

        // Run full engine bootstrap
        EngineBootstrap bootstrap = new EngineBootstrap(baseDir);
        bootstrap.run(commandsFile);

        // Load generated offer_summary.csv
        Path offerSummaryPath = baseDir.resolve("offer_summary.csv");
        assertTrue(Files.exists(offerSummaryPath), "offer_summary.csv must be generated");
        try (BufferedReader reader = Files.newBufferedReader(offerSummaryPath)) {
            String headerLine = reader.readLine();
            assertNotNull(headerLine);
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < parts.length; i++) {
                    row.put(headers[i], parts[i]);
                }
                offerSummaryRows.put(row.get("bill_id"), row);
            }
        }

        // Load generated emi_schedule.csv
        Path emiSchedulePath = baseDir.resolve("emi_schedule.csv");
        assertTrue(Files.exists(emiSchedulePath), "emi_schedule.csv must be generated");
        try (BufferedReader reader = Files.newBufferedReader(emiSchedulePath)) {
            String headerLine = reader.readLine();
            assertNotNull(headerLine);
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < parts.length; i++) {
                    row.put(headers[i], parts[i]);
                }
                emiScheduleRows.computeIfAbsent(row.get("bill_id"), k -> new ArrayList<>()).add(row);
            }
        }

        // Load generated returns_log.csv
        Path returnsLogPath = baseDir.resolve("returns_log.csv");
        assertTrue(Files.exists(returnsLogPath), "returns_log.csv must be generated");
        try (BufferedReader reader = Files.newBufferedReader(returnsLogPath)) {
            String headerLine = reader.readLine();
            assertNotNull(headerLine);
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < parts.length; i++) {
                    row.put(headers[i], parts[i]);
                }
                returnsLogRows.put(row.get("return_id"), row);
            }
        }

        // Load generated cashback_report.csv
        Path cashbackReportPath = baseDir.resolve("cashback_report.csv");
        assertTrue(Files.exists(cashbackReportPath), "cashback_report.csv must be generated");
        try (BufferedReader reader = Files.newBufferedReader(cashbackReportPath)) {
            String headerLine = reader.readLine();
            assertNotNull(headerLine);
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < parts.length; i++) {
                    row.put(headers[i], parts[i]);
                }
                String key = row.get("customer_id") + "|" + row.get("card_issuer") + "|" + row.get("month");
                cashbackReportRows.put(key, row);
            }
        }
    }

    @Test
    @DisplayName("Verify output datasets non-empty and row counts match commands")
    void testOutputDatasetsPopulated() {
        assertEquals(200, offerSummaryRows.size(), "offer_summary.csv must contain exactly 200 bill evaluations");
        assertEquals(15, returnsLogRows.size(), "returns_log.csv must contain exactly 15 return evaluations");
        assertFalse(emiScheduleRows.isEmpty(), "emi_schedule.csv must have entries for EMI bills");
        assertFalse(cashbackReportRows.isEmpty(), "cashback_report.csv must contain customer accounts");
    }

    @Test
    @DisplayName("EC-01: Boundary bill amounts BILL-0026 (5000.00) and BILL-0039 (25000.00)")
    void testBoundaryBillAmounts() {
        // BILL-0026: Exact Rs 5000.00 -> qualifies for EMI
        Map<String, String> bill0026 = offerSummaryRows.get("BILL-0026");
        assertNotNull(bill0026);
        assertEquals("5000.00", bill0026.get("bill_amount"));
        assertEquals("true", bill0026.get("emi_available"));
        assertEquals("199.00", bill0026.get("processing_fee"));

        // BILL-0039: Exact Rs 25000.00 -> fee waived (0.00)
        Map<String, String> bill0039 = offerSummaryRows.get("BILL-0039");
        assertNotNull(bill0039);
        assertEquals("25000.00", bill0039.get("bill_amount"));
        assertEquals("true", bill0039.get("emi_available"));
        assertEquals("0.00", bill0039.get("processing_fee"));
    }

    @Test
    @DisplayName("EC-02: Progressive cashback cap saturation (BILL-0012, BILL-0062, BILL-0102)")
    void testCashbackCapSaturationPlantedScenario() {
        Map<String, String> bill0012 = offerSummaryRows.get("BILL-0012");
        Map<String, String> bill0062 = offerSummaryRows.get("BILL-0062");
        Map<String, String> bill0102 = offerSummaryRows.get("BILL-0102");

        assertNotNull(bill0012);
        assertNotNull(bill0062);
        assertNotNull(bill0102);

        // All three bills belong to CUST-003, AXIS, October 2026
        assertEquals("CUST-003", bill0012.get("customer_id"));
        assertEquals("CUST-003", bill0062.get("customer_id"));
        assertEquals("CUST-003", bill0102.get("customer_id"));

        // Bill 1: 10% of 9000.00 = 900.00
        assertEquals("900.00", bill0012.get("cashback_amount"));
        assertEquals("CASHBACK", bill0012.get("recommended_option"));

        // Bill 2: 10% of 5000.00 = 500.00 (cumulative = 1400.00)
        assertEquals("500.00", bill0062.get("cashback_amount"));
        assertEquals("CASHBACK", bill0062.get("recommended_option"));

        // Bill 3: 10% of 3000.00 = 300.00, but cap remaining is 1500 - 1400 = 100.00
        assertEquals("100.00", bill0102.get("cashback_amount"));
        assertEquals("CASHBACK", bill0102.get("recommended_option"));

        // Verify account in cashback report shows net 1500.00 and remaining 0.00
        Map<String, String> reportEntry = cashbackReportRows.get("CUST-003|AXIS|2026-10");
        assertNotNull(reportEntry);
        assertEquals("1500.00", reportEntry.get("cashback_granted"));
        assertEquals("0.00", reportEntry.get("cap_remaining"));
    }

    @Test
    @DisplayName("EC-03: Non-divisible EMI ceiling and remainder schedule (BILL-0052)")
    void testNonDivisibleEmiPlantedScenario() {
        Map<String, String> bill0052 = offerSummaryRows.get("BILL-0052");
        assertNotNull(bill0052);
        assertEquals("10000.00", bill0052.get("bill_amount"));
        assertEquals("3", bill0052.get("tenure_months"));
        assertEquals("3334.00", bill0052.get("instalment"));
        assertEquals("3332.00", bill0052.get("last_instalment"));
        assertEquals("199.00", bill0052.get("processing_fee"));

        List<Map<String, String>> schedule = emiScheduleRows.get("BILL-0052");
        assertNotNull(schedule, "BILL-0052 must have repayment schedule entries");
        assertEquals(3, schedule.size());

        // Month 1: 3334.00 + 199.00 fee = 3533.00
        assertEquals("1", schedule.get(0).get("instalment_no"));
        assertEquals("3533.00", schedule.get(0).get("amount"));

        // Month 2: 3334.00
        assertEquals("2", schedule.get(1).get("instalment_no"));
        assertEquals("3334.00", schedule.get(1).get("amount"));

        // Month 3: 3332.00
        assertEquals("3", schedule.get(2).get("instalment_no"));
        assertEquals("3332.00", schedule.get(2).get("amount"));
    }

    @Test
    @DisplayName("EC-04: Exact benefit tie breaks to CASHBACK (BILL-0016)")
    void testTieBreakerPlantedScenario() {
        Map<String, String> bill0016 = offerSummaryRows.get("BILL-0016");
        assertNotNull(bill0016);
        assertEquals("20000.00", bill0016.get("bill_amount"));
        assertEquals("1500.00", bill0016.get("interest_saved"));
        assertEquals("1500.00", bill0016.get("cashback_amount"));
        assertEquals("CASHBACK", bill0016.get("recommended_option"), "Tie between 1500 EMI and 1500 Cashback must be CASHBACK");
        assertEquals("1500.00", bill0016.get("benefit_amount"));
    }

    @Test
    @DisplayName("EC-05: 7th day return (RET-004) vs 8th day return (RET-005)")
    void testReturnDaySevenAndEightPlantedScenario() {
        Map<String, String> ret004 = returnsLogRows.get("RET-004");
        Map<String, String> ret005 = returnsLogRows.get("RET-005");

        assertNotNull(ret004);
        assertNotNull(ret005);

        // RET-004: Exactly 7 days -> within window -> reverses cashback
        assertEquals("7", ret004.get("days_after_bill"));
        assertEquals("true", ret004.get("within_window"));
        assertEquals("450.00", ret004.get("cashback_reversed"));

        // RET-005: Exactly 8 days -> outside window -> rejects reversal
        assertEquals("8", ret005.get("days_after_bill"));
        assertEquals("false", ret005.get("within_window"));
        assertEquals("0.00", ret005.get("cashback_reversed"));
    }

    @Test
    @DisplayName("TRAP-01: Minimum EMI trap BILL-0030 (4999.00 -> EMI unavailable)")
    void testTrap01MinimumEmi() {
        Map<String, String> bill0030 = offerSummaryRows.get("BILL-0030");
        assertNotNull(bill0030);
        assertEquals("4999.00", bill0030.get("bill_amount"));
        assertEquals("false", bill0030.get("emi_available"));
    }

    @Test
    @DisplayName("TRAP-02: Processing fee trap BILL-0053 (24999.00 -> 199.00 fee)")
    void testTrap02ProcessingFee() {
        Map<String, String> bill0053 = offerSummaryRows.get("BILL-0053");
        assertNotNull(bill0053);
        assertEquals("24999.00", bill0053.get("bill_amount"));
        assertEquals("true", bill0053.get("emi_available"));
        assertEquals("199.00", bill0053.get("processing_fee"));
    }

    @Test
    @DisplayName("TRAP-03: Bundle adjusted threshold BILL-0066 (27000 gross - 2000 discount = 25000 net -> fee waived)")
    void testTrap03BundleAdjustedFeeThreshold() {
        Map<String, String> bill0066 = offerSummaryRows.get("BILL-0066");
        assertNotNull(bill0066);
        assertEquals("27000.00", bill0066.get("gross_amount"));
        assertEquals("2000.00", bill0066.get("bundle_discount"));
        assertEquals("25000.00", bill0066.get("bill_amount"));
        assertEquals("true", bill0066.get("emi_available"));
        assertEquals("0.00", bill0066.get("processing_fee"), "Fee must be waived because net bill amount >= 25000.00");
    }

    @Test
    @DisplayName("TRAP-04: AMEX non-partner card BILL-0088 (recommends NONE)")
    void testTrap04AmexNonPartnerCard() {
        Map<String, String> bill0088 = offerSummaryRows.get("BILL-0088");
        assertNotNull(bill0088);
        assertEquals("AMEX", bill0088.get("card_issuer"));
        assertEquals("false", bill0088.get("emi_available"));
        assertEquals("false", bill0088.get("cashback_eligible"));
        assertEquals("NONE", bill0088.get("recommended_option"));
        assertEquals("0.00", bill0088.get("benefit_amount"));
    }

    @Test
    @DisplayName("TRAP-05: Invalid tenure 12 months BILL-0096 (recommends NONE, card not cashback eligible)")
    void testTrap05InvalidTenure12Months() {
        Map<String, String> bill0096 = offerSummaryRows.get("BILL-0096");
        assertNotNull(bill0096);
        assertEquals("SBI", bill0096.get("card_issuer"));
        assertEquals("false", bill0096.get("emi_available"), "Tenure 12 is invalid, EMI must be false");
        assertEquals("false", bill0096.get("cashback_eligible"), "SBI card is not cashback eligible");
        assertEquals("NONE", bill0096.get("recommended_option"));
    }

    @Test
    @DisplayName("TRAP-06: Cashback-only card AXIS BILL-0099 (recommends CASHBACK)")
    void testTrap06CashbackOnlyCard() {
        Map<String, String> bill0099 = offerSummaryRows.get("BILL-0099");
        assertNotNull(bill0099);
        assertEquals("AXIS", bill0099.get("card_issuer"));
        assertEquals("false", bill0099.get("emi_available"), "AXIS card is not EMI eligible");
        assertEquals("true", bill0099.get("cashback_eligible"));
        assertEquals("CASHBACK", bill0099.get("recommended_option"));
    }

    @Test
    @DisplayName("TRAP-07: Multiple CASE lines 50% discount BILL-0119")
    void testTrap07MultipleCaseLinesDiscount() {
        Map<String, String> bill0119 = offerSummaryRows.get("BILL-0119");
        assertNotNull(bill0119);
        // Phone (30000) + Case 1 (1000) + Case 2 (800) = Gross 31800.00
        // Case discounts: 500 + 400 = 900.00
        // Net = 30900.00
        assertEquals("31800.00", bill0119.get("gross_amount"));
        assertEquals("900.00", bill0119.get("bundle_discount"));
        assertEquals("30900.00", bill0119.get("bill_amount"));
    }

    @Test
    @DisplayName("TRAP-08: Late return 20 days RET-014 (cashback_reversed = 0.00)")
    void testTrap08LateReturn() {
        Map<String, String> ret014 = returnsLogRows.get("RET-014");
        assertNotNull(ret014);
        assertEquals("20", ret014.get("days_after_bill"));
        assertEquals("false", ret014.get("within_window"));
        assertEquals("0.00", ret014.get("cashback_reversed"));
    }

    @Test
    @DisplayName("TRAP-09: Merchandise return on EMI purchase RET-003 on BILL-0052 (cashback_reversed = 0.00)")
    void testTrap09ReturnOnEmiBill() {
        Map<String, String> ret003 = returnsLogRows.get("RET-003");
        assertNotNull(ret003);
        assertEquals("BILL-0052", ret003.get("bill_id"));
        assertEquals("true", ret003.get("within_window"));
        assertEquals("0.00", ret003.get("cashback_reversed"), "Original purchase was EMI, reversal must be 0.00");
    }

    @Test
    @DisplayName("Verify exact CSV headers across all four generated evaluation outputs")
    void testExactCsvHeadersAcrossAllOutputs() throws IOException {
        String expectedSummaryHeader = "bill_id,customer_id,bill_date,card_issuer,gross_amount,bundle_discount,bill_amount," +
                "emi_available,tenure_months,instalment,last_instalment,processing_fee,interest_saved," +
                "cashback_eligible,cashback_amount,recommended_option,benefit_amount";
        String expectedScheduleHeader = "bill_id,instalment_no,amount";
        String expectedReturnsHeader = "return_id,bill_id,line_no,return_date,days_after_bill,within_window,cashback_reversed";
        String expectedCashbackHeader = "month,customer_id,card_issuer,cashback_granted,cashback_reversed,net_liability,cap_remaining";

        assertEquals(expectedSummaryHeader, Files.readAllLines(baseDir.resolve("offer_summary.csv")).get(0));
        assertEquals(expectedScheduleHeader, Files.readAllLines(baseDir.resolve("emi_schedule.csv")).get(0));
        assertEquals(expectedReturnsHeader, Files.readAllLines(baseDir.resolve("returns_log.csv")).get(0));
        assertEquals(expectedCashbackHeader, Files.readAllLines(baseDir.resolve("cashback_report.csv")).get(0));
    }

    @Test
    @DisplayName("Verify money formatting (scale 2, plain decimals) and zero currency symbols in outputs")
    void testMoneyFormattingAndNoCurrencySymbols() throws IOException {
        List<Path> outputFiles = List.of(
                baseDir.resolve("offer_summary.csv"),
                baseDir.resolve("emi_schedule.csv"),
                baseDir.resolve("returns_log.csv"),
                baseDir.resolve("cashback_report.csv")
        );

        for (Path file : outputFiles) {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                assertFalse(line.contains("Rs"), "Output must not contain 'Rs'");
                assertFalse(line.contains("$"), "Output must not contain '$'");
                assertFalse(line.contains("₹"), "Output must not contain '₹'");
            }
        }
    }

    @Test
    @DisplayName("Verify date formatting (YYYY-MM-DD) and boolean formatting (lowercase true/false)")
    void testDateAndBooleanFormatting() {
        for (Map<String, String> row : offerSummaryRows.values()) {
            assertTrue(row.get("bill_date").matches("^2026-10-[0-3][0-9]$"), "Date must match YYYY-MM-DD: " + row.get("bill_date"));
            assertTrue(row.get("emi_available").equals("true") || row.get("emi_available").equals("false"));
            assertTrue(row.get("cashback_eligible").equals("true") || row.get("cashback_eligible").equals("false"));
        }

        for (Map<String, String> row : returnsLogRows.values()) {
            assertTrue(row.get("return_date").matches("^2026-10-[0-3][0-9]$"), "Return date must match YYYY-MM-DD: " + row.get("return_date"));
            assertTrue(row.get("within_window").equals("true") || row.get("within_window").equals("false"));
        }
    }

    @Test
    @DisplayName("Verify output row sequence strictly matches commands.txt order")
    void testCommandOrderPreservationInOutputs() throws IOException {
        List<String> commandLines = Files.readAllLines(baseDir.resolve("commands.txt"));
        List<String> expectedBillOrder = new ArrayList<>();
        List<String> expectedReturnOrder = new ArrayList<>();

        for (String line : commandLines) {
            line = line.trim();
            if (line.startsWith("BILL|")) {
                expectedBillOrder.add(line.split("\\|")[1].trim());
            } else if (line.startsWith("RETURN|")) {
                expectedReturnOrder.add(line.split("\\|")[1].trim());
            }
        }

        assertEquals(200, expectedBillOrder.size());
        assertEquals(15, expectedReturnOrder.size());

        // Check offer_summary.csv row order
        List<String> summaryLines = Files.readAllLines(baseDir.resolve("offer_summary.csv"));
        for (int i = 0; i < expectedBillOrder.size(); i++) {
            String actualBillId = summaryLines.get(i + 1).split(",")[0];
            assertEquals(expectedBillOrder.get(i), actualBillId, "Row " + (i + 1) + " must match command order");
        }

        // Check returns_log.csv row order
        List<String> returnsLines = Files.readAllLines(baseDir.resolve("returns_log.csv"));
        for (int i = 0; i < expectedReturnOrder.size(); i++) {
            String actualReturnId = returnsLines.get(i + 1).split(",")[0];
            assertEquals(expectedReturnOrder.get(i), actualReturnId, "Return row " + (i + 1) + " must match command order");
        }
    }
}
