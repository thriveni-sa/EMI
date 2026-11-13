package com.retail.engine;

import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.exception.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Configuration Management Tests")
public class ConfigurationTest {

    @Test
    @DisplayName("Load official offer_config.csv: verify all 10 parameters")
    void testOfficialOfferConfigParameters() {
        Path configPath = Paths.get("offer_config.csv").toAbsolutePath().normalize();
        if (!Files.exists(configPath)) {
            configPath = Paths.get("data", "offer_config.csv").toAbsolutePath().normalize();
        }
        assertTrue(Files.exists(configPath), "offer_config.csv must exist");

        OfferConfiguration config = ConfigLoader.load(configPath);
        assertNotNull(config, "Config should load successfully");

        // Verify exact values matching teacher dataset
        assertEquals(new BigDecimal("15"), config.getEmiInterestPaPct(), "Annual interest rate should be 15%");
        assertEquals(new BigDecimal("199.00"), config.getProcessingFee(), "Processing fee should be Rs 199.00");
        assertEquals(new BigDecimal("25000.00"), config.getProcessingFeeWaivedFrom(), "Fee waiver threshold should be Rs 25,000.00");
        assertEquals(new BigDecimal("5000.00"), config.getEmiMinBill(), "EMI minimum bill should be Rs 5,000.00");
        assertEquals(new BigDecimal("10"), config.getCashbackPct(), "Cashback rate should be 10%");
        assertEquals(new BigDecimal("1500.00"), config.getCashbackCapPerCardCustomerMonth(), "Monthly cashback cap should be Rs 1,500.00");
        assertEquals(7, config.getReturnWindowDays(), "Return window should be 7 days");
        assertEquals(new BigDecimal("2000.00"), config.getBundleTvSoundbarOff(), "TV + Soundbar bundle discount should be Rs 2,000.00");
        assertEquals(new BigDecimal("50"), config.getBundlePhoneCasePctOffCase(), "Phone + Case bundle discount should be 50%");

        // Permissible tenures: 3, 6, 9
        Set<Integer> tenures = config.getEmiTenures();
        assertEquals(3, tenures.size());
        assertTrue(config.isTenurePermissible(3));
        assertTrue(config.isTenurePermissible(6));
        assertTrue(config.isTenurePermissible(9));
        assertFalse(config.isTenurePermissible(12), "Tenure 12 must not be permissible");
        assertFalse(config.isTenurePermissible(0), "Tenure 0 must not be permissible");
    }

