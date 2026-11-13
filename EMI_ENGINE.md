# ECE-035: No-Cost EMI Engine Specification & Verification Report

**Project Title:** Electronics Retail No-Cost EMI and Cashback Offer Engine  
**Component:** No-Cost EMI Calculation & Schedule Generation Subsystem  
**Target Platform:** Java 17+ LTS | Apache Maven | Pure Core Java  
**Specification Reference:** Capstone Brief (BR-02 through BR-10) & Mentor `README.md`

---

## 1. Engine Overview & Core Principles

The **No-Cost EMI Engine** is a core domain service of ECE-035 responsible for evaluating credit card promotional financing on retail purchases. Under commercial merchant-subvented No-Cost EMI models:
1. **Zero Customer Interest:** The consumer incurs 0% effective borrowing cost. The entire statutory annual flat interest (15.0% p.a.) is absorbed upfront as an **interest subvention discount** funded entirely by the electronics retailer / merchant.
2. **Principal-Only Repayment:** The consumer repays strictly the applicable net financed principal amount ($P$) across the designated tenure ($T$), divided into equalized monthly instalments.
3. **Upfront Processing Fee:** A non-refundable processing fee of Rs 199.00 is charged on transactions where the post-bundle applicable bill amount is below Rs 25,000.00. This fee is waived completely (Rs 0.00) for purchases of Rs 25,000.00 or greater. When applicable, the fee is aggregated into the **first instalment** in `emi_schedule.csv`.
4. **Zero Paise Drift Guarantee:** All computations strictly utilize `java.math.BigDecimal` with scale 2 and explicit rounding modes.

---

## 2. Business Rules Register (BR-02 through BR-10)

| Rule ID | Business Rule Requirement | Implementation Method | Verified Scenarios |
| :--- | :--- | :--- | :--- |
| **BR-02** | EMI is available only for card issuers marked `emi_eligible = true` in `cards.csv`. Non-eligible cards (e.g., `AMEX`, `AXIS`) receive `emi_available = false`. | `EmiCalculatorService.evaluateEmi()` | `BILL-0088` (AMEX), `BILL-0099` (AXIS) |
| **BR-03** | Applicable bill amount (post-bundle discount) must be at least $\text{Rs } 5,000.00$. Amounts $< 5,000.00$ make EMI unavailable. | `netBillAmount.compareTo(config.getEmiMinBill()) >= 0` | `BILL-0026` (5000.00: PASS), `BILL-0030` (4999.00: FAIL) |
| **BR-04** | Only tenures 3, 6, and 9 months are supported. A blank tenure denotes the customer did not request EMI. Tenure 12 is invalid. | `config.isTenurePermissible(tenure)` | `BILL-0096` (tenure 12: FAIL), `BILL-0001` (blank: unrequested) |
| **BR-05** | Annual flat interest rate is 15.0%. Calculated as $\text{Interest Saved} = P \times 15 \times T / 1200$. | `P.multiply(rate).multiply(T).divide(1200, 2, HALF_UP)` | `BILL-0016` (1500.00), `BILL-0039` (1875.00) |
| **BR-06** | Interest is an upfront discount funded by retailer; customer repays only the net principal through monthly instalments. | Upfront subvention deduction; principal repayment schedule | `BILL-0052` (10000 repaid over 3 months) |
| **BR-07** | Regular monthly instalment uses ceiling division: $\lceil P / T \rceil$. | `P.divide(T, 0, RoundingMode.CEILING).setScale(2)` | `BILL-0052` (10000/3 = 3334.00) |
| **BR-08** | Final instalment absorbs remainder: $\text{Last} = P - (\text{Base} \times (T - 1))$. Total principal repaid equals $P$ exactly. | `P.subtract(base.multiply(T - 1))` | `BILL-0052` (10000 - 6668 = 3332.00) |
| **BR-09** | If post-bundle applicable bill amount is $< \text{Rs } 25,000.00$, Rs 199.00 processing fee is charged and added to Instalment 1. | `config.getProcessingFee()` when $P < 25000.00$ | `BILL-0053` (24999.00 -> 199.00 fee) |
| **BR-10** | If post-bundle applicable bill amount is $\ge \text{Rs } 25,000.00$, processing fee is waived to $\text{Rs } 0.00$. | Fee $= 0.00$ when $P \ge 25000.00$ | `BILL-0039` (25000.00 -> 0.00), `BILL-0066` (25000.00 -> 0.00) |

---

## 3. Mathematical Formulations & Exact Rounding

### 3.1 Interest Subvention (Retailer Subsidized Benefit)
$$\text{Interest Saved} = \frac{P \times \text{Rate} \times T}{1200}$$
- **Variables:**
  - $P$: Post-bundle net bill amount (`bill_amount`)
  - $\text{Rate}$: Annual interest rate percentage ($15.0\%$)
  - $T$: Permissible tenure in months ($3, 6, 9$)
  - $1200$: Normalizing constant ($100 \times 12 \text{ months}$)
