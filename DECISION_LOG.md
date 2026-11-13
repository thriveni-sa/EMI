# ECE-035: Electronics Retail No-Cost EMI and Cashback Offer Engine
## Architectural & Engineering Decision Log (ADR)

**Document Version:** 1.0.0  
**Project Identifier:** ECE-035 (Team E005)  
**Standard:** Architecture Decision Record (ADR) Format  
**Scope:** Foundational Architectural, Design, and Financial Mathematical Decisions  

---

## Decision Index

- **DEC-01:** Target Pure Java 17+ SE with Zero External Third-Party Dependencies
- **DEC-02:** Pure Headless Command-Line Interface (CLI) Execution
- **DEC-03:** In-Memory Relational State and Zero External Database
- **DEC-04:** Use `BigDecimal` with Explicit Rounding Modes for All Currency & Percentages
- **DEC-05:** Sequential Deterministic Event-Stream Processing for Commands
- **DEC-06:** Evaluation of Processing Fee Waiver Post-Bundle Discount (TRAP-3 Resolution)
- **DEC-07:** Application of 50% Accessory Discount to Each Case Line Item (TRAP-7 Resolution)
- **DEC-08:** Deterministic Tie-Breaker Precedence Favoring Instant Cashback (EC-4 Resolution)
- **DEC-09:** Ceiling Rounding on Base Instalments with Remainder Absorption in Last Month (EC-3 Resolution)
- **DEC-10:** Immediate Restoration of Monthly Cashback Cap upon Valid Return Reversal
- **DEC-11:** EMI Processing Fee Treated as Downpayment/First Instalment and Kept Separate from `benefit_amount`
- **DEC-12:** Exact Inclusive Relational Operator ($\ge$) on Minimum Bill and Waiver Thresholds (EC-1 Resolution)
- **DEC-13:** Non-Crashing Graceful Invalidation on Unsupported Tenures (TRAP-5 Resolution)
- **DEC-14:** Automatic Fallback to Cashback on Cashback-Only Cards with Requested Tenure (TRAP-6 Resolution)

---

## Detailed Architectural Decision Records

### DEC-01: Target Pure Java 17+ SE with Zero External Third-Party Dependencies
- **Date:** 2026-11-13
- **Decision:** The entire application will be developed using pure Java 17+ Standard Edition (`java.base`), relying exclusively on standard library packages (`java.util`, `java.math`, `java.time`, `java.io`, `java.nio`). No external libraries (Apache Commons CSV, Jackson, Lombok, Guava) will be used.
- **Reason:** College capstone evaluation guidelines, portability across testing environments, and submission criteria mandate zero dependency friction. Running without third-party JARs ensures the project compiles instantly with plain `javac` on any standard Java 17+ JDK.
- **Alternatives Considered:** 
  - Using Apache Commons CSV and Lombok: Rejected due to external dependency constraints and evaluator evaluation harness restrictions.
- **Impact:** Eliminates classpath resolution errors; requires implementing dedicated, clean, zero-dependency `CsvReader` and `CsvWriter` components.

---

### DEC-02: Pure Headless Command-Line Interface (CLI) Execution
- **Date:** 2026-11-13
- **Decision:** Build strictly as a headless command-line application executed via `public static void main(String[] args)`. No web application, REST API, Swing, or JavaFX interface will be constructed.
- **Reason:** Project specifications explicitly state: *"There is no frontend, website or database requirement. Build type: command-line application. Input: CSV files and commands.txt."* Unnecessary UI layers add maintenance burden, violate scope boundaries, and risk non-conformance with automated evaluation scripts.
- **Alternatives Considered:** Building a lightweight Spring Boot or JavaFX dashboard: Rejected as direct violation of project constraints.
- **Impact:** Focused, testable, and streamlined execution lifecycle with zero UI overhead.

---

### DEC-03: In-Memory Relational State and Zero External Database
- **Date:** 2026-11-13
- **Decision:** Utilize pure in-memory data structures (`HashMap`, `ArrayList`, `TreeSet`) to maintain catalog lookups, customer ledgers, and transaction state. Do not use SQLite, H2, PostgreSQL, or any embedded database.
- **Reason:** Transaction volume for retail evaluation datasets (200 bills, 237 line items, 15 returns) is lightweight and executes entirely within < 10 MB of heap memory. In-memory maps provide instant $O(1)$ lookups, deterministic execution, and zero filesystem setup or schema migration issues.
- **Alternatives Considered:** Embedded SQLite or H2 database: Rejected because evaluator test runners expect self-contained execution without external drivers or locking file descriptors.
- **Impact:** Guarantees sub-500 ms execution time; requires proper encapsulation in Repository classes (`CardRepository`, `BillRepository`, `CustomerLedgerRepository`).

---

