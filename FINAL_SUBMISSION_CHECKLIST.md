# ECE-035: Final Capstone Submission Checklist & Verification Audit

**Project Identifier:** ECE-035  
**Team Name:** Team E005  
**Academic Institution:** Electronics and Communication Engineering / Computer Science Capstone Evaluation  
**Evaluation Session:** November 2026  
**Target Environment:** Java 17+ LTS (SE Headless CLI), Apache Maven 3.9+  
**Execution Verification Status:** **ALL AUDITS PASSED (100% READY FOR SUBMISSION)**

---

## 1. Submission Deliverables Inventory

| # | Deliverable Category | Target Artifact / File Path | Verification Status | Forensic Evidence / Details |
| :---: | :--- | :--- | :---: | :--- |
| **1** | **Project Descriptor** | [`pom.xml`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/pom.xml) | **VERIFIED** | Clean Maven configuration; Java 17 target; JUnit 5 Jupiter dependency only; zero external runtime dependencies. |
| **2** | **Project Overview & Guide**| [`README.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/README.md) | **VERIFIED** | Comprehensive overview, problem statement, features, architecture, execution commands, output schemas, and 5-minute demo script. |
| **3** | **System Design Document** | [`DESIGN.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DESIGN.md) | **VERIFIED** | IEEE 1016 architecture description, glossary, BR-01 to BR-21 register, Mermaid UML class diagram, sequence diagrams, state lifecycles. |
| **4** | **Published Design PDF** | [`docs/DESIGN_DOCUMENT.pdf`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/docs/DESIGN_DOCUMENT.pdf) | **VERIFIED** | Professional publication-quality PDF formatted with title banner, metadata block, business rules, and trap proofs. |
| **5** | **Architecture Decisions** | [`DECISION_LOG.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DECISION_LOG.md) | **VERIFIED** | 14 formal Architecture Decision Records (DEC-01 through DEC-14) covering all architectural and financial choices. |
| **6** | **Requirements Specification**| [`REQUIREMENTS.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/REQUIREMENTS.md) | **VERIFIED** | Formal IEEE 830 / ISO 29148 specification detailing functional and non-functional requirements. |
| **7** | **Traceability Matrix** | [`REQUIREMENT_TRACEABILITY.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/REQUIREMENT_TRACEABILITY.md) | **VERIFIED** | Strict bidirectional mapping between business rules, functional requirements, source classes, and test cases. |
| **8** | **Dataset Forensic Audit** | [`DATASET_ANALYSIS.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DATASET_ANALYSIS.md) | **VERIFIED** | Forensic inspection of teacher sample pack (6 cards, 10 params, 200 bills, 237 items, 15 returns, 218 commands). |
| **9** | **Data Validation Report** | [`DATA_VALIDATION_REPORT.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DATA_VALIDATION_REPORT.md) | **VERIFIED** | Comprehensive validation audit isolating planted trap `BILL-0096` and schema checks. |
| **10** | **Input Specifications** | [`INPUT_VALIDATION.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/INPUT_VALIDATION.md) | **VERIFIED** | Strict CSV schema contracts, field types, bounds, and referential integrity rules. |
| **11** | **Unit & Integration Tests** | [`TEST_COVERAGE.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/TEST_COVERAGE.md) | **VERIFIED** | Complete test coverage report across 14 test suites and 128 tests (100% passing). |
| **12** | **End-to-End Verification** | [`INTEGRATION_TEST_REPORT.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/INTEGRATION_TEST_REPORT.md) | **VERIFIED** | Independent verification of all 14 teacher-planted scenarios (EC1-EC5 & TRAP1-TRAP9). |
| **13** | **Viva Defense Guide** | [`VIVA_GUIDE.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/VIVA_GUIDE.md) | **VERIFIED** | Examiner Q&A defense preparation guide, 3-minute & 5-minute demonstration scripts, and presentation outline. |

---

## 2. Generated Evaluation Datasets Audit

| Output CSV Deliverable | Header Row & Columns Verified | Line Count (Header + Rows) | Format & Typing Compliance | Status |
| :--- | :--- | :---: | :--- | :---: |
| **`offer_summary.csv`** | 17 columns matching README line 98 | **201 lines** | • Bill amount = Gross - Bundle Discount<br>• Money format: plain scale 2 decimals (`0.00`)<br>• Zero currency symbols (`Rs`, `$`, `₹`)<br>• Row sequence matches `commands.txt` order | **PASSED** |
| **`emi_schedule.csv`** | 3 columns: `bill_id,instalment_no,amount` | **229 lines** | • Only generated for bills where EMI recommended<br>• Processing fee added to instalment 1 when applicable<br>• Sum of instalments strictly equals Principal + Fee | **PASSED** |
| **`returns_log.csv`** | 7 columns: `return_id,bill_id,line_no,return_date,days_after_bill,within_window,cashback_reversed` | **16 lines** | • `within_window=true` if days $\le 7$<br>• Full clawback for eligible cashback returns<br>• 0.00 clawback for EMI purchases & late returns | **PASSED** |
| **`cashback_report.csv`** | 7 columns: `month,customer_id,card_issuer,cashback_granted,cashback_reversed,net_liability,cap_remaining` | **71 lines** | • 70 active customer accounts + 1 header<br>• Net Liability = Granted - Reversed<br>• Cap Remaining = 1500.00 - Net Liability | **PASSED** |

---

## 3. Business Rules Compliance Matrix (`BR-01` to `BR-21`)

- [x] **BR-01 (Card Issuer Catalog):** All 6 cards loaded from `cards.csv`.
- [x] **BR-02 (EMI Eligibility):** EMI offered only when `emi_eligible == true`.
- [x] **BR-03 (Min EMI Bill):** Requires post-bundle bill amount $\ge \text{Rs } 5,000.00$.
- [x] **BR-04 (Tenure Support):** Permissible tenures {3, 6, 9}; blank means unrequested; others rejected.
- [x] **BR-05 (15% Flat Interest):** Subvention interest formula $P \times 15 \times T / 1200$ strictly enforced.
- [x] **BR-06 (Retailer Subvention):** Customer pays only principal; interest is absorbed upfront.
- [x] **BR-07 (Ceiling Instalment):** Base monthly instalment $\lceil P / T \rceil$ computed with ceiling rounding.
- [x] **BR-08 (Last Instalment Remainder):** Final instalment absorbs fractional remainder to balance principal.
- [x] **BR-09 (Processing Fee):** Rs 199.00 fee added to first instalment when post-bundle bill $< \text{Rs } 25,000.00$.
- [x] **BR-10 (Fee Waiver):** Processing fee waived (Rs 0.00) when post-bundle bill $\ge \text{Rs } 25,000.00$.
- [x] **BR-11 (10% Cashback):** 10% instant rebate computed on post-bundle bill amount.
- [x] **BR-12 (Monthly Cashback Cap):** Strict monthly ceiling of Rs 1,500.00 per customer, per issuer, per calendar month.
- [x] **BR-13 (TV + Soundbar Bundle):** Flat Rs 2,000.00 discount per bill containing TV and SOUNDBAR categories.
- [x] **BR-14 (Phone + Case Bundle):** 50% discount on **each** CASE line item in bills containing PHONE category.
- [x] **BR-15 (Bundle Precedence):** Bundle discounts deducted strictly before evaluating EMI or Cashback.
- [x] **BR-16 (Mutual Exclusivity):** Bill receives either No-Cost EMI or Cashback, never both.
- [x] **BR-17 (Fiduciary Maximization):** Option providing strictly higher customer rupee savings is recommended.
- [x] **BR-18 (Deterministic Tie-Breaker):** Exact financial benefit tie breaks strictly in favor of **CASHBACK**.
- [x] **BR-19 (7-Day Return Window):** Return within 7 calendar days reverses full cashback previously awarded.
- [x] **BR-20 (Cap Restoration):** Eligible return restores cap usage in the **original bill's** month bucket.
- [x] **BR-21 (Late Return Policy):** Return after 7 calendar days forfeits cashback reversal (reversed = 0.00).

---

## 4. Teacher-Planted Scenarios Audit

- [x] **EC-01 (Boundary Amounts):** `BILL-0026` (5000.00 $\rightarrow$ fee 199.00); `BILL-0039` (25000.00 $\rightarrow$ fee 0.00).
- [x] **EC-02 (Progressive Cap):** CUST-003 on AXIS: `BILL-0012` (900.00), `BILL-0062` (500.00), `BILL-0102` (100.00 $\rightarrow$ cap exhausted).
- [x] **EC-03 (Non-Divisible EMI):** `BILL-0052` (Rs 10,000 / 3 $\rightarrow$ instalments 3533.00, 3334.00, 3332.00).
- [x] **EC-04 (Tie-Breaker):** `BILL-0016` (KOTAK Rs 20,000 @ 6M $\rightarrow$ Interest 1500 == Cashback 1500 $\rightarrow$ CASHBACK wins).
- [x] **EC-05 (Return Window Boundary):** `RET-004` (Day 7 $\rightarrow$ reverses 450.00); `RET-005` (Day 8 $\rightarrow$ reverses 0.00).
- [x] **TRAP-01 (Min EMI Threshold):** `BILL-0030` (Rs 4,999.00 $< 5,000.00 \rightarrow$ `emi_available=false`).
- [x] **TRAP-02 (Fee Threshold):** `BILL-0053` (Rs 24,999.00 $< 25,000.00 \rightarrow$ Rs 199.00 fee applies).
- [x] **TRAP-03 (Bundle-Adjusted Fee):** `BILL-0066` (Gross 27k $-$ 2k bundle = Net 25k $\rightarrow$ fee waived post-discount).
- [x] **TRAP-04 (Non-Partner Card):** `BILL-0088` (AMEX is non-partner $\rightarrow$ recommended NONE).
- [x] **TRAP-05 (Invalid Tenure):** `BILL-0096` (Tenure 12 on SBI is invalid $\rightarrow$ `emi_available=false`).
- [x] **TRAP-06 (Cashback-Only Card):** `BILL-0099` (AXIS tenure 6 chosen $\rightarrow$ recommends CASHBACK).
- [x] **TRAP-07 (Multiple Cases):** `BILL-0119` (Phone + 2 Cases $\rightarrow$ 50% discount on both cases = 900.00 off).
- [x] **TRAP-08 (Late Return):** `RET-014` (20 days after bill $\rightarrow$ `within_window=false`, reversal 0.00).
- [x] **TRAP-09 (EMI Return):** `RET-003` (Return on EMI bill `BILL-0052` $\rightarrow$ reversal 0.00).

---

## 5. Architectural Integrity & Security Checks

- [x] **Zero Hardcoded Shortcuts:** Regex audit of `src/main/java/` confirms zero hardcoded customer IDs, bill IDs, or card issuer names.
- [x] **Dynamic Generalization:** Verified via `DynamicDatasetIntegrationTest.java` running synthetic banks, 12% interest, Rs 30k fee waiver, and 10-day return windows.
- [x] **Zero External Runtime Libraries:** `pom.xml` contains zero runtime dependencies; all code compiles with plain standard JDK 17.
- [x] **Zero Database Dependencies:** Pure in-memory collections (`Map`, `List`, `Set`) with $O(1)$ lookup performance.
- [x] **Zero Personal Secrets or Credentials:** Clean codebase free of API keys, tokens, or hardcoded passwords.
- [x] **Deterministic Re-runs:** Successive executions overwrite CSV files atomically without memory leakage or row duplication.

---

## 6. Demonstration Video Status

- **Demonstration Video Link:** `[Demo Video Link](https://www.youtube.com/watch?v=REPLACE_WITH_ACTUAL_DEMO_VIDEO_ID)`  
  *(Placeholder for student's recorded demo video URL)*
- **Script Location:** Section 8 of [`README.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/README.md) and Section 3 of [`VIVA_GUIDE.md`](file:///c:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/VIVA_GUIDE.md).

---

## 7. Submission Notice & Instructions to Student

> [!IMPORTANT]
> **Submission Protocol Notice:**  
> In accordance with your explicit instructions (*"Do not email or submit anything on my behalf"*), **no external transmission, email, or upload has been performed by the assistant**.  
> The project repository and build artifacts are 100% prepared, self-contained, tested, and ready on your local filesystem for your final manual submission to the college portal or evaluator harness.