- **Rounding:** `RoundingMode.HALF_UP` with final scale 2.
- **Example (`BILL-0053`, $P = \text{Rs } 24,999.00, T = 9$):**
  $$\text{Interest Saved} = \frac{24999.00 \times 15.0 \times 9}{1200} = \frac{3374865}{1200} = 2812.3875 \xrightarrow{\text{HALF\_UP}} \text{Rs } 2,812.39$$

### 3.2 Non-Divisible Instalment Ceiling & Remainder Absorption
When principal $P$ is not evenly divisible by tenure $T$, integer division produces repeating fractions. To prevent financial drift or missing paise:
1. **Base Regular Instalment (Months 1 through $T - 1$):**
   $$\text{Base Instalment} = \lceil P / T \rceil = \text{P.divide}(T, 0, \text{RoundingMode.CEILING}).\text{setScale}(2)$$
2. **Final Balancing Instalment (Month $T$):**
   $$\text{Last Instalment} = P - \left(\text{Base Instalment} \times (T - 1)\right)$$
3. **Mathematical Proof of Exact Repayment:**
   $$\sum_{i=1}^{T} \text{Instalment}_i = (\text{Base} \times (T - 1)) + \left[P - (\text{Base} \times (T - 1))\right] = P$$
- **Example (`BILL-0052`, $P = \text{Rs } 10,000.00, T = 3$):**
  - $\text{Base Instalment} = \lceil 10000.00 / 3 \rceil = 3334.00$
  - $\text{Months 1 and 2} = 3334.00 \times 2 = 6668.00$
  - $\text{Month 3 (Last)} = 10000.00 - 6668.00 = 3332.00$
  - $\text{Sum} = 3334.00 + 3334.00 + 3332.00 = 10,000.00$ (exact to 0 paise).

### 3.3 Repayment Schedule Generation with Fee Upfront
Per BR-09 and README.md line 103, when processing fee applies (Rs 199.00), it is added to **Instalment 1**:
$$\text{Schedule Month 1} = \text{Base Instalment} + \text{Processing Fee}$$
$$\text{Schedule Month } i \ (1 < i < T) = \text{Base Instalment}$$
$$\text{Schedule Month } T = \text{Last Instalment}$$
- **Example (`BILL-0052` in `emi_schedule.csv`):**
  - Month 1: $3334.00 + 199.00 = \text{Rs } 3,533.00$
  - Month 2: $\text{Rs } 3,334.00$
  - Month 3: $\text{Rs } 3,332.00$
  - $\text{Total Schedule Outflow} = 3533.00 + 3334.00 + 3332.00 = \text{Rs } 10,199.00$ ($P + \text{Fee}$).

---

## 4. Handling Unrequested Tenure vs Ineligible Scenarios

A critical architectural requirement is to **never invent output formats** and to adhere strictly to the mentor's evaluation schema (`README.md` lines 97–100):

| Scenario | Input Condition | `emi_available` | `tenure_months` | `instalment` | `last_instalment` | `processing_fee` | `interest_saved` |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Unrequested Tenure** | `tenure_months` is blank in `bills.csv` (e.g., `BILL-0001`) | `false` | *empty string* (`,,`) | `0.00` | `0.00` | `0.00` | `0.00` |
| **Invalid Tenure** | `tenure_months` = 12 (e.g., `BILL-0096`) | `false` | *empty string* (`,,`) | `0.00` | `0.00` | `0.00` | `0.00` |
| **Below Minimum** | Post-bundle amount $< \text{Rs } 5,000.00$ (e.g., `BILL-0030`) | `false` | *empty string* (`,,`) | `0.00` | `0.00` | `0.00` | `0.00` |
| **Ineligible Card** | `emi_eligible = false` (e.g., `AXIS` or `AMEX`) | `false` | *empty string* (`,,`) | `0.00` | `0.00` | `0.00` | `0.00` |
| **Qualifying EMI** | Eligible card, $P \ge 5000.00$, tenure $\in \{3,6,9\}$ | `true` | `3`, `6`, or `9` | $> 0.00$ | $> 0.00$ | `0.00` or `199.00` | $> 0.00$ |

> [!IMPORTANT]
> **Formatting Invariant in `offer_summary.csv`:**
> When `emi_available = false`, the `tenure_months` column emits an empty string between two commas (e.g., `...,false,,0.00,0.00,0.00,0.00,...`). The numerical currency columns (`instalment`, `last_instalment`, `processing_fee`, `interest_saved`) emit `0.00` (scale 2).

---

## 5. Architectural Distinction: EMI Availability vs EMI Recommendation

