# ECE-035: Electronics Retail No-Cost EMI & Cashback Offer Engine
### Capstone Project Final Submission — Team E005

[![Java Version](https://img.shields.io/badge/Java-17%2B%20LTS-blue.svg)](https://openjdk.org/)
[![Build Tool](https://img.shields.io/badge/Maven-3.9%2B-red.svg)](https://maven.apache.org/)
[![Test Suite](https://img.shields.io/badge/Tests-128%20Passed%20%2F%200%20Failed-brightgreen.svg)]()
[![Standard](https://img.shields.io/badge/Standard-IEEE%201016--2009-orange.svg)]()

---

## 1. Project Description & Problem Statement

In the modern consumer electronics retail industry, high-value transactions (smartphones, televisions, laptops, appliances) are heavily driven by financial promotional offers sponsored by partner banking institutions. At the checkout counter or point of sale (POS), consumers are routinely offered:
1. **No-Cost EMI (Equated Monthly Instalments):** A financing mechanism wherein the annual flat loan interest liability (typically 15% p.a.) is subsidized and absorbed directly by the merchant/manufacturer (interest subvention), allowing the consumer to repay only the purchase price in monthly instalments. Administrative processing fees (Rs 199.00) apply to transactions below Rs 25,000.00 and are waived at or above this threshold.
2. **Instant Card Cashback:** An upfront price reduction (typically 10% of the transaction amount) funded by the card issuer, subject to a statutory monthly ceiling (Rs 1,500.00 per customer, per card issuer, per calendar month).

### The Engineering Challenge
Co-sponsoring banking agreements strictly prohibit consumers from "stacking" both promotional benefits on the same purchase. Consequently, the retail engine must act as an **automated fiduciary arbitrator**:
- It must calculate the exact customer rupee savings for both options under real-world constraints (card eligibility, minimum thresholds, non-divisible instalment ceiling roundings, bundle discount deductions, and remaining monthly cashback caps).
- It must arbitrate and recommend the option that maximizes customer savings, applying a deterministic tie-breaker favoring **CASHBACK** when benefits are equal.
- It must track customer monthly liabilities in an in-memory audit ledger, handle item returns within a 7-day statutory window by reversing cashback, and emit standardized evaluation reports.
- **Strict Architecture Boundaries:** Pure Core Java 17+ LTS CLI with **zero third-party runtime libraries**, **zero database dependencies**, and strictly deterministic stream processing.

---

## 2. Key Features & Business Rules (`BR-01` to `BR-21`)

- **Dynamic Configuration:** Loads 10 system offer parameters from `offer_config.csv` (thresholds, fees, rates, return windows, tenures).
- **Partner Card Catalog:** Enforces issuer-level eligibility flags for EMI and Cashback from `cards.csv`.
- **Pre-Discount Bundle Engine:** Automatically computes flat Rs 2,000.00 bundle discount for TV + SOUNDBAR and 50% discount on each CASE line when purchased with a PHONE.
- **No-Cost EMI Calculation Engine:**
  - Annual interest subvention formula: $\text{Interest} = P \times (\text{Rate} / 100) \times (\text{Tenure} / 12)$.
  - Non-divisible instalments: Ceiling rounding $\lceil P / T \rceil$ on base instalments, absorbing remainder in the final month.
  - Processing fee: Rs 199.00 added to instalment 1 if post-bundle bill $< \text{Rs } 25,000.00$; waived (Rs 0.00) if $\ge \text{Rs } 25,000.00$.
- **Instant Cashback & Cap Tracking Engine:**
  - 10% instant rebate evaluated on post-bundle bill amount.
  - Multi-customer, multi-issuer, calendar-month cap isolation (Rs 1,500.00 ceiling).
  - Progressive cap saturation handling partial and exhausted limits.
- **Merchandise Return & Clawback Engine:**
  - Statutory 7-day window enforcement ($\text{return\_date} - \text{bill\_date} \le 7$).
  - Eligible returns reverse full awarded cashback and restore cap usage in the **original bill's** month ledger.
  - Returns beyond 7 days forfeit reversal. EMI purchases produce Rs 0.00 clawback.
- **Sequential Command Processor:**
  - Tokenizes and executes `commands.txt` line by line preserving chronological sequence.
  - Skips comments (`#`) and empty lines; maintains line diagnostics.

---

## 3. Project Architecture & Clean Layered Design

The system implements a clean, decoupled 4-tier domain architecture:

```
c:\Users\Thriveni S A\OneDrive\Desktop\Electronic\
├── pom.xml                                  # Maven project configuration (Java 17, JUnit 5)
├── README.md                                # Comprehensive project guide & documentation
├── TEACHER_SAMPLE_DATA_README.md            # Original teacher sample pack reference
├── DESIGN.md                                # Full IEEE 1016 Software Architecture Specification
├── DECISION_LOG.md                          # Architecture Decision Records (ADR-01 to ADR-14)
├── REQUIREMENTS.md                          # IEEE 830 / ISO 29148 Software Requirements Specification
├── REQUIREMENT_TRACEABILITY.md              # Bidirectional Requirement Traceability Matrix (RTM)
├── DATASET_ANALYSIS.md                      # Forensic analysis of mentor data pack & planted traps
├── DATA_VALIDATION_REPORT.md                # Data integrity audit report
├── INPUT_VALIDATION.md                      # CSV schemas & validation specifications
├── TEST_COVERAGE.md                         # Unit & integration test coverage report (128 tests)
├── INTEGRATION_TEST_REPORT.md               # End-to-end integration test execution report
├── FINAL_SUBMISSION_CHECKLIST.md            # Capstone pre-submission verification checklist
├── VIVA_GUIDE.md                            # Examiner Q&A defense guide & demonstration script
│
├── docs/                                    # Generated documentation artifacts
│   └── DESIGN_DOCUMENT.pdf                  # Professional IEEE 1016 System Design PDF
│
├── src/main/java/com/retail/engine/
│   ├── Main.java                            # CLI entry point
│   ├── EngineBootstrap.java                 # Lifecycle orchestrator & dependency wiring
│   ├── model/                               # Domain models & immutable records
│   │   ├── Bill.java, BillItem.java, CardIssuer.java, OfferConfiguration.java
│   │   ├── EmiSchedule.java, CashbackTransaction.java, ReturnTransaction.java
│   │   ├── BundleDiscount.java, OfferResult.java, ReturnLogRecord.java, CashbackReport.java
│   │   └── ProductCategory.java, OfferType.java
│   ├── config/                              # Configuration loader & validation
│   │   ├── OfferConfiguration.java, ConfigLoader.java
│   ├── repository/                          # In-memory indexing & ledger tracking
│   │   ├── CardRepository.java, BillRepository.java, ReturnRepository.java
│   │   └── CustomerLedgerRepository.java
│   ├── service/                             # Pure business calculation engines
│   │   ├── BundleDiscountService.java, EmiCalculatorService.java, CashbackService.java
│   │   ├── OfferArbitrationEngine.java, ReturnProcessingService.java
│   │   └── ReportExportService.java
│   ├── command/                             # Command parsing & sequential execution
│   │   ├── Command.java, CommandProcessor.java, CommandType.java
│   ├── validation/                          # Strict CSV schema & domain validators
│   │   └── DataValidator.java
│   └── exception/                           # Exception hierarchy with line diagnostics
│       ├── EngineException.java, ValidationException.java, ConfigurationException.java
│
└── src/test/java/com/retail/engine/         # 14 JUnit 5 Test Suites (128 passing tests)
    ├── ConfigurationTest.java               # Config loading & defaults (8 tests)
    ├── CardEligibilityTest.java             # Partner card rules & flags (10 tests)
    ├── BundleDiscountEngineTest.java        # TV+Soundbar & Phone+Case rules (8 tests)
    ├── EmiEngineTest.java                   # EMI formulas, fees & invariance (15 tests)
    ├── CashbackEngineTest.java              # 10% rebate & card filtering (12 tests)
    ├── CapTrackingTest.java                 # Rs 1500 monthly cap & restoration (6 tests)
    ├── ReturnProcessingEngineTest.java      # 7-day window & clawbacks (8 tests)
    ├── OfferArbitrationTest.java            # Fiduciary decision & tie breaker (13 tests)
    ├── CommandProcessingTest.java           # Command parser & order tests (7 tests)
    ├── InputProcessingTest.java             # CSV schemas & referential integrity (10 tests)
    ├── ReportFormattingTest.java            # Exact headers, formats & scale (6 tests)
    ├── DynamicDatasetIntegrationTest.java   # Generalization on new dataset (1 test)
    ├── DomainModelTest.java                 # Value objects & immutability (5 tests)
    └── FullEngineEvaluationTest.java        # All 200 bills & teacher traps (19 tests)
```

---

## 4. Input CSV Data Feeds & Configuration Specifications

All dates follow ISO-8601 `YYYY-MM-DD`. All monetary figures are in plain decimals (no currency symbols).

1. **`cards.csv` (6 records):**
   - Columns: `issuer_code, issuer_name, emi_eligible, cashback_eligible`
   - Issuers: `HDFC` (EMI+CB), `ICICI` (EMI+CB), `SBI` (EMI-only), `AXIS` (CB-only), `KOTAK` (EMI+CB), `AMEX` (Non-partner).
2. **`offer_config.csv` (10 parameters):**
   - `EMI_MIN_BILL`: `5000.00`
   - `EMI_TENURES`: `3;6;9`
   - `EMI_INTEREST_PA_PCT`: `15`
   - `PROCESSING_FEE`: `199.00`
   - `PROCESSING_FEE_WAIVED_FROM`: `25000.00`
   - `CASHBACK_PCT`: `10`
   - `CASHBACK_CAP_PER_CARD_CUSTOMER_MONTH`: `1500.00`
   - `BUNDLE_TV_SOUNDBAR_OFF`: `2000.00`
   - `BUNDLE_PHONE_CASE_PCT_OFF_CASE`: `50`
   - `RETURN_WINDOW_DAYS`: `7`
3. **`bills.csv` (200 records):**
   - Columns: `bill_id, customer_id, bill_date, card_issuer, tenure_months`
4. **`bill_items.csv` (237 line items):**
   - Columns: `bill_id, line_no, category, item_name, price`
5. **`returns.csv` (15 records):**
   - Columns: `return_id, bill_id, line_no, return_date`
6. **`commands.txt` (218 commands):**
   - Syntax: `COMMAND_NAME|arg1|arg2`
   - Supported verbs: `LOAD_CONFIG`, `LOAD_CARDS`, `BILL`, `RETURN`, `PRINT_CASHBACK_REPORT`.

---

## 5. Output Specifications (Teacher-Specified Evaluation CSVs)

Execution produces exactly four CSV files in the current working directory:

### 1. `offer_summary.csv` (Exactly 201 lines: 1 header + 200 bills)
Columns:
`bill_id,customer_id,bill_date,card_issuer,gross_amount,bundle_discount,bill_amount,emi_available,tenure_months,instalment,last_instalment,processing_fee,interest_saved,cashback_eligible,cashback_amount,recommended_option,benefit_amount`
- `bill_amount` = `gross_amount` − `bundle_discount`.
- `recommended_option` = `EMI`, `CASHBACK`, or `NONE`.
- `benefit_amount` = `interest_saved` for EMI, `cashback_amount` for CASHBACK, `0.00` for NONE.

### 2. `emi_schedule.csv` (Exactly 229 lines: 1 header + 228 instalment rows)
Columns:
`bill_id,instalment_no,amount`
- Generated only for bills where `recommended_option == EMI`.
- Instalment 1 includes the Rs 199.00 processing fee when applicable ($< \text{Rs } 25,000.00$).

### 3. `returns_log.csv` (Exactly 16 lines: 1 header + 15 return rows)
Columns:
`return_id,bill_id,line_no,return_date,days_after_bill,within_window,cashback_reversed`
- `within_window` is `true` if $\text{return\_date} - \text{bill\_date} \le 7$.
- `cashback_reversed` claws back cashback granted; 0.00 for EMI purchases or late returns.

### 4. `cashback_report.csv` (Exactly 71 lines: 1 header + 70 active customer accounts)
Columns:
`month,customer_id,card_issuer,cashback_granted,cashback_reversed,net_liability,cap_remaining`
- Invariance: $\text{net\_liability} = \text{cashback\_granted} - \text{cashback\_reversed}$.
- $\text{cap\_remaining} = 1500.00 - \text{net\_liability}$.

---

## 6. Execution & Build Instructions

### Prerequisites
- **JDK 17 LTS or higher** (`java -version` returns 17+)
- **Apache Maven 3.9+** (`mvn -version`)
- **Git** (`git --version`)

### Step 1: Clean Build and Packaging
```bash
mvn clean package
```
*Expected Result:* Compiles 34 main source files, executes 128 tests (100% pass), and packages `target/ece-035-offer-engine-1.0.0.jar`.

### Step 2: Run Full Automated Test Suite
```bash
mvn test
```
*Expected Result:* 128 tests pass across 14 test suites in ~3.5 seconds with 0 failures, 0 errors, and 0 skipped.

### Step 3: Run the CLI Application
```bash
# Run with default commands.txt
java -jar target/ece-035-offer-engine-1.0.0.jar

# Or pass custom command file explicitly
java -jar target/ece-035-offer-engine-1.0.0.jar commands.txt
```
*Expected Terminal Output:*
```text
================================================================
ECE-035: Electronics Retail No-Cost EMI & Cashback Offer Engine
================================================================
Initializing Engine at workspace: .
Processing command stream: commands.txt
Execution completed successfully in 65 ms.
Evaluation reports generated:
 - offer_summary.csv
 - emi_schedule.csv
 - returns_log.csv
 - cashback_report.csv
```

---

## 7. Forensic Verification of Planted Scenarios

| Code | Target Record & File Location | Condition & Expected Behavior | Actual Result |
| :--- | :--- | :--- | :--- |
| **EC-01** | `BILL-0026` & `BILL-0039` (`bills.csv:27,40`) | 5000.00 on HDFC (fee 199.00 applies); 25000.00 on ICICI (fee waived to 0.00). | **PASS** |
| **EC-02** | `BILL-0012, 0062, 0102` (`bills.csv:13,63,103`)| CUST-003 AXIS: earns 900.00, then 500.00 (used 1400), then capped at 100.00. | **PASS** |
| **EC-03** | `BILL-0052` (`bills.csv:53`) | Rs 10,000 / 3 on SBI: instalments 3533.00 (3334+199), 3334.00, 3332.00. | **PASS** |
| **EC-04** | `BILL-0016` (`bills.csv:17`) | KOTAK Rs 20,000 @ 6M: Interest 1500.00 == Cashback 1500.00 $\rightarrow$ CASHBACK wins. | **PASS** |
| **EC-05** | `RET-004` vs `RET-005` (`returns.csv:5,6`) | Day 7 return (reverses 450.00) vs Day 8 return (rejected, reverses 0.00). | **PASS** |
| **TRAP-01** | `BILL-0030` (`bills.csv:31`) | Rs 4,999.00 below Rs 5,000.00 min threshold $\rightarrow$ `emi_available=false`. | **PASS** |
| **TRAP-02** | `BILL-0053` (`bills.csv:54`) | Rs 24,999.00 below Rs 25,000.00 waiver threshold $\rightarrow$ Rs 199.00 fee applies. | **PASS** |
| **TRAP-03** | `BILL-0066` (`bills.csv:67`) | TV 20k + Soundbar 7k - 2k bundle = 25k net $\rightarrow$ fee waived post-discount. | **PASS** |
| **TRAP-04** | `BILL-0088` (`bills.csv:89`) | AMEX non-partner card $\rightarrow$ recommends NONE, benefit 0.00. | **PASS** |
| **TRAP-05** | `BILL-0096` (`bills.csv:97`) | Planted tenure 12 is invalid $\rightarrow$ `emi_available=false`, recommends NONE. | **PASS** |
| **TRAP-06** | `BILL-0099` (`bills.csv:100`)| Tenure 6 chosen on cashback-only AXIS card $\rightarrow$ recommends CASHBACK. | **PASS** |
| **TRAP-07** | `BILL-0119` (`bills.csv:120`)| Phone + 2 Cases $\rightarrow$ 50% discount on **each** case ($500 + 400 = 900.00$ off). | **PASS** |
| **TRAP-08** | `RET-014` (`returns.csv:15`) | Return after 20 days $\rightarrow$ outside window $\rightarrow$ `cashback_reversed=0.00`. | **PASS** |
| **TRAP-09** | `RET-003` (`returns.csv:4`)  | Return on EMI bill (`BILL-0052`) $\rightarrow$ zero cashback clawback (`0.00`). | **PASS** |

---

## 8. Five-Minute Capstone Demonstration Guide

### Demonstration Video
- **Recorded Demonstration URL:** `[Demo Video Link](https://www.youtube.com/watch?v=REPLACE_WITH_ACTUAL_DEMO_VIDEO_ID)`  
  *(Placeholder for student's recorded demo video URL)*

### Detailed 5-Minute Demonstration Script

| Timeline | Phase | Action & What to Show | Key Talking Points |
| :--- | :--- | :--- | :--- |
| **0:00 - 0:45** | **Introduction & Architecture** | Show terminal and project structure in IDE. | State project identity ECE-035 (Team E005). Explain the retail POS problem: anti-stacking policies mandate mutual exclusivity between No-Cost EMI and Cashback. Highlight pure Java 17 headless CLI with zero external dependencies. |
| **0:45 - 1:30** | **Clean Build & Automated Tests** | Run `mvn clean test` in terminal. | Highlight 128 tests passing across 14 test suites in ~3.5s. Point out dedicated tests for every business rule (`BR-01` to `BR-21`), invariance theorem proofs, and synthetic dataset generalization. |
| **1:30 - 2:30** | **CLI Execution & Core Engines** | Run `java -jar target/ece-035-offer-engine-1.0.0.jar`. | Engine completes 218 commands across 200 bills in 65 ms. Explain bundle detection: TV+Soundbar (Rs 2,000 off) and Phone+Case (50% per case). Show `BILL-0066` where fee waiver evaluates post-bundle. |
| **2:30 - 3:30** | **EMI, Cashback & Tie-Breaker** | Open `offer_summary.csv` and inspect `BILL-0016` and `BILL-0052`. | Point out `BILL-0052`: Rs 10,000 / 3 gives ceiling 3334, 3334, 3332. Show `emi_schedule.csv` where Month 1 has Rs 199 fee. Show `BILL-0016`: Interest 1500 == Cashback 1500 $\rightarrow$ deterministic tie-break to CASHBACK. |
| **3:30 - 4:15** | **Ledger Capping & Returns** | Open `cashback_report.csv` and `returns_log.csv`. | Show `EC-02` CUST-003 progressive cap saturation (900 $\rightarrow$ 500 $\rightarrow$ 100). Show `EC-05` Day 7 return (`RET-004` reverses 450) vs Day 8 (`RET-005` rejects). Explain cross-month cap restoration in original bill month. |
| **4:15 - 5:00** | **Generalization & Defense Conclusion** | Show `DynamicDatasetIntegrationTest.java` and `DESIGN_DOCUMENT.pdf`. | Demonstrate that zero bank names or bill IDs are hardcoded. Engine dynamically adapts to any schema-compliant parameters. Conclude and invite questions. |

---

## 9. Submission Checklist & Deliverable Traceability

- **`pom.xml`**: Clean configuration targeting Java 17 and JUnit 5 only.
- **Source Code**: 34 classes across 8 structured packages under `src/main/java/com/retail/engine/`.
- **Test Code**: 14 test classes containing 128 tests under `src/test/java/com/retail/engine/`.
- **Generated Reports**: `offer_summary.csv`, `emi_schedule.csv`, `returns_log.csv`, `cashback_report.csv`.
- **Comprehensive Docs**: [`DESIGN.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DESIGN.md), [`DECISION_LOG.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DECISION_LOG.md), [`REQUIREMENTS.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/REQUIREMENTS.md), [`REQUIREMENT_TRACEABILITY.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/REQUIREMENT_TRACEABILITY.md), [`DATASET_ANALYSIS.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DATASET_ANALYSIS.md), [`TEST_COVERAGE.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/TEST_COVERAGE.md), [`INTEGRATION_TEST_REPORT.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/INTEGRATION_TEST_REPORT.md), [`docs/DESIGN_DOCUMENT.pdf`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/docs/DESIGN_DOCUMENT.pdf), [`VIVA_GUIDE.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/VIVA_GUIDE.md).
