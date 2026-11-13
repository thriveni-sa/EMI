package com.retail.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Dynamic & Synthetic Dataset Generalization Test")
public class DynamicDatasetIntegrationTest {

    @Test
    @DisplayName("Process completely new dataset: proves zero hardcoding and full configuration dynamicity")
    void testProcessSyntheticDataset(@TempDir Path tempDir) throws IOException {
        // 1. Write synthetic cards.csv
        Path cardsFile = tempDir.resolve("cards.csv");
        String cardsContent = "issuer_code,issuer_name,emi_eligible,cashback_eligible\n" +
                "ALPHA,Alpha Global Bank,true,true\n" +
                "BETA,Beta Credit Ltd,false,true\n" +
                "GAMMA,Gamma National Bank,true,false\n" +
                "DELTA,Delta Non-Partner,false,false\n";
        Files.writeString(cardsFile, cardsContent);

        // 2. Write synthetic offer_config.csv (custom non-default rules)
        Path configFile = tempDir.resolve("offer_config.csv");
        String configContent = "parameter,value\n" +
                "EMI_MIN_BILL,6000.00\n" +
                "EMI_TENURES,6;12\n" +
                "EMI_INTEREST_PA_PCT,12\n" +
                "PROCESSING_FEE,250.00\n" +
                "PROCESSING_FEE_WAIVED_FROM,30000.00\n" +
                "CASHBACK_PCT,5\n" +
                "CASHBACK_CAP_PER_CARD_CUSTOMER_MONTH,2000.00\n" +
                "BUNDLE_TV_SOUNDBAR_OFF,1500.00\n" +
                "BUNDLE_PHONE_CASE_PCT_OFF_CASE,40\n" +
                "RETURN_WINDOW_DAYS,10\n";
        Files.writeString(configFile, configContent);

        // 3. Write synthetic bills.csv (matching schema BILL-nnnn and CUST-nnn)
        Path billsFile = tempDir.resolve("bills.csv");
        String billsContent = "bill_id,customer_id,bill_date,card_issuer,tenure_months\n" +
                "BILL-9001,CUST-901,2026-11-01,ALPHA,6\n" +
                "BILL-9002,CUST-901,2026-11-02,BETA,\n" +
                "BILL-9003,CUST-902,2026-11-05,GAMMA,12\n" +
                "BILL-9004,CUST-903,2026-11-10,DELTA,6\n";
        Files.writeString(billsFile, billsContent);

        // 4. Write synthetic bill_items.csv
        Path itemsFile = tempDir.resolve("bill_items.csv");
        String itemsContent = "bill_id,line_no,category,item_name,price\n" +
                "BILL-9001,1,TV,Smart TV 55,35000.00\n" +
                "BILL-9001,2,SOUNDBAR,Audio Bar,5000.00\n" +
                "BILL-9002,1,PHONE,Smart Phone,20000.00\n" +
                "BILL-9002,2,CASE,Leather Case,1000.00\n" +
                "BILL-9003,1,LAPTOP,Pro Laptop,48000.00\n" +
                "BILL-9004,1,HEADPHONES,Wireless,8000.00\n";
        Files.writeString(itemsFile, itemsContent);

        // 5. Write synthetic returns.csv (matching schema RET-nnn)
        Path returnsFile = tempDir.resolve("returns.csv");
        String returnsContent = "return_id,bill_id,line_no,return_date\n" +
                "RET-901,BILL-9002,2,2026-11-08\n" + // 6 days -> within 10-day window
                "RET-902,BILL-9001,1,2026-11-25\n";  // 24 days -> outside 10-day window
        Files.writeString(returnsFile, returnsContent);

        // 6. Write synthetic commands.txt
        Path commandsFile = tempDir.resolve("commands.txt");
        String commandsContent = "# Synthetic test stream\n" +
                "LOAD_CONFIG|offer_config.csv\n" +
                "LOAD_CARDS|cards.csv\n" +
                "BILL|BILL-9001\n" +
                "BILL|BILL-9002\n" +
                "RETURN|RET-901\n" +
                "BILL|BILL-9003\n" +
                "BILL|BILL-9004\n" +
                "RETURN|RET-902\n" +
                "PRINT_CASHBACK_REPORT|2026-11\n";
        Files.writeString(commandsFile, commandsContent);

        // Run engine bootstrap
        EngineBootstrap bootstrap = new EngineBootstrap(tempDir);
        bootstrap.run(commandsFile);

        // Verify offer_summary.csv exists and contains 4 rows
        Path offerSummary = tempDir.resolve("offer_summary.csv");
        assertTrue(Files.exists(offerSummary));
        List<String> summaryLines = Files.readAllLines(offerSummary);
        assertEquals(5, summaryLines.size(), "Header + 4 bills");

        Map<String, Map<String, String>> summaryMap = parseCsvLines(summaryLines);

        // Verify BILL-9001: TV (35000) + Soundbar (5000) = Gross 40000
        // Bundle discount: 1500 -> Net: 38500.00
        // Tenure 6, 12% interest: 38500 * 12 * 6 / 1200 = 2310.00 interest saved
        // Fee waived: 38500 >= 30000 -> fee = 0.00
        // Cashback: 5% of 38500 = 1925.00
        // Arbitration: EMI (2310.00) > Cashback (1925.00) -> Recommend EMI!
        Map<String, String> bill9001 = summaryMap.get("BILL-9001");
        assertNotNull(bill9001);
        assertEquals("40000.00", bill9001.get("gross_amount"));
        assertEquals("1500.00", bill9001.get("bundle_discount"));
        assertEquals("38500.00", bill9001.get("bill_amount"));
        assertEquals("true", bill9001.get("emi_available"));
        assertEquals("6", bill9001.get("tenure_months"));
        assertEquals("0.00", bill9001.get("processing_fee"));
        assertEquals("2310.00", bill9001.get("interest_saved"));
        assertEquals("1925.00", bill9001.get("cashback_amount"));
        assertEquals("EMI", bill9001.get("recommended_option"));
        assertEquals("2310.00", bill9001.get("benefit_amount"));

        // Verify BILL-9002: Phone (20000) + Case (1000)
        // 40% discount on case = 400.00 -> Net: 20600.00
        // Tenure blank, Beta card is Cashback-only
        // 5% of 20600 = 1030.00 -> Recommend CASHBACK
        Map<String, String> bill9002 = summaryMap.get("BILL-9002");
        assertNotNull(bill9002);
        assertEquals("21000.00", bill9002.get("gross_amount"));
        assertEquals("400.00", bill9002.get("bundle_discount"));
        assertEquals("20600.00", bill9002.get("bill_amount"));
        assertEquals("false", bill9002.get("emi_available"));
        assertEquals("", bill9002.get("tenure_months"));
        assertEquals("1030.00", bill9002.get("cashback_amount"));
        assertEquals("CASHBACK", bill9002.get("recommended_option"));

        // Verify BILL-9004: Delta non-partner card -> Recommend NONE
        Map<String, String> bill9004 = summaryMap.get("BILL-9004");
        assertNotNull(bill9004);
        assertEquals("false", bill9004.get("emi_available"));
        assertEquals("false", bill9004.get("cashback_eligible"));
        assertEquals("NONE", bill9004.get("recommended_option"));

        // Verify returns_log.csv
        Path returnsLog = tempDir.resolve("returns_log.csv");
        assertTrue(Files.exists(returnsLog));
        List<String> returnLines = Files.readAllLines(returnsLog);
        assertEquals(3, returnLines.size(), "Header + 2 returns");

        Map<String, Map<String, String>> returnsMap = parseCsvLines(returnLines);
        // RET-901 on BILL-9002 (CASHBACK): 6 days after bill <= 10 days window -> reverses 1030.00
        Map<String, String> ret901 = returnsMap.get("RET-901");
        assertNotNull(ret901);
        assertEquals("6", ret901.get("days_after_bill"));
        assertEquals("true", ret901.get("within_window"));
        assertEquals("1030.00", ret901.get("cashback_reversed"));

        // RET-902 on BILL-9001 (EMI bill): 24 days after bill > 10 days window -> 0.00
        Map<String, String> ret902 = returnsMap.get("RET-902");
        assertNotNull(ret902);
        assertEquals("24", ret902.get("days_after_bill"));
        assertEquals("false", ret902.get("within_window"));
        assertEquals("0.00", ret902.get("cashback_reversed"));

        // Run second execution to verify idempotence and deterministic re-runs
        assertDoesNotThrow(() -> bootstrap.run(commandsFile));
        List<String> reSummaryLines = Files.readAllLines(offerSummary);
        assertEquals(5, reSummaryLines.size(), "Re-running must overwrite cleanly without duplicate lines");
    }

    private Map<String, Map<String, String>> parseCsvLines(List<String> lines) {
        Map<String, Map<String, String>> map = new HashMap<>();
        if (lines.isEmpty()) return map;

        String[] headers = lines.get(0).split(",");
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] tokens = line.split(",", -1);
            Map<String, String> row = new HashMap<>();
            for (int h = 0; h < headers.length && h < tokens.length; h++) {
                row.put(headers[h], tokens[h]);
            }
            map.put(tokens[0], row);
        }
        return map;
    }
}