    @Test
    @DisplayName("Missing config file throws ConfigurationException")
    void testMissingConfigFile() {
        Path nonExistent = Paths.get("non_existent_config_file_xyz.csv");
        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> ConfigLoader.load(nonExistent));
        assertTrue(ex.getMessage().contains("File does not exist"));
    }

    @Test
    @DisplayName("Empty config file throws ConfigurationException")
    void testEmptyConfigFile(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("empty_config.csv");
        Files.createFile(emptyFile);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> ConfigLoader.load(emptyFile));
        assertTrue(ex.getMessage().contains("File is empty"));
    }

    @Test
    @DisplayName("Custom valid configuration parameters are loaded accurately")
    void testCustomConfigParameters(@TempDir Path tempDir) throws IOException {
        Path customConfig = tempDir.resolve("custom_config.csv");
        String content = "parameter,value\n" +
                "EMI_MIN_BILL,10000.00\n" +
                "EMI_TENURES,6;12\n" +
                "EMI_INTEREST_PA_PCT,12\n" +
                "PROCESSING_FEE,250.00\n" +
                "PROCESSING_FEE_WAIVED_FROM,30000.00\n" +
                "CASHBACK_PCT,5\n" +
                "CASHBACK_CAP_PER_CARD_CUSTOMER_MONTH,2000.00\n" +
                "BUNDLE_TV_SOUNDBAR_OFF,1500.00\n" +
                "BUNDLE_PHONE_CASE_PCT_OFF_CASE,40\n" +
                "RETURN_WINDOW_DAYS,14\n";
        Files.writeString(customConfig, content);

        OfferConfiguration config = ConfigLoader.load(customConfig);
        assertEquals(new BigDecimal("12"), config.getEmiInterestPaPct());
        assertEquals(new BigDecimal("250.00"), config.getProcessingFee());
        assertEquals(new BigDecimal("30000.00"), config.getProcessingFeeWaivedFrom());
        assertEquals(new BigDecimal("10000.00"), config.getEmiMinBill());
        assertEquals(new BigDecimal("5"), config.getCashbackPct());
        assertEquals(new BigDecimal("2000.00"), config.getCashbackCapPerCardCustomerMonth());
        assertEquals(14, config.getReturnWindowDays());
        assertTrue(config.isTenurePermissible(6));
        assertTrue(config.isTenurePermissible(12));
        assertFalse(config.isTenurePermissible(3));
    }

    @Test
    @DisplayName("Missing mandatory key throws ConfigurationException identifying missing key")
    void testMissingMandatoryKey(@TempDir Path tempDir) throws IOException {
        Path incomplete = tempDir.resolve("incomplete_config.csv");
        String content = "parameter,value\n" +
                "EMI_MIN_BILL,5000.00\n" +
                "EMI_TENURES,3;6;9\n";
        Files.writeString(incomplete, content);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> ConfigLoader.load(incomplete));
        assertTrue(ex.getMessage().contains("Missing mandatory configuration parameters"));
    }

    @Test
    @DisplayName("Duplicate configuration parameter throws ConfigurationException with line diagnostics")
    void testDuplicateParameter(@TempDir Path tempDir) throws IOException {
        Path dup = tempDir.resolve("dup_config.csv");
        String content = "parameter,value\n" +
                "EMI_MIN_BILL,5000.00\n" +
                "EMI_MIN_BILL,6000.00\n";
        Files.writeString(dup, content);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> ConfigLoader.load(dup));
        assertTrue(ex.getMessage().contains("Duplicate parameter key: 'EMI_MIN_BILL'"));
    }

    @Test
    @DisplayName("Non-numeric parameter value throws ConfigurationException with line diagnostics")
    void testNonNumericParameterValue(@TempDir Path tempDir) throws IOException {
        Path badValue = tempDir.resolve("bad_value.csv");
        String content = "parameter,value\n" +
                "EMI_MIN_BILL,5000.00\n" +
                "EMI_TENURES,3;6;9\n" +
                "EMI_INTEREST_PA_PCT,not_a_number\n" +
                "PROCESSING_FEE,199.00\n" +
                "PROCESSING_FEE_WAIVED_FROM,25000.00\n" +
                "CASHBACK_PCT,10\n" +
                "CASHBACK_CAP_PER_CARD_CUSTOMER_MONTH,1500.00\n" +
                "BUNDLE_TV_SOUNDBAR_OFF,2000.00\n" +
                "BUNDLE_PHONE_CASE_PCT_OFF_CASE,50\n" +
                "RETURN_WINDOW_DAYS,7\n";
        Files.writeString(badValue, content);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> ConfigLoader.load(badValue));
        assertTrue(ex.getMessage().contains("Non-numeric value for EMI_INTEREST_PA_PCT"));
    }

    @Test
    @DisplayName("Negative return window throws ConfigurationException")
    void testNegativeReturnWindow(@TempDir Path tempDir) throws IOException {
        Path negativeWindow = tempDir.resolve("neg_window.csv");
        String content = "parameter,value\n" +
                "EMI_MIN_BILL,5000.00\n" +
                "EMI_TENURES,3;6;9\n" +
                "EMI_INTEREST_PA_PCT,15\n" +
                "PROCESSING_FEE,199.00\n" +
                "PROCESSING_FEE_WAIVED_FROM,25000.00\n" +
                "CASHBACK_PCT,10\n" +
                "CASHBACK_CAP_PER_CARD_CUSTOMER_MONTH,1500.00\n" +
                "BUNDLE_TV_SOUNDBAR_OFF,2000.00\n" +
                "BUNDLE_PHONE_CASE_PCT_OFF_CASE,50\n" +
                "RETURN_WINDOW_DAYS,-5\n";
        Files.writeString(negativeWindow, content);

        ConfigurationException ex = assertThrows(ConfigurationException.class, () -> ConfigLoader.load(negativeWindow));
        assertTrue(ex.getMessage().contains("RETURN_WINDOW_DAYS cannot be negative"));
    }
}
