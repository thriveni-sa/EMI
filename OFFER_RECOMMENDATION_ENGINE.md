# ECE-035: Offer Recommendation & Arbitration Engine Specification

**Project Title:** Electronics Retail No-Cost EMI and Cashback Offer Engine  
**Component:** Fiduciary Offer Recommendation & Benefit Arbitration Subsystem  
**Target Platform:** Java 17+ LTS | Apache Maven | Pure Core Java  
**Specification Reference:** Capstone Brief (BR-16, BR-17, BR-18) & Mentor `README.md` (EC4, TRAP4, TRAP6)

---

## 1. Engine Overview & Fiduciary Architecture

The **Offer Recommendation & Arbitration Engine** is the central decision-making subsystem of ECE-035. Acting in a fiduciary capacity on behalf of the customer, the engine arbitrates between **Merchant-Subvented No-Cost EMI** and **Instant Bank Cashback** to guarantee the maximum possible financial savings.

### 1.1 Core Principles
1. **Mutual Exclusivity (BR-16):** A bill can receive either No-Cost EMI or Cashback, **never both**.
2. **Value Maximization (BR-17):** The engine recommends the option providing the higher actual rupee savings to the consumer.
3. **Deterministic Tie-Breaker (BR-18):** When both promotions provide the exact same rupee benefit, **`CASHBACK`** is awarded.
4. **Precedence Invariant:** Bundle discounts (TV+Soundbar, Phone+Case) are evaluated and deducted from the gross purchase amount *before* calculating EMI interest subvention or bank cashback.
5. **Processing Fee Separation:** The Rs 199.00 upfront processing fee is kept strictly separate. It is **not** treated as interest saved, nor is it subtracted from `interest_saved` during promotional comparison.
6. **State Commitment Isolation:** Only the recommended offer is committed to downstream ledgers. If EMI is recommended, no cashback is deducted from the customer's monthly cap. If Cashback is recommended, no EMI repayment schedule is exported.

```
                               +-------------------------------------+
                               |            Gross Amount             |
                               +-------------------------------------+
                                                  |
                                                  v
                               +-------------------------------------+
                               |     Bundle Discount Deduction       |
                               +-------------------------------------+
                                                  |
                                                  v
                               +-------------------------------------+
                               |   Net Applicable Bill Amount (P)    |
                               +-------------------------------------+
                                                  |
                         +------------------------+------------------------+
                         |                                                 |
                         v                                                 v
        +---------------------------------+               +---------------------------------+
        |      EMI Engine Evaluation      |               |    Cashback Engine Evaluation   |
        | - Check emi_eligible card flag  |               | - Check cashback_eligible flag  |
        | - Check valid tenure in {3,6,9} |               | - Calculate gross 10% rebate    |
        | - Check net bill >= Rs 5,000.00 |               | - Fetch customer monthly ledger |
        | - Calculate Interest Saved (Is) |               | - Check remaining monthly cap   |
        | - Compute Processing Fee (Fee)  |               | - Awarded Cashback (Cb)         |
        +---------------------------------+               +---------------------------------+
                         |                                                 |
                         +------------------------+------------------------+
                                                  |
                                                  v
                               +-------------------------------------+
                               |       OfferArbitrationEngine        |
                               +-------------------------------------+
                                                  |
       +------------------------------------------+------------------------------------------+
       |                                          |                                          |
       v                                          v                                          v
[Both EMI & CB Qualify]                 [Single Offer Qualifies]                    [Neither Qualifies]
       |                                          |                                          |
       +- If Is > Cb  --> Recommend EMI           +- If only EMI  --> Recommend EMI          +- Recommend NONE
       +- If Cb > Is  --> Recommend CASHBACK      +- If only CB   --> Recommend CASHBACK     +- Benefit = 0.00
       +- If Is == Cb --> Recommend CASHBACK      |                                          +- Rationale: No offers
          (Tie Breaker per BR-18)                 |                                             qualify
       |                                          |
       +------------------------------------------+
                               |
                               v
               +-------------------------------+
               |       OfferResult Record      |
               | - recommended_option          |
               | - benefit_amount              |
               | - transparent rationale       |
               +-------------------------------+
```

---

## 2. Business Rules Register (BR-16 through BR-18)

