package com.retail.engine;

import com.retail.engine.command.Command;
import com.retail.engine.command.CommandProcessor;
import com.retail.engine.command.CommandType;
import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.exception.ConfigurationException;
import com.retail.engine.exception.EngineException;
import com.retail.engine.exception.ValidationException;
import com.retail.engine.model.Bill;
import com.retail.engine.model.BillItem;
import com.retail.engine.model.CardIssuer;
import com.retail.engine.model.ProductCategory;
import com.retail.engine.repository.BillRepository;
import com.retail.engine.repository.CardRepository;
import com.retail.engine.repository.ReturnRepository;
import com.retail.engine.validation.DataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Input and Command Processing Layer Tests")
public class InputProcessingTest {

    private Path baseDir;

    @BeforeEach
    void setUp() {
        baseDir = Paths.get(".").toAbsolutePath().normalize();
    }

    private Path resolve(String filename) {
        Path p = baseDir.resolve(filename);
        if (Files.exists(p)) return p;
        return baseDir.resolve("data").resolve(filename);
    }

    @Test
    @DisplayName("Load official dataset completely: 6 cards, 10 config params, 200 bills, 237 items, 15 returns, 218 commands")
    void testOfficialDataPackLoading() {
        // 1. Config Loader
        OfferConfiguration config = ConfigLoader.load(resolve("offer_config.csv"));
        assertNotNull(config);
        assertEquals(new BigDecimal("5000.00"), config.getEmiMinBill());
        assertEquals(new BigDecimal("15"), config.getEmiInterestPaPct());
        assertEquals(new BigDecimal("199.00"), config.getProcessingFee());
        assertEquals(new BigDecimal("25000.00"), config.getProcessingFeeWaivedFrom());
        assertEquals(new BigDecimal("10"), config.getCashbackPct());
        assertEquals(new BigDecimal("1500.00"), config.getCashbackCapPerCardCustomerMonth());
        assertEquals(new BigDecimal("2000.00"), config.getBundleTvSoundbarOff());
        assertEquals(new BigDecimal("50"), config.getBundlePhoneCasePctOffCase());
        assertEquals(7, config.getReturnWindowDays());
        assertTrue(config.isTenurePermissible(3));
        assertTrue(config.isTenurePermissible(6));
        assertTrue(config.isTenurePermissible(9));
        assertFalse(config.isTenurePermissible(12));

        // 2. Card Repository
        CardRepository cardRepo = new CardRepository();
        cardRepo.load(resolve("cards.csv"));
        assertEquals(6, cardRepo.count());
        assertTrue(cardRepo.findByCode("HDFC").isPresent());
        assertTrue(cardRepo.findByCode("HDFC").get().isEmiEligible());
        assertTrue(cardRepo.findByCode("HDFC").get().isCashbackEligible());
        assertTrue(cardRepo.findByCode("AXIS").get().isCashbackEligible());
        assertFalse(cardRepo.findByCode("AXIS").get().isEmiEligible());
        assertFalse(cardRepo.findByCode("AMEX").get().isEmiEligible());
        assertFalse(cardRepo.findByCode("AMEX").get().isCashbackEligible());

        // 3. Bill Repository
        BillRepository billRepo = new BillRepository();
        billRepo.loadBills(resolve("bills.csv"));
        assertEquals(200, billRepo.billCount());

        billRepo.loadBillItems(resolve("bill_items.csv"));
        assertEquals(237, billRepo.itemCount());

        // 4. Return Repository
        ReturnRepository returnRepo = new ReturnRepository();
        returnRepo.load(resolve("returns.csv"));
        assertEquals(15, returnRepo.count());

        // Referential validation
        assertDoesNotThrow(() -> returnRepo.validateReferentialIntegrity(billRepo));

        // 5. Commands Parsing
        List<Command> commands = CommandProcessor.parseCommands(resolve("commands.txt"));
        assertEquals(218, commands.size());
        assertEquals(CommandType.LOAD_CONFIG, commands.get(0).type());
        assertEquals(CommandType.LOAD_CARDS, commands.get(1).type());
        assertEquals(CommandType.PRINT_CASHBACK_REPORT, commands.get(commands.size() - 1).type());
    }

    @Test
    @DisplayName("Missing file throws ConfigurationException with path details")
    void testMissingFileHandling() {
        Path missing = baseDir.resolve("non_existent_config.csv");
        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> ConfigLoader.load(missing));
        assertTrue(ex.getMessage().contains("File does not exist"));