### DEC-04: Use `BigDecimal` with Explicit Rounding Modes for All Currency & Percentages
- **Date:** 2026-11-13
- **Decision:** Prohibit primitive `double` or `float` for all monetary amounts, interest calculations, bundle discounts, and percentage rates. Enforce `java.math.BigDecimal` with 2 decimal places and explicit `RoundingMode` across all services.
- **Reason:** Binary floating-point arithmetic (IEEE 754) introduces representation errors (e.g., $0.1 + 0.2 = 0.30000000000000004$), which cause catastrophic discrepancies in financial ledgers, tax compliance, and automated test comparisons.
- **Alternatives Considered:** Primitive `double` with string formatting: Rejected due to rounding artifacts during cumulative calculations.
- **Impact:** 100% mathematical precision and exact match with teacher evaluation outputs.

---

### DEC-05: Sequential Deterministic Event-Stream Processing for Commands
- **Date:** 2026-11-13
- **Decision:** The engine will parse and execute `commands.txt` strictly in file line sequence. All state mutations (cashback cap exhaustion and return reversals) will occur synchronously in command order.
- **Reason:** Evaluator README notes: *"Commands must be processed in file order (bills and returns are interleaved by date so that the monthly cap is updated in the right sequence)."* Multi-threaded or parallel batch processing would introduce race conditions and non-deterministic ledger balances.
- **Alternatives Considered:** Multi-threaded parallel processing: Rejected because transaction interleaving relies on chronological file sequence.
- **Impact:** Deterministic output reproducibility across all test executions.

---

### DEC-06: Evaluation of Processing Fee Waiver Post-Bundle Discount (TRAP-3 Resolution)
- **Date:** 2026-11-13
- **Decision:** The Rs 25,000.00 fee waiver threshold (`PROCESSING_FEE_WAIVED_FROM`) will be evaluated strictly on the **Net Bill Amount** ($\text{Gross Amount} - \text{Bundle Discount}$), not on the Gross Amount.
- **Reason:** Confirmed by planted scenario TRAP-3 in evaluator README: *"TV 20000 + SOUNDBAR 7000 − 2000 bundle = exactly 25000.00 → fee waiver decided after bundle discount → bills.csv line 67 (BILL-0066)"*.
- **Alternatives Considered:** Checking waiver on Gross Amount: Rejected as it violates TRAP-3 and misrepresents actual transaction value.
- **Impact:** Guarantees correct fee calculation on bundle purchases.

---

### DEC-07: Application of 50% Accessory Discount to Each Case Line Item (TRAP-7 Resolution)
- **Date:** 2026-11-13
- **Decision:** When a bill contains a `PHONE` and multiple `CASE` line items (e.g., `BILL-0119`), apply the 50% bundle discount to **each** case line item.
- **Reason:** Directly mandated by evaluator README for TRAP-7: *"PHONE with two CASE lines — brief says 50% off 'the case'; apply to each CASE line and document it → bills.csv line 120 (BILL-0119)"*.
- **Alternatives Considered:** Applying 50% discount to only the cheapest case or only the first case: Rejected because teacher test specification explicitly commands applying it to each case line.
- **Impact:** `BILL-0119` receives $500.00 + 400.00 = \text{Rs } 900.00$ discount, resulting in exact net bill amount of Rs 30,900.00.

---

### DEC-08: Deterministic Tie-Breaker Precedence Favoring Instant Cashback (EC-4 Resolution)
- **Date:** 2026-11-13
- **Decision:** When both No-Cost EMI and Instant Cashback are eligible and yield the identical monetary benefit to the customer, the engine will break the tie deterministically in favor of `CASHBACK`.
- **Reason:** Directly verified by planted scenario EC-4 in evaluator README: *"Interest saved exactly equal to cashback — 20000.00 on KOTAK tenure 6: interest saved 1500.00 = capped cashback 1500.00 → tie goes to CASHBACK → bills.csv line 17 (BILL-0016)"*. From a consumer utility standpoint, upfront immediate price reduction is superior to deferred loan subvention.
- **Alternatives Considered:** Favoring EMI or marking as ambiguous: Rejected because evaluator rule specifies CASHBACK.
- **Impact:** Predictable, deterministic resolution on equal-benefit transactions.

---

### DEC-09: Ceiling Rounding on Base Instalments with Remainder Absorption in Last Month (EC-3 Resolution)
- **Date:** 2026-11-13
- **Decision:** Monthly instalments will be computed as $\text{base} = \lceil \text{Bill Amount} / \text{Tenure} \rceil$ using ceiling rounding (`RoundingMode.CEILING`). Months 1 through $(N - 1)$ will be assigned the base instalment, and Month $N$ will absorb the difference: $\text{last} = \text{Bill Amount} - (\text{base} \times (N - 1))$.
- **Reason:** Confirmed by evaluator planted scenario EC-3: *"Bill amount not divisible by tenure (last instalment adjustment) — 10000.00 on SBI (EMI-only card) tenure 3 -> 3334, 3334, 3332 -> bills.csv line 53 (BILL-0052)"*.
- **Alternatives Considered:** Truncating or half-up rounding: Rejected as it produces fractional discrepancies or fails to match the 3334, 3334, 3332 sequence.
- **Impact:** Sum of all instalments matches principal bill amount to the exact rupee.