A frequent trap in capstone evaluations is conflating **EMI availability/eligibility** with **EMI recommendation**:

- **`emi_available` (Boolean):** Indicates whether the bill qualifies mechanically for No-Cost EMI (card is EMI-eligible, customer requested a permissible tenure, and post-bundle bill amount meets $\ge \text{Rs } 5,000.00$).
- **`recommended_option` (Enum: `EMI`, `CASHBACK`, `NONE`):** Indicates the fiduciary choice made by the `OfferArbitrationEngine` after comparing financial savings:
  $$\text{Compare}(\text{Interest Saved}, \text{Awarded Cashback})$$

### The Definitive Verification Case: `BILL-0026`
- **Bill Data:** Customer `CUST-001`, Date `2026-10-04`, Card `HDFC`, Requested Tenure `3`, Bill Amount `Rs 5,000.00`.
- **EMI Evaluation:**
  - Card `HDFC` has `emi_eligible = true`.
  - Tenure `3` is permissible.
  - Amount $\text{Rs } 5,000.00 \ge \text{Rs } 5,000.00$ (passes minimum threshold).
  - Processing fee: Rs 199.00 (since $5000 < 25000$).
  - Interest Saved: $5000 \times 15 \times 3 / 1200 = \text{Rs } 187.50$.
  - **Verdict:** `emi_available = true`!
- **Cashback Evaluation:**
  - Card `HDFC` has `cashback_eligible = true`.
  - Cashback: $10\% \text{ of } 5000.00 = \text{Rs } 500.00$.
- **Fiduciary Arbitration:**
  - $\text{Cashback (Rs 500.00)} > \text{Interest Saved (Rs 187.50)}$.
  - **Verdict:** `recommended_option = CASHBACK`, `benefit_amount = 500.00`.
- **Live Output Row in `offer_summary.csv`:**
  ```csv
  BILL-0026,CUST-001,2026-10-04,HDFC,5000.00,0.00,5000.00,true,3,1667.00,1666.00,199.00,187.50,true,500.00,CASHBACK,500.00
  ```
  Notice that `emi_available` is **`true`**, and all EMI calculations (`1667.00`, `1666.00`, `199.00`, `187.50`) are fully present in the summary, even though `recommended_option` is **`CASHBACK`**! Furthermore, because EMI was not recommended, `BILL-0026` does **not** appear in `emi_schedule.csv`.

---

## 6. Verification of the 6 Mandated Planted Scenarios

```
+-----------+------------+------------------+------------------+---------------+----------------+-------------------------------------------------------------+
| Scenario  | Bill ID    | Applicable Bill  | Card / Tenure    | emi_available | Processing Fee | Result & Verification Defense                               |
+-----------+------------+------------------+------------------+---------------+----------------+-------------------------------------------------------------+
| EC-1      | BILL-0026  | Rs 5,000.00      | HDFC / 3 months  | true          | Rs 199.00      | Exact Rs 5k minimum boundary qualifies. Fee charged.        |
| EC-1      | BILL-0039  | Rs 25,000.00     | ICICI / 6 months | true          | Rs 0.00        | Exact Rs 25k boundary waives fee to 0.00. Recommended EMI.  |
| EC-3      | BILL-0052  | Rs 10,000.00     | SBI / 3 months   | true          | Rs 199.00      | Non-divisible: 3334, 3334, 3332. Schedule Month 1: 3533.00. |
| TRAP-1    | BILL-0030  | Rs 4,999.00      | HDFC / 3 months  | false         | Rs 0.00        | Rs 1.00 below threshold -> EMI unavailable. Awards cashback.|
| TRAP-2    | BILL-0053  | Rs 24,999.00     | ICICI / 9 months | true          | Rs 199.00      | Rs 1.00 below waiver -> Rs 199.00 fee charged.              |
| TRAP-3    | BILL-0066  | Rs 25,000.00*    | HDFC / 6 months  | true          | Rs 0.00        | Gross 27k - 2k bundle = 25k net. Fee WAIVED post-discount.  |
+-----------+------------+------------------+------------------+---------------+----------------+-------------------------------------------------------------+
* BILL-0066 has Gross Amount = Rs 27,000.00, Bundle Discount = Rs 2,000.00, Net Applicable Amount = Rs 25,000.00.
```

### Detailed Row Extractions from Live Output Files:

1. **`BILL-0026` (`offer_summary.csv` line 27):**
   ```csv
   BILL-0026,CUST-001,2026-10-04,HDFC,5000.00,0.00,5000.00,true,3,1667.00,1666.00,199.00,187.50,true,500.00,CASHBACK,500.00
   ```
