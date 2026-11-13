# ECE-035: Capstone Viva Examination & Architectural Defense Guide

**Project Title:** Electronics Retail No-Cost EMI and Cashback Offer Engine  
**Project ID:** ECE-035 (Team E005)  
**Target Platform:** Java 17+ LTS | Apache Maven | Pure Core Java (Zero External Runtime Libraries)  
**Execution Paradigm:** Sequential Command-Line Stream Processor (CLI)

---

## 1. Executive Summary & Architectural Overview

### 1.1 Project Purpose
ECE-035 is an enterprise-grade retail promotional engine designed to arbitrate financial promotions between **Merchant-Subvented No-Cost EMI** and **Instant Bank Cashback** for high-ticket electronic retail purchases. The engine acts in a **fiduciary capacity** on behalf of the customer, mathematically guaranteeing the maximum financial savings while strictly adhering to commercial banking agreements, statutory return regulations, and card issuer constraints.

### 1.2 Core Architectural Principles
1. **Zero External Runtime Dependencies:** Implemented strictly using the Java Standard Library (`java.math`, `java.time`, `java.nio`, `java.util`). No Apache Commons CSV, Jackson, Lombok, Spring, or Guava are permitted in production runtime. This guarantees zero supply-chain vulnerabilities, minimal memory overhead (<32 MB heap), sub-100 ms cold startup, and instant portability across any JVM.
2. **Fixed-Point Financial Determinism:** All currency representations utilize `java.math.BigDecimal` with scale 2 and explicit `RoundingMode` specifications (`HALF_UP` for interest and cashback calculations; `CEILING` for base monthly instalment division).
3. **Strict Order of Precedence:** Bundle discounts (merchandise-level promotions) are computed first and deducted from gross amounts to establish `bill_amount`. All downstream banking thresholds ($\ge \text{Rs } 5,000.00$ for EMI; $\ge \text{Rs } 25,000.00$ for fee waiver), interest subventions, and cashbacks evaluate exclusively against this discounted net amount.
4. **Stateful Customer Ledger:** In-memory ledger tracks real-time rolling monthly cashback caps ($\text{Rs } 1,500.00$ per customer per card issuer per calendar month) across sequential chronological command streams, with dynamic restoration upon valid merchandise returns.

```
                           +-------------------------------------+
                           |            commands.txt             |
                           +-------------------------------------+
                                              |
                                              v
                              +-------------------------------+
                              |       CommandProcessor        |
                              +-------------------------------+
                                              |
        +-------------------------------------+-------------------------------------+
        |                                     |                                     |
        v                                     v                                     v
+---------------+                     +---------------+                     +---------------+
|  LOAD_CONFIG  |                     |  LOAD_CARDS   |                     |     BILL      |
+---------------+                     +---------------+                     +---------------+
        |                                     |                                     |
        v                                     v                                     v
[OfferConfiguration]                  [CardRepository]                      [BillRepository]
                                                                                    |
                                              +-------------------------------------+
                                              |
                                              v
                              +-------------------------------+
                              |    OfferArbitrationEngine     |
                              +-------------------------------+
                                 |            |            |
                     +-----------+            |            +-----------+
                     |                        |                        |
                     v                        v                        v
             +---------------+        +---------------+        +---------------+
             | BundleDiscount|        | EmiCalculator |        |CashbackService|
             |    Service    |        |    Service    |        | (Ledger Repo) |
             +---------------+        +---------------+        +---------------+
                     |                        |                        |
                     +-----------+            |            +-----------+
                                 |            |            |
                                 v            v            v
                              +-------------------------------+
                              |          OfferResult          |
                              +-------------------------------+
                                              |
        +-------------------------------------+-------------------------------------+
        |                                     |                                     |
        v                                     v                                     v
+---------------+                     +---------------+                     +---------------+
| offer_summary |                     | emi_schedule  |                     |  returns_log  |
|     .csv      |                     |     .csv      |                     |     .csv      |
+---------------+                     +---------------+                     +---------------+
```

---

## 2. Business Rule Register (BR-01 through BR-21)

