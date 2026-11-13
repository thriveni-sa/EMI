# ECE-035: Electronics Retail No-Cost EMI and Cashback Offer Engine
## Requirement Traceability Matrix (RTM) & Architectural Mapping

**Document Version:** 1.0.0  
**Project Identifier:** ECE-035 (Team E005)  
**Methodology:** Strict Forward & Backward Traceability (Requirements $\leftrightarrow$ Design $\leftrightarrow$ Code Components $\leftrightarrow$ Test Verification)  
**Target Platform:** Java 17+ SE (LTS), Command-Line Architecture  
**Primary Dataset Authority:** Evaluation Data Pack Prepared by 10 SECONDS for Team E005  

---

## 1. Architectural Component Model

To ensure high cohesion, low coupling, and testability without any external framework dependencies, the planned Java architecture is organized into four core layers:

```
[Command-Line Entry (Main)]
            │
            ▼
┌─────────────────────────┐
│    Command Processor    │ ◄─── (commands.txt)
└───────────┬─────────────┘
            │
   ┌────────┴──────────────────────────┐
   ▼                                   ▼
┌─────────────────────────┐ ┌─────────────────────────┐
│     Domain Services     │ │   Data Repositories     │
│ ─────────────────────── │ │ ─────────────────────── │
│ • BundleDiscountService │ │ • OfferConfigRepo       │
│ • EmiCalculatorService  │ │ • CardRepository        │
│ • CashbackService       │ │ • BillRepository        │
│ • OfferArbitrationEngine│ │ • ReturnRepository      │
│ • ReturnProcessService  │ │ • CustomerLedgerRepo    │
│ • ReportExportService   │ └─────────────────────────┘
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ Output Exporters (CSV)  │ ───► offer_summary.csv, emi_schedule.csv,
└─────────────────────────┘      returns_log.csv, cashback_report.csv
```

---

## 2. Master Requirement Traceability Matrix (RTM)

### 2.1 Business Rules Traceability (BR-01 through BR-21)