2. **`BILL-0039` (`offer_summary.csv` line 40):**
   ```csv
   BILL-0039,CUST-002,2026-10-06,ICICI,25000.00,0.00,25000.00,true,6,4167.00,4165.00,0.00,1875.00,true,1500.00,EMI,1875.00
   ```
3. **`BILL-0052` (`offer_summary.csv` line 53 & `emi_schedule.csv` lines 77–79):**
   ```csv
   # offer_summary.csv:
   BILL-0052,CUST-004,2026-10-07,SBI,10000.00,0.00,10000.00,true,3,3334.00,3332.00,199.00,375.00,false,0.00,EMI,375.00

   # emi_schedule.csv:
   BILL-0052,1,3533.00
   BILL-0052,2,3334.00
   BILL-0052,3,3332.00
   ```
4. **`BILL-0030` (`offer_summary.csv` line 31):**
   ```csv
   BILL-0030,CUST-008,2026-10-05,HDFC,4999.00,0.00,4999.00,false,,0.00,0.00,0.00,0.00,true,499.90,CASHBACK,499.90
   ```
5. **`BILL-0053` (`offer_summary.csv` line 54 & `emi_schedule.csv` lines 80–88):**
   ```csv
   # offer_summary.csv:
   BILL-0053,CUST-009,2026-10-08,ICICI,24999.00,0.00,24999.00,true,9,2778.00,2775.00,199.00,2812.39,true,1500.00,EMI,2812.39

   # emi_schedule.csv (Instalment 1 includes fee: 2778.00 + 199.00 = 2977.00):
   BILL-0053,1,2977.00
   BILL-0053,2,2778.00
   BILL-0053,3,2778.00
   BILL-0053,4,2778.00
   BILL-0053,5,2778.00
   BILL-0053,6,2778.00
   BILL-0053,7,2778.00
   BILL-0053,8,2778.00
   BILL-0053,9,2775.00
   ```
6. **`BILL-0066` (`offer_summary.csv` line 67 & `emi_schedule.csv` lines 122–127):**
   ```csv
   # offer_summary.csv:
   BILL-0066,CUST-010,2026-10-10,HDFC,27000.00,2000.00,25000.00,true,6,4167.00,4165.00,0.00,1875.00,true,1500.00,EMI,1875.00

   # emi_schedule.csv (Fee is waived: Instalment 1 has no fee added):
   BILL-0066,1,4167.00
   BILL-0066,2,4167.00
   BILL-0066,3,4167.00
   BILL-0066,4,4167.00
   BILL-0066,5,4167.00
   BILL-0066,6,4165.00
   ```

---

## 7. Unit & Integration Test Suite

The test suite in [`EmiEngineTest.java`](file:///C:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/src/test/java/com/retail/engine/EmiEngineTest.java) contains 14 targeted unit tests covering every single edge condition:

1. `testCardNotEligibleForEmi`: Non-partner (AMEX), cashback-only (AXIS), and unknown cards return `available = false`.
2. `testNoTenureRequested`: Blank tenure produces `available = false`, `tenureMonths = null`, and `0.00` amounts.
3. `testInvalidTenure12Months`: Planted trap `BILL-0096` with tenure 12 is rejected.
4. `testPermissibleAndImpermissibleTenures`: Tenures 3, 6, 9 accepted; tenures 1, 2, 4, 5, 7, 8, 10, 11, 24, 36 rejected.
5. `testBelowMinimumThreshold`: Amount Rs 4,999.00 (`BILL-0030`) rejected with `available = false`.
6. `testExactMinimumThreshold`: Exact Rs 5,000.00 boundary (`BILL-0026`) qualifies for EMI.
7. `testProcessingFeeBelow25000`: Amount Rs 24,999.00 (`BILL-0053`) incurs Rs 199.00 fee.
8. `testProcessingFeeWaiverAt25000`: Exact Rs 25,000.00 boundary (`BILL-0039`) waives fee to Rs 0.00.
9. `testInterestSubventionFormulaAcrossTenures`: Formulations $P \times 15 \times T / 1200$ verified across tenures 3, 6, and 9.
10. `testNonDivisibleEmiSchedule3Months`: Rs 10,000 / 3 months (`BILL-0052`) yields 3334, 3334, 3332, fee on Month 1.
11. `testNonDivisibleEmiSchedule6Months`: Rs 50,000 / 6 months yields 8334 (x5) + 8330, fee waived.
12. `testNonDivisibleEmiSchedule9Months`: Rs 24,999 / 9 months (`BILL-0053`) yields 2778 (x8) + 2775, fee on Month 1.
13. `testProcessingFeeEvaluatedPostBundleDiscount`: Post-bundle fee waiver (`BILL-0066`) verified end-to-end.
14. `testEmiAvailabilityNotConfusedWithRecommendation`: Availability preserved as `true` even when Cashback wins recommendation (`BILL-0026`).