| Rule ID | Domain Category | Business Definition | Implementation Target |
| :--- | :--- | :--- | :--- |
| **BR-01** | Card Eligibility | Only credit cards with `emi_eligible = true` qualify for No-Cost EMI. | `EmiCalculatorService.java` |
| **BR-02** | Card Eligibility | Only credit cards with `cashback_eligible = true` qualify for Instant Cashback. | `CashbackService.java` |
| **BR-03** | Exclusion | Non-partner cards (e.g. `AMEX`) are ineligible for both promotional tracks. | `OfferArbitrationEngine.java` |
| **BR-04** | Dual Capability | Cards with both flags enabled (HDFC, ICICI, KOTAK) enter arbitration. | `OfferArbitrationEngine.java` |
| **BR-05** | Single Capability | Cards with single flag enabled (SBI = EMI only, AXIS = Cashback only) bypass arbitration. | `OfferArbitrationEngine.java` |
| **BR-06** | EMI Request | Customer requests EMI by specifying `tenure_months` in bill header; blank implies upfront payment. | `Bill.java`, `EmiCalculatorService.java` |
| **BR-07** | EMI Minimum | Net bill amount must be $\ge \text{Rs } 5,000.00$ to qualify for EMI. | `EmiCalculatorService.java` |
| **BR-08** | Permissible Tenure | Only 3, 6, and 9 months tenures are permissible; any other tenure is rejected. | `OfferConfiguration.java` |
| **BR-09** | Processing Fee | Standard fee is Rs 199.00; fee is completely waived (Rs 0.00) if net bill $\ge \text{Rs } 25,000.00$. | `EmiCalculatorService.java` |
| **BR-10** | Interest Subvention | Retailer bears interest: $\text{Interest Saved} = P \times \frac{\text{Rate}}{100} \times \frac{T}{12}$ at 15.0% p.a. | `EmiCalculatorService.java` |
| **BR-11** | Instalment Rounding | Base monthly instalment uses ceiling division: $\lceil P / T \rceil$. Final instalment absorbs remainder: $P - (\text{Base} \times (T - 1))$. | `EmiCalculatorService.java` |
| **BR-12** | Schedule Upfront Fee | If processing fee applies (Rs 199.00), it is added to Month 1 instalment amount. | `EmiCalculatorService.java` |
| **BR-13** | Bundle (TV+Soundbar) | Purchasing TV and SOUNDBAR on same bill gives flat Rs 2,000.00 discount. | `BundleDiscountService.java` |
| **BR-14** | Bundle (Phone+Case) | Purchasing PHONE and CASE on same bill gives 50% discount on each CASE line item. | `BundleDiscountService.java` |
| **BR-15** | Precedence Order | Bundle discounts are deducted from gross amount *before* evaluating EMI or Cashback. | `OfferArbitrationEngine.java` |
| **BR-16** | Arbitration Fiduciary | When both qualify, recommend the option providing the highest financial savings to customer. | `OfferArbitrationEngine.java` |
| **BR-17** | Tie Breaker | If Interest Saved equals Cashback Amount, tie breaks deterministically in favor of `CASHBACK`. | `OfferArbitrationEngine.java` |
| **BR-18** | Fallback | If neither promotional track qualifies, recommend `NONE` with benefit amount Rs 0.00. | `OfferArbitrationEngine.java` |
| **BR-19** | Return Duration | Return duration measured as calendar days: $\text{return\_date} - \text{bill\_date}$. | `ReturnProcessingService.java` |
| **BR-20** | 7-Day Window | Returns $\le 7$ days reverse awarded cashback. Returns $> 7$ days reject cashback reversal. | `ReturnProcessingService.java` |
| **BR-21** | Cap Restoration | Reversing cashback restores customer's monthly cap in ledger for subsequent purchases. | `CustomerLedgerRepository.java` |

---

## 3. Evaluator Trap & Edge Case Defense Matrix

During your viva examination, evaluators will interrogate specific planted test records in `commands.txt`, `bills.csv`, and `returns.csv`. Use the exact proofs below to defend your engine:

```
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| Trap ID   | Record ID  | Adversarial Condition Tested        | Expected Behavior & Mathematical Defense              |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-1    | BILL-0030  | Bill amount = Rs 4,999.00           | EMI unavailable (falls Rs 1.00 short of Rs 5000.00).  |
|           |            |                                     | Recommends CASHBACK (Rs 499.90 benefit).              |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-2    | BILL-0053  | Bill amount = Rs 24,999.00          | Fee of Rs 199.00 charged (falls Rs 1.00 short of      |
|           |            |                                     | waiver threshold of Rs 25,000.00).                    |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-3    | BILL-0066  | TV (20k) + Soundbar (7k) = 27k      | 27k gross - 2k bundle discount = 25,000.00 net.       |
|           |            | Threshold evaluated POST-discount   | Fee is WAIVED (Rs 0.00) because 25,000 >= 25,000!      |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-4    | BILL-0088  | Card issuer = AMEX                  | Non-partner card (emi=false, cb=false).               |
|           |            |                                     | Recommends NONE with Rs 0.00 benefit.                 |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-5    | BILL-0096  | Tenure = 12 months                  | Tenure 12 is invalid (only 3, 6, 9 permissible).      |
|           |            | Card = SBI                          | EMI rejected. SBI has cb=false -> Recommends NONE.    |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-6    | BILL-0099  | Card = AXIS                         | AXIS is cashback-only (emi=false, cb=true).           |
|           |            | Tenure = 3 months requested         | Tenure ignored; customer receives CASHBACK.           |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-7    | BILL-0119  | Phone (30k) + 2 Case items          | 50% discount applies to EACH case line item:          |
|           |            | Case 1 (1000) + Case 2 (800)        | 500 + 400 = 900.00 total discount. Gross 31.8k -> 30.9k|
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-8    | RET-014    | Return on BILL-0012 after 20 days   | 20 days > 7 days window. within_window = false.       |
|           |            |                                     | Cashback reversed = Rs 0.00 (Customer keeps cashback).|
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| TRAP-9    | RET-003    | Return on BILL-0052 (EMI purchase)  | within_window = true (2 days), but original bill won  |
|           |            |                                     | EMI! Cashback reversed = Rs 0.00 (no cashback to claw)|
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| EC-1      | BILL-0026  | Exact Rs 5,000.00 boundary          | EMI available = true (meets >= 5000.00).              |
|           | BILL-0039  | Exact Rs 25,000.00 boundary         | Fee = Rs 0.00 (meets >= 25000.00 waiver).             |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| EC-2      | BILL-0012  | CUST-003, AXIS, Oct 2026            | Bill 12 earns 900.00 (remaining 600.00).              |
|           | BILL-0062  |                                     | Bill 62 earns 500.00 (remaining 100.00).              |
|           | BILL-0102  | Cumulative cap saturation           | Bill 102 earns 100.00 (cap saturated: 1500/1500).     |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| EC-3      | BILL-0052  | Rs 10,000 / 3 months                | 10000 / 3 = 3333.33... -> Base instalment = 3334.00.  |
|           |            | Non-divisible instalment            | Last instalment = 10000 - (3334 * 2) = 3332.00.       |
|           |            |                                     | Month 1 adds Rs 199 fee = Rs 3,533.00.                |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| EC-4      | BILL-0016  | KOTAK, 6m, Rs 20,000.00             | Interest saved = 20000 * 0.15 * 6/12 = 1,500.00.      |
|           |            | Exact financial tie                 | Cashback = 10% of 20000 = 2000 capped at 1,500.00.    |
|           |            |                                     | Tie breaks deterministically to CASHBACK.             |
+-----------+------------+-------------------------------------+-------------------------------------------------------+
| EC-5      | RET-004    | Return on 7th calendar day          | Days elapsed = 7. within_window = true. Reverses 450. |
|           | RET-005    | Return on 8th calendar day          | Days elapsed = 8. within_window = false. Reversal = 0.|
+-----------+------------+-------------------------------------+-------------------------------------------------------+
```

---

## 4. Key Financial Algorithms & Mathematical Formulations

### 4.1 Interest Subvention (Retailer Cost of No-Cost EMI)
$$\text{Interest Saved} = P \times \left(\frac{\text{emi\_interest\_pa\_pct}}{100}\right) \times \left(\frac{\text{tenure\_months}}{12}\right)$$
- Evaluated with scale 6 intermediate fractions and rounded to scale 2 using `RoundingMode.HALF_UP`.
- *Example (BILL-0016):* $20,000.00 \times 0.15 \times \frac{6}{12} = \text{Rs } 1,500.00$.

### 4.2 Non-Divisible Monthly Instalment Calculation
$$\text{Base Instalment} = \lceil P / T \rceil = \text{P.divide}(T, 0, \text{RoundingMode.CEILING}).\text{setScale}(2)$$
$$\text{Last Instalment} = P - (\text{Base Instalment} \times (T - 1))$$
- *Example (BILL-0052, Principal = Rs 10,000.00, Tenure = 3):*
  - $\text{Base Instalment} = \lceil 10000 / 3 \rceil = 3334.00$
  - $\text{Sum for Months 1 and 2} = 3334.00 \times 2 = 6668.00$
  - $\text{Last Instalment} = 10000.00 - 6668.00 = 3332.00$
  - $\text{Total Principal Repaid} = 3334.00 + 3334.00 + 3332.00 = 10,000.00$ (exact to 0 paise).
  - *Repayment Schedule Month 1:* $3334.00 + 199.00 \text{ (Processing Fee)} = \text{Rs } 3,533.00$.