| Rule ID | Rule Summary | Functional Req | Future Java Class | Target Method(s) | Unit / E2E Test Case ID | Dataset Verification Reference |
|---|---|---|---|---|---|---|
| **BR-01** | Card Issuer EMI Eligibility | FR-02, FR-06 | `CardRepository`, `EmiCalculatorService` | `CardIssuer.isEmiEligible()`, `EmiCalculatorService.isEligible(Bill, CardIssuer)` | `TC-BR01-CARD-EMI-ELIGIBILITY` | `cards.csv` (HDFC=true, AXIS=false, AMEX=false); `bills.csv` (BILL-0088, BILL-0099) |
| **BR-02** | Card Issuer Cashback Eligibility | FR-02, FR-10 | `CardRepository`, `CashbackService` | `CardIssuer.isCashbackEligible()`, `CashbackService.isEligible(Bill, CardIssuer)` | `TC-BR02-CARD-CASHBACK-ELIGIBILITY` | `cards.csv` (AXIS=true, SBI=false, AMEX=false); `bills.csv` (BILL-0052, BILL-0088) |
| **BR-03** | Bundle Discount: TV + Soundbar | FR-05 | `BundleDiscountService` | `evaluateTvSoundbarBundle(List<BillItem>)` | `TC-BR03-BUNDLE-TV-SOUNDBAR` | `bill_items.csv` (BILL-0022, BILL-0032, BILL-0066) |
| **BR-04** | Bundle Discount: Phone + Case | FR-05 | `BundleDiscountService` | `evaluatePhoneCaseBundle(List<BillItem>)` | `TC-BR04-BUNDLE-PHONE-CASE` | `bill_items.csv` (BILL-0009, BILL-0010, BILL-0119) |
| **BR-05** | Net Bill Amount Derivation | FR-04, FR-05 | `BillProcessingPipeline` | `calculateNetBillAmount(Bill, List<BillItem>)` | `TC-BR05-NET-AMOUNT-CALC` | `bill_items.csv` all bills; verification across `offer_summary.csv:bill_amount` |
| **BR-06** | Customer EMI Intent Requirement | FR-06 | `EmiCalculatorService` | `hasCustomerOptedForEmi(Bill)` | `TC-BR06-EMI-INTENT-CHECK` | `bills.csv` (BILL-0001 blank tenure $\to$ EMI false; BILL-0005 tenure 9 $\to$ EMI true) |
| **BR-07** | Minimum Bill Threshold (Rs 5000) | FR-06 | `EmiCalculatorService` | `meetsMinimumBillThreshold(BigDecimal)` | `TC-BR07-EMI-MIN-THRESHOLD` | `bills.csv` (BILL-0026 Rs 5000.00 $\to$ OK; BILL-0030 Rs 4999.00 $\to$ Fails) |
| **BR-08** | Permissible EMI Tenures (3, 6, 9) | FR-06 | `EmiCalculatorService` | `isPermissibleTenure(int, Set<Integer>)` | `TC-BR08-PERMISSIBLE-TENURES` | `offer_config.csv` (3;6;9); `bills.csv` (BILL-0096 tenure 12 $\to$ Fails) |
| **BR-09** | Processing Fee & Waiver (Rs 25k) | FR-08 | `EmiCalculatorService` | `calculateProcessingFee(BigDecimal)` | `TC-BR09-PROCESSING-FEE-WAIVER` | `bills.csv` (BILL-0026 Rs 5000 $\to$ 199.00; BILL-0039 Rs 25000 $\to$ 0.00; BILL-0053 Rs 24999 $\to$ 199.00; BILL-0066 net 25000 $\to$ 0.00) |
| **BR-10** | Interest Saved Subvention (15% p.a.) | FR-07 | `EmiCalculatorService` | `calculateInterestSaved(BigDecimal, int, BigDecimal)` | `TC-BR10-INTEREST-SAVED-CALC` | `bills.csv` (BILL-0016 Rs 20000 @ 6m $\to$ 1500.00; BILL-0052 Rs 10000 @ 3m $\to$ 375.00) |
| **BR-11** | Instalment & Remainder Adjustment | FR-09 | `EmiCalculatorService` | `calculateInstalments(BigDecimal, int)` | `TC-BR11-INSTALMENT-REMAINDER` | `bills.csv` (BILL-0052: 10000 / 3 $\to$ 3334, 3334, last=3332) |
| **BR-12** | Schedule Processing Fee Integration | FR-09, FR-15 | `EmiCalculatorService`, `ReportExportService` | `generateSchedule(Bill, EmiPlan)` | `TC-BR12-SCHEDULE-FEE-INCLUSION` | `bills.csv` (BILL-0052 schedule row 1 = 3334 + 199 = 3533.00) |
| **BR-13** | Instant Cashback Base (10%) | FR-10 | `CashbackService` | `calculateBaseCashback(BigDecimal, BigDecimal)` | `TC-BR13-CASHBACK-BASE-CALC` | `bills.csv` (BILL-0012: 9000.00 @ 10% $\to$ 900.00) |
| **BR-14** | Monthly Cap Per Customer Per Card | FR-10 | `CashbackService`, `CustomerLedgerRepository` | `calculateEligibleCashback(CustomerKey, BigDecimal)` | `TC-BR14-MONTHLY-CASHBACK-CAP` | `bills.csv` (CUST-003 on AXIS: BILL-0012 900, BILL-0062 500, BILL-0102 only 100 capped) |
| **BR-15** | Mutual Exclusivity of Offers | FR-11 | `OfferArbitrationEngine` | `arbitrateBestOffer(EmiResult, CashbackResult)` | `TC-BR15-MUTUAL-EXCLUSIVITY` | Engine asserts exactly one offer type is awarded per row in `offer_summary.csv` |
| **BR-16** | Value Maximization Recommendation | FR-11 | `OfferArbitrationEngine` | `arbitrateBestOffer(EmiResult, CashbackResult)` | `TC-BR16-RECOMMEND-MAX-BENEFIT` | Verified across all 200 bills in `bills.csv` |
| **BR-17** | Equal-Benefit Tie-Breaker | FR-11 | `OfferArbitrationEngine` | `resolveTie(EmiResult, CashbackResult)` | `TC-BR17-TIE-BREAK-TO-CASHBACK` | `bills.csv` (BILL-0016: Interest Saved 1500.00 == Cashback 1500.00 $\to$ CASHBACK) |
| **BR-18** | Fallback Recommendation (NONE) | FR-11 | `OfferArbitrationEngine` | `createNoneOffer()` | `TC-BR18-RECOMMEND-NONE` | `bills.csv` (BILL-0088 AMEX $\to$ NONE, 0.00; BILL-0030 below min $\to$ NONE, 0.00) |
| **BR-19** | Return Window Enforcement (7 Days) | FR-12 | `ReturnProcessingService` | `isWithinReturnWindow(LocalDate, LocalDate)` | `TC-BR19-RETURN-WINDOW-CHECK` | `returns.csv` (RET-004 Day 7 $\to$ true; RET-005 Day 8 $\to$ false; RET-014 Day 20 $\to$ false) |
| **BR-20** | Conditional Cashback Clawback | FR-13 | `ReturnProcessingService` | `calculateCashbackReversal(ReturnTransaction, Bill)` | `TC-BR20-CASHBACK-CLAWBACK` | `returns.csv` (RET-003 on EMI bill $\to$ 0.00; RET-005 outside window $\to$ 0.00; RET-001 valid $\to$ reverse) |
| **BR-21** | Net Liability & Cap Ledger Tracking | FR-14, FR-15 | `CustomerLedgerRepository`, `ReportExportService` | `updateLedger(LedgerEvent)`, `generateCashbackReport(YearMonth)` | `TC-BR21-LEDGER-RECONCILIATION` | `commands.txt` line 219 (`PRINT_CASHBACK_REPORT\|2026-10`) $\to$ `cashback_report.csv` |

