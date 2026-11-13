package com.retail.engine;

import com.retail.engine.exception.ValidationException;
import com.retail.engine.model.CardIssuer;
import com.retail.engine.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ECE-035 Card Eligibility & Partner Specification Tests")
public class CardEligibilityTest {

    private CardRepository cardRepository;

    @BeforeEach
    void setUp() {
        cardRepository = new CardRepository();
        Path cardsFile = Paths.get("cards.csv").toAbsolutePath().normalize();
        if (!Files.exists(cardsFile)) {
            cardsFile = Paths.get("data", "cards.csv").toAbsolutePath().normalize();
        }
        cardRepository.load(cardsFile);
    }

    @Test
    @DisplayName("BR-01: Exactly 6 card issuers registered in official partner dataset")
    void testOfficialCardIssuersCount() {
        assertEquals(6, cardRepository.count(), "Exactly 6 card issuers must be loaded from cards.csv");
    }

    @Test
    @DisplayName("BR-01, BR-02, BR-11: HDFC card offers both EMI and Cashback")
    void testHdfcEligibility() {
        Optional<CardIssuer> hdfc = cardRepository.findByCode("HDFC");
        assertTrue(hdfc.isPresent(), "HDFC card must exist");
        assertTrue(hdfc.get().isEmiEligible(), "HDFC must be EMI eligible");
        assertTrue(hdfc.get().isCashbackEligible(), "HDFC must be Cashback eligible");
    }

    @Test
    @DisplayName("BR-01, BR-02, BR-11: ICICI card offers both EMI and Cashback")
    void testIciciEligibility() {
        Optional<CardIssuer> icici = cardRepository.findByCode("ICICI");
        assertTrue(icici.isPresent(), "ICICI card must exist");
        assertTrue(icici.get().isEmiEligible(), "ICICI must be EMI eligible");
        assertTrue(icici.get().isCashbackEligible(), "ICICI must be Cashback eligible");
    }

    @Test
    @DisplayName("BR-01, BR-02, BR-11: KOTAK card offers both EMI and Cashback")
    void testKotakEligibility() {
        Optional<CardIssuer> kotak = cardRepository.findByCode("KOTAK");
        assertTrue(kotak.isPresent(), "KOTAK card must exist");
        assertTrue(kotak.get().isEmiEligible(), "KOTAK must be EMI eligible");
        assertTrue(kotak.get().isCashbackEligible(), "KOTAK must be Cashback eligible");
    }

    @Test
    @DisplayName("BR-01, BR-02, BR-11: SBI card is EMI-eligible but NOT cashback-eligible")
    void testSbiEligibility() {
        Optional<CardIssuer> sbi = cardRepository.findByCode("SBI");
        assertTrue(sbi.isPresent(), "SBI card must exist");
        assertTrue(sbi.get().isEmiEligible(), "SBI must be EMI eligible");
        assertFalse(sbi.get().isCashbackEligible(), "SBI must NOT be Cashback eligible");
    }

    @Test
    @DisplayName("BR-01, BR-02, BR-11 & TRAP-06: AXIS card is Cashback-eligible but NOT EMI-eligible")
    void testAxisEligibility() {
        Optional<CardIssuer> axis = cardRepository.findByCode("AXIS");
        assertTrue(axis.isPresent(), "AXIS card must exist");
        assertFalse(axis.get().isEmiEligible(), "AXIS must NOT be EMI eligible");
        assertTrue(axis.get().isCashbackEligible(), "AXIS must be Cashback eligible");
    }

    @Test
    @DisplayName("BR-01, BR-02, BR-11 & TRAP-04: AMEX card is non-partner (neither EMI nor Cashback)")
    void testAmexEligibility() {
        Optional<CardIssuer> amex = cardRepository.findByCode("AMEX");
        assertTrue(amex.isPresent(), "AMEX card must exist");
        assertFalse(amex.get().isEmiEligible(), "AMEX must NOT be EMI eligible");
        assertFalse(amex.get().isCashbackEligible(), "AMEX must NOT be Cashback eligible");
    }

    @Test
    @DisplayName("Unknown or unregistered card issuer returns empty and denies offers")
    void testUnknownCardIssuer() {
        Optional<CardIssuer> unknown = cardRepository.findByCode("DISCOVER");
        assertFalse(unknown.isPresent(), "DISCOVER is not a registered partner card");

        Optional<CardIssuer> nullCard = cardRepository.findByCode(null);
        assertFalse(nullCard.isPresent(), "Null card issuer code must return empty Optional");
    }

    @Test
    @DisplayName("Card repository validates header and throws ValidationException on invalid columns")
    void testMalformedCardFileHeader(@TempDir Path tempDir) throws IOException {
        Path badHeaderCards = tempDir.resolve("bad_cards_header.csv");
        String content = "card_issuer,emi_eligible,cashback_eligible\n" +
                "CITI,true,false\n";
        Files.writeString(badHeaderCards, content);

        CardRepository customRepo = new CardRepository();
        ValidationException ex = assertThrows(ValidationException.class, () -> customRepo.load(badHeaderCards));
        assertTrue(ex.getMessage().contains("Header Error"));
    }

    @Test
    @DisplayName("Card repository validates boolean values and throws ValidationException for non-boolean strings")
    void testInvalidBooleanCardFile(@TempDir Path tempDir) throws IOException {
        Path badBoolCards = tempDir.resolve("bad_cards_bool.csv");
        String content = "issuer_code,issuer_name,emi_eligible,cashback_eligible\n" +
                "CITI,Citibank,yes,no\n";
        Files.writeString(badBoolCards, content);

        CardRepository customRepo = new CardRepository();
        ValidationException ex = assertThrows(ValidationException.class, () -> customRepo.load(badBoolCards));
        assertTrue(ex.getMessage().contains("Invalid boolean value 'yes'"));
    }
}