### 4.3 Instant Cashback & Cap Saturation
$$\text{Gross Cashback} = P \times \left(\frac{\text{cashback\_pct}}{100}\right) = P \times 0.10$$
$$\text{Remaining Cap} = \max(0, \text{Monthly Cap} - \text{Cumulative Grants} + \text{Cumulative Reversals})$$
$$\text{Awarded Cashback} = \min(\text{Gross Cashback}, \text{Remaining Cap})$$

---

## 5. Frequently Asked Viva Questions & Model Answers

### Q1: Why did you not use a relational database like MySQL or SQLite?
> **Answer:** The project specification explicitly stipulates a command-line application processing CSV feeds without external database engines. Relational databases introduce network latency, connection pooling overhead, JDBC configuration complexity, and external process dependencies. Our in-memory repository architecture with Java collections (`HashMap`, `ArrayList`, `LinkedHashMap`) provides $O(1)$ key lookups, completes all 218 transactions across 200 bills in under 75 milliseconds, and complies fully with the headless architectural requirements.

### Q2: Why did you use `BigDecimal` instead of `double` or `float`?
> **Answer:** Binary floating-point primitives (`double`, `float`) conform to IEEE-754 and represent numbers in base-2. Consequently, decimal fractions like $0.10$ or $0.15$ cannot be represented precisely, causing cumulative roundoff errors (e.g., $0.1 + 0.2 = 0.30000000000000004$). In retail banking and financial audit engines, missing or phantom paise constitute regulatory non-compliance. `BigDecimal` performs exact arbitrary-precision decimal arithmetic with configurable rounding modes (`HALF_UP`, `CEILING`), guaranteeing arithmetic precision down to 0 paise.

### Q3: Why is the bundle discount applied before checking the processing fee waiver?
> **Answer:** Commercial banking agreements stipulate that the financing amount ($P$) submitted to the card issuer is the net transaction amount payable by the consumer after all in-store retail discounts are deducted. If a customer purchases a TV (Rs 20,000) and Soundbar (Rs 7,000) with a Rs 2,000 bundle discount, the retailer finances only Rs 25,000.00. Per BR-09, the fee waiver threshold of $\ge \text{Rs } 25,000.00$ applies to the actual financed net amount ($P$). Evaluating the threshold on the gross amount would improperly waive fees on bills that finance less than Rs 25,000. This is explicitly verified by test case `BILL-0066`.

### Q4: If EMI and Cashback provide the exact same financial saving, which one is chosen and why?
> **Answer:** Per Business Rule BR-17 and Edge Case EC-04, an exact benefit tie breaks strictly in favor of `CASHBACK`. From a consumer finance perspective, instant cashback provides immediate liquidity and eliminates multi-month credit utilization on the customer's credit card. In our codebase, this is enforced in `OfferArbitrationEngine.java` (line 62), verified by test case `BILL-0016` where both EMI interest saved and cashback award equal exactly Rs 1,500.00.

### Q5: What happens when an item from an EMI bill is returned?
> **Answer:** Per Business Rule BR-20 and planted trap TRAP-9 (`RET-003`), returning an item purchased under No-Cost EMI produces a cashback reversal of exactly `0.00`. Because the customer received an upfront interest subvention rather than a bank cashback grant, there is no cashback liability in the customer's ledger account to claw back.

### Q6: How does the engine prevent duplicate bundle discounts or multiple case discounts?
> **Answer:** Per BR-13, the TV + Soundbar bundle grants a single flat Rs 2,000.00 discount per bill regardless of whether multiple TVs or Soundbars are purchased. However, per BR-14 and TRAP-7 (`BILL-0119`), if a qualifying phone is present, the 50% discount applies to *every* accessory case line item on the bill. The `BundleDiscountService` inspects the presence of product categories, applies the flat cap for TV/Soundbars, and iterates over all case items for phone bundles.