---

### 2.2 Functional Requirements Traceability (FR-01 through FR-15)

| Req ID | Functional Requirement Title | Primary Target Class | Target Method | Verifying Test Suite |
|---|---|---|---|---|
| **FR-01** | Configuration Loading | `com.retail.engine.config.OfferConfigLoader` | `OfferConfig loadConfig(Path path)` | `OfferConfigLoaderTest` |
| **FR-02** | Card Catalog Loading | `com.retail.engine.repository.CardRepository` | `void loadCards(Path path)` | `CardRepositoryTest` |
| **FR-03** | Command Stream Parsing | `com.retail.engine.command.CommandProcessor` | `void processCommands(Path commandFile)` | `CommandProcessorTest` |
| **FR-04** | Line Item Aggregation | `com.retail.engine.repository.BillRepository` | `List<BillItem> getItemsForBill(String billId)` | `BillRepositoryTest` |
| **FR-05** | Bundle Discount Evaluation | `com.retail.engine.service.BundleDiscountService`| `BundleResult evaluateBundles(List<BillItem>)`| `BundleDiscountServiceTest` |
| **FR-06** | No-Cost EMI Evaluation | `com.retail.engine.service.EmiCalculatorService` | `EmiEvaluation evaluate(Bill, BigDecimal, Card)`| `EmiCalculatorServiceTest` |
| **FR-07** | Interest Subvention Calculation | `com.retail.engine.service.EmiCalculatorService` | `BigDecimal computeInterestSubvention(...)` | `EmiCalculationMathTest` |
| **FR-08** | Processing Fee Assessment | `com.retail.engine.service.EmiCalculatorService` | `BigDecimal determineFee(BigDecimal netAmount)` | `ProcessingFeeWaiverTest` |
| **FR-09** | Instalment Schedule Generation | `com.retail.engine.service.EmiCalculatorService` | `List<Instalment> buildSchedule(Bill, EmiPlan)`| `EmiScheduleGeneratorTest` |
| **FR-10** | Instant Cashback Calculation | `com.retail.engine.service.CashbackService` | `CashbackEvaluation evaluate(Bill, BigDecimal)` | `CashbackCappingTest` |
| **FR-11** | Best-Offer Decision Engine | `com.retail.engine.service.OfferArbitrationEngine`| `OfferDecision decide(EmiEval, CashbackEval)` | `OfferArbitrationEngineTest` |
| **FR-12** | Return Window Validation | `com.retail.engine.service.ReturnProcessingService`| `boolean validateReturnWindow(Bill, Return)` | `ReturnWindowValidationTest` |
| **FR-13** | Cashback Reversal Clawback | `com.retail.engine.service.ReturnProcessingService`| `BigDecimal executeClawback(Bill, Return)` | `CashbackClawbackTest` |
| **FR-14** | Monthly Reconciliation Report | `com.retail.engine.service.ReportExportService` | `void generateCashbackReport(YearMonth, Path)` | `CashbackReportGenerationTest` |
| **FR-15** | Evaluation File Writing | `com.retail.engine.service.ReportExportService` | `void exportAllReports(Path outputDirectory)` | `EndToEndReportExportTest` |