| Rule ID | Business Rule Requirement | Implementation Method | Verified Scenarios |
| :--- | :--- | :--- | :--- |
| **BR-16** | A bill can receive either No-Cost EMI or Cashback, never both. | Mutual exclusivity enforced in `OfferArbitrationEngine`. | All 200 bills in `offer_summary.csv` |
| **BR-17** | Recommend the option with the higher actual rupee benefit. | `emi.interestSaved().compareTo(cb.awardedCashback())` | `BILL-0039` (EMI wins), `BILL-0026` (CB wins) |
| **BR-18** | If financial benefits are exactly equal, `CASHBACK` wins. | Invariant: `comp == 0 -> recommendedOption = CASHBACK` | `BILL-0016` (1500.00 vs 1500.00 -> CASHBACK) |
| **CON-01** | Processing fee is kept separate; not subtracted from comparison benefit. | EMI comparison benefit is strictly `interest_saved`. | Verified in `testProcessingFeeKeptSeparate` |
| **CON-02** | Only the recommended card offer is granted and recorded. | If EMI wins, no cashback cap is consumed; if CB wins, schedule is omitted. | Verified across `FullEngineEvaluationTest` |

---

## 3. Mathematical Decision Logic & Tie-Breaking

### 3.1 Comparison Metrics
- **EMI Benefit Metric:**
  $$\text{Benefit}_{\text{EMI}} = \text{interest\_saved} = \frac{P \times 15.0 \times T}{1200}$$
- **Cashback Benefit Metric:**
  $$\text{Benefit}_{\text{CB}} = \text{awarded\_cashback} = \min\left(P \times 0.10, \ \text{Remaining Cap}\right)$$
- **Fiduciary Invariant (Separation of Processing Fee):**
  $$\text{Benefit}_{\text{EMI}} \neq \text{interest\_saved} - \text{processing\_fee}$$
  The processing fee is an upfront banking service charge, not a reduction in merchant interest subvention. Subtracting the fee from interest saved would improperly distort the consumer's decision matrix.

### 3.2 Decision Matrix
$$\text{Recommended Option} = \begin{cases}
\text{EMI}, & \text{if } \text{EMI qualifies} \land \left(\neg\text{CB qualifies} \lor \text{Benefit}_{\text{EMI}} > \text{Benefit}_{\text{CB}}\right) \\
\text{CASHBACK}, & \text{if } \text{CB qualifies} \land \left(\neg\text{EMI qualifies} \lor \text{Benefit}_{\text{CB}} \ge \text{Benefit}_{\text{EMI}}\right) \\
\text{NONE}, & \text{if } \neg\text{EMI qualifies} \land \neg\text{CB qualifies}
\end{cases}$$

---

## 4. Deep-Dive: Exact Tie-Breaker Scenario `BILL-0016`

In `README.md` (lines 79), the teacher planted the definitive equal-benefit tie-breaker scenario:

### Bill Analysis:
- **Bill ID:** `BILL-0016`
- **Customer ID:** `CUST-005`
- **Bill Date:** `2026-10-03`
- **Card Issuer:** `KOTAK` (`emi_eligible = true`, `cashback_eligible = true`)
- **Tenure Requested:** `6` months
- **Purchase:** TV for Rs 20,000.00 (Gross = Net = Rs 20,000.00)

### Financial Calculations:
1. **No-Cost EMI Track:**
   $$\text{Interest Saved} = \frac{20000.00 \times 15.0 \times 6}{1200} = \text{Rs } 1,500.00$$
   $$\text{Base Instalment} = \lceil 20000 / 6 \rceil = \text{Rs } 3,334.00, \quad \text{Last Instalment} = \text{Rs } 3,330.00$$
   $$\text{Processing Fee} = \text{Rs } 199.00 \ (< 25000.00)$$
2. **Instant Cashback Track:**
   $$\text{Gross Cashback} = 20000.00 \times 10\% = \text{Rs } 2,000.00$$
   $$\text{Monthly Cap Remaining} = \text{Rs } 1,500.00$$
   $$\text{Awarded Cashback} = \min(2000.00, 1500.00) = \text{Rs } 1,500.00$$
3. **Arbitration Comparison:**
   $$\text{Interest Saved (Rs 1,500.00)} == \text{Awarded Cashback (Rs 1,500.00)}$$
   $$\text{Tie-Breaker Rule (BR-18)} \implies \mathbf{CASHBACK}$$