### Q7: How do you handle invalid tenures like tenure 12 in `BILL-0096`?
> **Answer:** Per BR-08 and TRAP-5, allowed tenures are strictly restricted to 3, 6, and 9 months (read dynamically from `offer_config.csv`). When tenure 12 is encountered on `BILL-0096`, `EmiCalculatorService` marks `emi_available = false`. Since `BILL-0096` uses an SBI card (which has `cashback_eligible = false`), neither promotional track qualifies, and the engine safely falls back to `NONE` with Rs 0.00 benefit.

### Q8: How does the ledger account maintain integrity across calendar months?
> **Answer:** In `CustomerLedgerRepository`, ledger accounts are keyed by a composite compound key: `customer_id + "|" + card_issuer + "|" + year_month` (e.g. `CUST-003|AXIS|2026-10`). This isolates monthly limits, preventing cashback grants in October from consuming or locking the customer's cap in November.

---

## 6. Live Demonstration Script (3-Minute Presentation)

```powershell
# Step 1: Clean build and execute full test suite (65 passing unit & integration tests)
mvn test

# Step 2: Package into standalone executable JAR
mvn package

# Step 3: Run engine against official mentor commands.txt
java -jar target/ece-035-offer-engine-1.0.0.jar commands.txt

# Step 4: Inspect generated offer summary report
Get-Content offer_summary.csv -Head 10

# Step 5: Verify specific planted trap records live
# Trap 3: BILL-0066 Fee Waiver Post Bundle Discount
Select-String -Path offer_summary.csv -Pattern "BILL-0066"

# Trap 7: BILL-0119 Multiple Case 50% Discount
Select-String -Path offer_summary.csv -Pattern "BILL-0119"

# Edge Case 4: BILL-0016 Tie-Breaker to Cashback
Select-String -Path offer_summary.csv -Pattern "BILL-0016"

# Edge Case 3: BILL-0052 Non-Divisible EMI Schedule
Select-String -Path emi_schedule.csv -Pattern "BILL-0052"

# Trap 8 & 9: RET-003 (EMI return) and RET-014 (Late return)
Select-String -Path returns_log.csv -Pattern "RET-003","RET-014"
```

---

## 7. Capstone Presentation Slide Outline (10 Slides)

* **Slide 1: Title Slide**  
  *ECE-035: Electronics Retail No-Cost EMI and Cashback Offer Engine*  
  Student Name / Team E005 | Department of Computer Science & Engineering  
* **Slide 2: Industry Problem Statement**  
  Financing electronics purchases: Merchant-subvented No-Cost EMI vs Bank Instant Cashback. Need for a fiduciary, deterministic decision engine.  
* **Slide 3: System Requirements & Scope**  
  Java 17 CLI | Zero runtime dependencies | 5 CSV inputs + sequential command stream | 4 evaluation output reports.  
* **Slide 4: Layered Software Architecture**  
  Domain-Driven Layering: Command Processing $\rightarrow$ Domain Repositories $\rightarrow$ Core Services (Bundle, EMI, Cashback) $\rightarrow$ Fiduciary Arbitration Engine $\rightarrow$ Report Exporter.  
* **Slide 5: Business Rules & Order of Precedence**  
  BR-01 to BR-21: Gross Amount $\rightarrow$ Bundle Discount $\rightarrow$ Net Financed Amount $\rightarrow$ EMI & Cashback Evaluation $\rightarrow$ Arbitration $\rightarrow$ Ledger Commitment.  
* **Slide 6: Financial Mathematics & Determinism**  
  Subvention interest formulas, `BigDecimal` scale-2 precision, non-divisible instalment ceiling and remainder absorption.  
* **Slide 7: Planted Traps & Adversarial Edge Cases**  
  Detailed analysis of TRAP 1–9 and EC 1–5: Rs 4,999 vs Rs 5,000; Rs 24,999 vs Rs 25,000; post-bundle fee waiver (`BILL-0066`); multiple cases (`BILL-0119`).  
* **Slide 8: Returns & Cap Restoration Lifecycle**  
  Statutory 7-day window boundary (`RET-004` vs `RET-005`), conditional clawbacks, ledger cap restoration.  
* **Slide 9: Quality Assurance & Verification**  
  65 comprehensive JUnit 5 tests covering all 18 planted scenarios; 75 ms processing time for 218 commands across 200 bills.  
* **Slide 10: Conclusion & Technical Takeaways**  
  Zero-dependency enterprise architecture; mathematical guarantee of zero paise drift; production-ready command engine.