---

### 2.3 Planted Edge Cases & Adversarial Traps Traceability

| Code | Nature of Condition | Source Line / Identifier | Verifying Test Case Name | Expected Assertion / Behavior |
|---|---|---|---|---|
| **EC-01A**| Exact Min EMI Threshold (Rs 5000.00) | `bills.csv:27` (`BILL-0026`) | `testEc01_ExactMinimumEmiThreshold()` | `emi_available=true`, `processing_fee=199.00` |
| **EC-01B**| Exact Fee Waiver Threshold (Rs 25000.00)| `bills.csv:40` (`BILL-0039`) | `testEc01_ExactFeeWaiverThreshold()` | `emi_available=true`, `processing_fee=0.00` |
| **EC-02** | Progressive Monthly Cap Saturation | `bills.csv:13,63,103` (`BILL-0012,0062,0102`) | `testEc02_ProgressiveCapExhaustion()` | B1: 900.00, B2: 500.00 (Total 1400.00), B3: 100.00 (clamped at 1500.00) |
| **EC-03** | Indivisible Amount Instalment Remainder | `bills.csv:53` (`BILL-0052`) | `testEc03_InstalmentRemainderAdjustment()`| Instalment 1=3334, 2=3334, 3=3332. Sum == 10000.00 |
| **EC-04** | Strict Financial Benefit Tie | `bills.csv:17` (`BILL-0016`) | `testEc04_EqualBenefitTieBreaksToCashback()`| `interest_saved==1500.00`, `cashback==1500.00` $\implies$ `recommended_option=CASHBACK` |
| **EC-05A**| Boundary Return on Exact 7th Day | `returns.csv:5` (`RET-004` on `BILL-0015`) | `testEc05_ReturnOnDaySevenReversesCashback()`| $\Delta_{\text{days}}=7 \implies$ `within_window=true`, `cashback_reversed=450.00` |
| **EC-05B**| Expired Return on 8th Day | `returns.csv:6` (`RET-005` on `BILL-0019`) | `testEc05_ReturnOnDayEightRetainsCashback()` | $\Delta_{\text{days}}=8 \implies$ `within_window=false`, `cashback_reversed=0.00` |
| **TRAP-01**| One Rupee Below Min EMI (Rs 4999.00) | `bills.csv:31` (`BILL-0030`) | `testTrap01_OneRupeeBelowEmiMinBill()` | `emi_available=false`, `recommended_option=NONE` |
| **TRAP-02**| One Rupee Below Waiver (Rs 24999.00)| `bills.csv:54` (`BILL-0053`) | `testTrap02_OneRupeeBelowWaiverThreshold()`| `processing_fee=199.00` (fee NOT waived) |
| **TRAP-03**| Post-Bundle Waiver Evaluation | `bills.csv:67` (`BILL-0066`) | `testTrap03_BundleDiscountAffectsFeeWaiver()`| Gross=27000, Bundle=2000 $\implies$ Net=25000 $\implies$ `processing_fee=0.00` |
| **TRAP-04**| Non-Partner Ineligible Card (AMEX) | `bills.csv:89` (`BILL-0088`) | `testTrap04_NonPartnerCardProducesNone()` | Card has both flags false $\implies$ `recommended_option=NONE` |
| **TRAP-05**| Unsupported Financing Tenure (12 Months)| `bills.csv:97` (`BILL-0096`) | `testTrap05_UnsupportedTenureRejection()` | Tenure 12 not in (3,6,9) $\implies$ `emi_available=false` |
| **TRAP-06**| Tenure Specified on Cashback-Only Card | `bills.csv:100` (`BILL-0099`) | `testTrap06_TenureOnCashbackCardFallback()` | AXIS `emi_eligible=false` $\implies$ EMI skipped, Cashback evaluated & awarded |
| **TRAP-07**| Multi-Case Phone Companion Bundle | `bills.csv:120` (`BILL-0119`) | `testTrap07_MultiCaseBundleDiscount()` | 50% discount applied to Case 1 (1000 $\to$ 500) AND Case 2 (800 $\to$ 400) |
| **TRAP-08**| Extreme Stale Return (20 Days Elapsed)| `returns.csv:15` (`RET-014` on `BILL-0012`)| `testTrap08_StaleReturnTwentyDays()` | $\Delta_{\text{days}}=20 \implies$ `within_window=false`, `cashback_reversed=0.00` |
| **TRAP-09**| Return on Purchase with No Cashback (EMI)| `returns.csv:4` (`RET-003` on `BILL-0052`)| `testTrap09_ReturnOnEmiBillZeroClawback()` | Original bill had EMI $\implies$ `cashback_reversed=0.00` |