### Live Output Row in `offer_summary.csv`:
```csv
BILL-0016,CUST-005,2026-10-03,KOTAK,20000.00,0.00,20000.00,true,6,3334.00,3330.00,199.00,1500.00,true,1500.00,CASHBACK,1500.00
```
- `emi_available`: `true`
- `interest_saved`: `1500.00`
- `cashback_amount`: `1500.00`
- `recommended_option`: **`CASHBACK`**
- `benefit_amount`: **`1500.00`**
- `rationale`: `"CASHBACK recommended: exact benefit tie (Rs 1500.00) breaks to CASHBACK per BR-18"`

---

## 5. Transparent Recommendation Breakdown

The engine provides full visibility into every promotional decision via the `RecommendationBreakdown` domain record:

```java
public record RecommendationBreakdown(
        String billId,
        BigDecimal grossAmount,
        BigDecimal bundleDiscount,
        BigDecimal netBillAmount,
        boolean emiAvailable,
        BigDecimal interestSaved,
        BigDecimal processingFee,
        boolean cashbackEligible,
        BigDecimal awardedCashback,
        OfferType recommendedOption,
        BigDecimal benefitAmount,
        String rationale
) {}
```

### Sample Transparent Rationales Emitted by Engine:
- **EMI Wins:** `"EMI recommended: interest saved (Rs 4500.00) exceeds cashback (Rs 1500.00)"`
- **Cashback Wins:** `"CASHBACK recommended: cashback (Rs 800.00) exceeds interest saved (Rs 300.00)"`
- **Exact Tie:** `"CASHBACK recommended: exact benefit tie (Rs 1500.00) breaks to CASHBACK per BR-18"`
- **Sole EMI:** `"EMI recommended: sole qualifying promotion (interest saved Rs 375.00)"`
- **Sole Cashback:** `"CASHBACK recommended: sole qualifying promotion (awarded cashback Rs 1500.00)"`
- **Fallback None:** `"NONE: neither promotional option qualifies"`

---

## 6. Unit & Integration Test Suite

The test suite in [`OfferArbitrationTest.java`](file:///C:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/src/test/java/com/retail/engine/OfferArbitrationTest.java) contains **13 comprehensive unit tests**:

1. `testEmiWinsHigherBenefit`: Interest saved (Rs 4,500.00) > Cashback (Rs 1,500.00) $\rightarrow$ recommends `EMI`.
2. `testCashbackWinsHigherBenefit`: Cashback (Rs 800.00) > Interest saved (Rs 300.00) $\rightarrow$ recommends `CASHBACK`.
3. `testExactTieBreaksToCashbackBill0016`: Exact tie between Rs 1,500.00 and Rs 1,500.00 $\rightarrow$ recommends `CASHBACK` (`BILL-0016`).
4. `testNeitherEligibleAmexBill0088`: Non-partner AMEX card recommends `NONE` with Rs 0.00 benefit.
5. `testOnlyEmiEligibleCardSbi`: SBI card (`cashback_eligible = false`, `emi_eligible = true`) recommends `EMI`.
6. `testOnlyEmiEligibleWhenCashbackCapExhausted`: When customer's monthly cashback cap is exhausted (0.00 available), EMI wins.
7. `testOnlyCashbackEligibleCardAxis`: AXIS card (`emi_eligible = false`, `cashback_eligible = true`) recommends `CASHBACK` (`BILL-0099`).
8. `testOnlyCashbackEligibleWhenNoTenureRequested`: Customer did not choose tenure $\rightarrow$ recommends `CASHBACK`.
9. `testOnlyCashbackEligibleWhenBelowMinBill`: Purchase of Rs 4,999.00 (`BILL-0030`) below Rs 5,000 threshold $\rightarrow$ recommends `CASHBACK`.
10. `testInvalidTenureRejection`: Tenure 12 (`BILL-0096`) $\rightarrow$ EMI unavailable; SBI card has no cashback $\rightarrow$ recommends `NONE`.
11. `testCappedCashbackArbitration`: Large purchase (Rs 60,000) where gross cashback is Rs 6,000.00 capped to Rs 1,500.00 vs EMI interest saved Rs 6,750.00 $\rightarrow$ EMI wins.
12. `testProcessingFeeKeptSeparateNotSubtractedFromEmiBenefit`: Confirms processing fee is NOT subtracted from `interest_saved` during comparison.
13. `testTransparentRecommendationBreakdown`: Verifies `RecommendationBreakdown` domain record and rationale string.