        CardRepository cardRepo = new CardRepository();
        assertThrows(ConfigurationException.class, () -> cardRepo.load(missing));
    }

    @Test
    @DisplayName("Malformed CSV header throws ValidationException identifying error")
    void testMalformedCsvHeader(@TempDir Path tempDir) throws IOException {
        Path badHeaderCsv = tempDir.resolve("bad_cards.csv");
        Files.writeString(badHeaderCsv, "wrong_col,issuer_name,emi_eligible,cashback_eligible\nHDFC,HDFC Bank,true,true\n");

        CardRepository cardRepo = new CardRepository();
        ValidationException ex = assertThrows(ValidationException.class, () -> cardRepo.load(badHeaderCsv));
        assertTrue(ex.getMessage().contains("Header Error"));
    }

    @Test
    @DisplayName("Row with invalid column count throws ValidationException with exact line number")
    void testInvalidColumnCount(@TempDir Path tempDir) throws IOException {
        Path badRowCsv = tempDir.resolve("bad_cards.csv");
        Files.writeString(badRowCsv, "issuer_code,issuer_name,emi_eligible,cashback_eligible\nHDFC,HDFC Bank,true\n"); // Missing 1 column on line 2

        CardRepository cardRepo = new CardRepository();
        ValidationException ex = assertThrows(ValidationException.class, () -> cardRepo.load(badRowCsv));
        assertTrue(ex.getMessage().contains("Line 2"));
        assertTrue(ex.getMessage().contains("Column count mismatch"));
    }

    @Test
    @DisplayName("Duplicate identifiers throw ValidationException citing duplicate and original line numbers")
    void testDuplicateIdentifiers(@TempDir Path tempDir) throws IOException {
        Path dupCards = tempDir.resolve("dup_cards.csv");
        Files.writeString(dupCards, "issuer_code,issuer_name,emi_eligible,cashback_eligible\n" +
                "HDFC,HDFC Bank,true,true\n" +
                "HDFC,HDFC Duplicate,false,false\n");

        CardRepository cardRepo = new CardRepository();
        ValidationException ex = assertThrows(ValidationException.class, () -> cardRepo.load(dupCards));
        assertTrue(ex.getMessage().contains("Duplicate card issuer_code 'HDFC'"));
        assertTrue(ex.getMessage().contains("Line 3"));
        assertTrue(ex.getMessage().contains("line 2"));
    }

    @Test
    @DisplayName("Data type errors throw ValidationException with field diagnostics")
    void testInvalidDataTypesAndDomains(@TempDir Path tempDir) throws IOException {
        // Test invalid boolean
        Path badBool = tempDir.resolve("bad_bool.csv");
        Files.writeString(badBool, "issuer_code,issuer_name,emi_eligible,cashback_eligible\nHDFC,HDFC Bank,maybe,true\n");
        CardRepository cardRepo = new CardRepository();
        ValidationException exBool = assertThrows(ValidationException.class, () -> cardRepo.load(badBool));
        assertTrue(exBool.getMessage().contains("Invalid boolean value 'maybe'"));

        // Test malformed bill_id
        assertThrows(ValidationException.class, () -> DataValidator.validateBillId("test.csv", 10, "B-01"));

        // Test non-positive price
        assertThrows(ValidationException.class, () -> DataValidator.validateAndParsePrice("test.csv", 5, "-10.00"));
        assertThrows(ValidationException.class, () -> DataValidator.validateAndParsePrice("test.csv", 6, "0.00"));

        // Test invalid category
        assertThrows(ValidationException.class, () -> DataValidator.validateAndParseCategory("test.csv", 7, "INVALID_CAT"));
    }

    @Test
    @DisplayName("Blank tenure in bills.csv is parsed as null without error")
    void testBlankTenureHandling() {
        BillRepository billRepo = new BillRepository();
        billRepo.loadBills(resolve("bills.csv"));

        Bill bill0001 = billRepo.findBillById("BILL-0001").orElseThrow();
        assertFalse(bill0001.hasTenureRequested());
        assertTrue(bill0001.getTenureMonths().isEmpty());

        Bill bill0005 = billRepo.findBillById("BILL-0005").orElseThrow();
        assertTrue(bill0005.hasTenureRequested());
        assertEquals(9, bill0005.getTenureMonths().get());
    }

    @Test
    @DisplayName("Planted tenure 12 in BILL-0096 is parsed as integer 12 for downstream trap handling")
    void testPlantedTenure12() {
        BillRepository billRepo = new BillRepository();
        billRepo.loadBills(resolve("bills.csv"));

        Bill bill0096 = billRepo.findBillById("BILL-0096").orElseThrow();
        assertTrue(bill0096.hasTenureRequested());
        assertEquals(12, bill0096.getTenureMonths().get());
    }

    @Test
    @DisplayName("Referential integrity failure: orphaned line item throws ValidationException")
    void testReferentialIntegrityOrphanedItem(@TempDir Path tempDir) throws IOException {
        Path billsCsv = tempDir.resolve("bills.csv");
        Files.writeString(billsCsv, "bill_id,customer_id,bill_date,card_issuer,tenure_months\n" +
                "BILL-0001,CUST-001,2026-10-01,HDFC,3\n");

        Path itemsCsv = tempDir.resolve("bill_items.csv");
        Files.writeString(itemsCsv, "bill_id,line_no,category,item_name,price\n" +
                "BILL-9999,1,TV,Smart TV,20000.00\n"); // Non-existent bill

        BillRepository repo = new BillRepository();
        repo.loadBills(billsCsv);
        ValidationException ex = assertThrows(ValidationException.class, () -> repo.loadBillItems(itemsCsv));
        assertTrue(ex.getMessage().contains("Referential integrity error"));
        assertTrue(ex.getMessage().contains("BILL-9999"));
    }

    @Test
    @DisplayName("Command stream preserves strict line order and ignores comments")
    void testCommandOrderPreservation() {
        List<Command> commands = CommandProcessor.parseCommands(resolve("commands.txt"));
        assertEquals(218, commands.size());

        // Verify sequence
        assertEquals(CommandType.LOAD_CONFIG, commands.get(0).type());
        assertEquals("offer_config.csv", commands.get(0).getArgument(0));

        assertEquals(CommandType.LOAD_CARDS, commands.get(1).type());
        assertEquals("cards.csv", commands.get(1).getArgument(0));

        assertEquals(CommandType.BILL, commands.get(2).type());
        assertEquals("BILL-0001", commands.get(2).getArgument(0));

        // Line numbers must be monotonically strictly increasing
        for (int i = 1; i < commands.size(); i++) {
            assertTrue(commands.get(i).lineNo() > commands.get(i - 1).lineNo());
        }
    }
}