---

## 3. Data Validation & Integrity Traceability (VR-01 through VR-10)

| Validation ID | Invariant Verified | Enforcing Java Component | Target Method | Failure / Exception Strategy |
|---|---|---|---|---|
| **VR-01** | Config Completeness | `OfferConfigLoader` | `validateConfigKeys(Map<String, String>)` | Throws `MissingConfigurationException` |
| **VR-02** | Card Schema & Uniqueness | `CardRepository` | `validateCardRow(String[])` | Rejects row; throws `DuplicateIssuerException` |
| **VR-03** | Bill & Customer ID Format | `BillRepository` | `validateIdentifiers(String billId, String custId)`| Throws `InvalidIdentifierFormatException` |
| **VR-04** | ISO-8601 Date Parsing | `DateUtils` | `LocalDate parseIsoDate(String dateStr)` | Throws `DateTimeParseException` |
| **VR-05** | Card Issuer Foreign Key | `BillRepository` | `verifyIssuerRegistered(String issuerCode)` | Emits warning, treats issuer as unlisted |
| **VR-06** | Positive Monetary Pricing | `BillRepository` | `validateItemPrice(BigDecimal price)` | Throws `IllegalPriceException` if price $\le 0$ |
| **VR-07** | Bill-Item Referential Integrity| `BillRepository` | `linkItemsToBills(Map<String, Bill>)` | Flags orphaned line items |
| **VR-08** | Command Syntax & Tokens | `CommandProcessor` | `parseCommand(String line)` | Skips malformed line, writes descriptive log |
| **VR-09** | Return-Bill Referential Integrity| `ReturnRepository` | `validateReturnReference(String billId)` | Throws `OrphanedReturnException` |
| **VR-10** | Chronological Sanity ($\text{Ret} \ge \text{Bill}$)| `ReturnProcessingService`| `validateChronology(LocalDate bill, LocalDate ret)`| Rejects return if $\text{return\_date} < \text{bill\_date}$ |

---

## 4. Reporting Artifacts Traceability (RR-01 through RR-04)