---

### DEC-10: Immediate Restoration of Monthly Cashback Cap upon Valid Return Reversal
- **Date:** 2026-11-13
- **Decision:** When a valid return reverses cashback granted on a purchase, the customer's available monthly limit will be restored immediately: $\text{net\_liability} = \text{granted} - \text{reversed}$, and $\text{cap\_remaining} = 1500.00 - \text{net\_liability}$.
- **Reason:** Real-world merchant accounting and the teacher's ledger schema (`month, customer_id, card_issuer, cashback_granted, cashback_reversed, net_liability, cap_remaining`) enforce financial conservation. If cashback is reclaimed, the merchant liability decreases and consumer entitlement is restored.
- **Alternatives Considered:** Sunk cap (not restoring cap after return): Rejected because it contradicts the formula $\text{cap\_remaining} = \text{cap} - \text{net\_liability}$.
- **Impact:** Maintains bidirectional financial balance across returns.

---

### DEC-11: EMI Processing Fee Treated as Downpayment/First Instalment and Kept Separate from `benefit_amount`
- **Date:** 2026-11-13
- **Decision:** In `offer_summary.csv`, `benefit_amount` for EMI will strictly equal `interest_saved`. The processing fee will be recorded in its dedicated `processing_fee` column, and in `emi_schedule.csv`, the fee will be added to the first instalment amount.
- **Reason:** Mandated by evaluator README: *"benefit_amount = interest_saved for EMI, cashback_amount for CASHBACK, 0.00 for NONE. emi_schedule.csv: bill_id, instalment_no, amount (first instalment includes the processing fee when applicable)"*.
- **Alternatives Considered:** Subtracting fee from benefit amount: Rejected because evaluator schema explicitly segregates `processing_fee` and `benefit_amount`.
- **Impact:** Exact field-for-field compatibility with the evaluator's automated test suite.

---

### DEC-12: Exact Inclusive Relational Operator ($\ge$) on Minimum Bill and Waiver Thresholds (EC-1 Resolution)
- **Date:** 2026-11-13
- **Decision:** Boundary condition checks for minimum EMI eligibility ($\ge \text{Rs } 5,000.00$) and processing fee waiver ($\ge \text{Rs } 25,000.00$) will use inclusive relational comparison (`compareTo(threshold) >= 0`).
- **Reason:** Confirmed by planted scenario EC-1: `BILL-0026` of exactly Rs 5,000.00 qualifies for EMI, and `BILL-0039` of exactly Rs 25,000.00 receives a 0.00 fee waiver.
- **Alternatives Considered:** Strict inequality (`>`): Rejected because it fails on exact boundary amounts.
- **Impact:** Passes both EC-1 and boundary traps TRAP-1 (Rs 4,999.00) and TRAP-2 (Rs 24,999.00).

---

### DEC-13: Non-Crashing Graceful Invalidation on Unsupported Tenures (TRAP-5 Resolution)
- **Date:** 2026-11-13
- **Decision:** If a bill specifies an unsupported tenure (e.g., `tenure_months = 12` in `BILL-0096`), the engine will reject EMI eligibility (`emi_available = false`) and continue command processing without throwing unhandled exceptions.
- **Reason:** Planted adversarial trap TRAP-5 in evaluator dataset: *"tenure 12 is not an allowed tenure -> EMI not available -> bills.csv line 97 (BILL-0096)"*.
- **Alternatives Considered:** Crashing on unparseable tenure: Rejected as bad architectural design.
- **Impact:** High system resilience and robustness against adversarial inputs.

---

### DEC-14: Automatic Fallback to Cashback on Cashback-Only Cards with Requested Tenure (TRAP-6 Resolution)
- **Date:** 2026-11-13
- **Decision:** When a customer requests an EMI tenure on a card that is only eligible for cashback (e.g., `AXIS` in `BILL-0099`), the engine will set `emi_available = false`, proceed to evaluate Cashback, and recommend Cashback if eligible.
- **Reason:** Confirmed by planted trap TRAP-6: *"tenure chosen on a cashback-only card (AXIS) -> no EMI, cashback evaluated -> bills.csv line 100 (BILL-0099)"*.
- **Alternatives Considered:** Rejecting the bill as invalid: Rejected because POS systems must offer available promotions even if the customer's first preference cannot be financed.
- **Impact:** Prevents lost promotional opportunities for customers while upholding card partner agreements.