| Report ID | Output Filename | Generating Service | Formatting & Writing Method | Mandatory Columns Validated |
|---|---|---|---|---|
| **RR-01** | `offer_summary.csv` | `ReportExportService` | `writeOfferSummary(List<OfferSummaryRecord>, Writer)` | 17 columns: `bill_id`, `customer_id`, `bill_date`, `card_issuer`, `gross_amount`, `bundle_discount`, `bill_amount`, `emi_available`, `tenure_months`, `instalment`, `last_instalment`, `processing_fee`, `interest_saved`, `cashback_eligible`, `cashback_amount`, `recommended_option`, `benefit_amount` |
| **RR-02** | `emi_schedule.csv` | `ReportExportService` | `writeEmiSchedule(List<InstalmentRecord>, Writer)` | 3 columns: `bill_id`, `instalment_no`, `amount` (Instalment 1 includes processing fee) |
| **RR-03** | `returns_log.csv` | `ReportExportService` | `writeReturnsLog(List<ReturnLogRecord>, Writer)` | 7 columns: `return_id`, `bill_id`, `line_no`, `return_date`, `days_after_bill`, `within_window`, `cashback_reversed` |
| **RR-04** | `cashback_report.csv`| `ReportExportService` | `writeCashbackReport(List<CashbackReportRecord>, Writer)` | 7 columns: `month`, `customer_id`, `card_issuer`, `cashback_granted`, `cashback_reversed`, `net_liability`, `cap_remaining` |

---

## 5. Non-Functional Requirements & Architectural Constraints Matrix

| ID | Engineering Quality Metric | Architectural Implementation Strategy | Verification Protocol |
|---|---|---|---|
| **NFR-01** | Pure Java 17+ LTS | Target bytecode `release 17`; standard language idioms (Records, Switch Expressions, Streams). | Automated CI compiler check: `javac -version` |
| **NFR-02** | Zero External Dependencies | Use built-in `java.util`, `java.io`, `java.nio`, `java.time`, and `java.math`. | Binary dependency audit: empty pom/build dependencies |
| **NFR-03** | Deterministic Reproducibility | Strict sequential iteration matching `commands.txt` order; no parallel race conditions. | Bitwise SHA-256 hash comparison across runs |
| **NFR-04** | Processing Latency ($< 500$ ms) | In-memory `HashMap` lookups ($O(1)$) and buffered I/O streams (`BufferedReader`, `BufferedWriter`). | JMH microbenchmark & stopwatch profiling |
| **NFR-05** | Heap Memory Bound ($< 64$ MB) | Streamlined Plain Old Java Objects (POJOs) or Java Records; zero object graph bloating. | JVM runtime telemetry (`-Xmx64m`) |
| **NFR-06** | Financial Precision | Strict enforcement of `BigDecimal` with 2 decimal places and explicit `RoundingMode`. | Floating-point delta regression suite |
| **NFR-07** | State Synchronization | Transactional ledger mutations synchronized in date-interleaved command stream order. | Sequential ledger state assertion tests |
| **NFR-08** | Clean Code / SOLID | Strict separation of domain entities, repositories, and stateless calculation services. | Static analysis & code architecture review |

---

## 6. Verification & Test Execution Strategy

The verification strategy follows a four-tier testing pyramid:
1. **Tier 1: Unit Calculation Tests (Math & Policy)**
   - `testInterestSubventionMath()`: Validates simple interest formula against varied tenures and principals.
   - `testInstalmentRemainderDivision()`: Validates ceiling rounding and last instalment balance.
   - `testCashbackCappingBoundaries()`: Validates partial cap exhaustion and edge clamping.
2. **Tier 2: Business Rules & Trap Verification (Planted Traps)**
   - Dedicated test methods for each of `EC-01` through `EC-05` and `TRAP-01` through `TRAP-09`.
   - Asserts exact field-by-field outcomes matching teacher expectations.
3. **Tier 3: Stream & Pipeline Integration Tests**
   - Ingests `offer_config.csv`, `cards.csv`, and full 219-command `commands.txt`.
   - Asserts state continuity across interleaved bills and returns.
4. **Tier 4: Output File Schema & Format Golden Master Tests**
   - Asserts header conformity, column count, comma separation, decimal precision (`0.00`), and boolean string serialization.

