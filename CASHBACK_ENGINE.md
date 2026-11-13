# ECE-035: Instant Cashback Engine Specification & Verification Report

**Project Title:** Electronics Retail No-Cost EMI and Cashback Offer Engine  
**Component:** Instant Cashback & Customer Ledger Subsystem  
**Target Platform:** Java 17+ LTS | Apache Maven | Pure Core Java  
**Specification Reference:** Capstone Brief (BR-11, BR-12) & Mentor `README.md` (EC2)

---

## 1. Engine Overview & Architecture

The **Instant Cashback Engine** is responsible for evaluating promotional cash rebates on credit card purchases and maintaining a persistent, chronological ledger of customer liability.

### 1.1 Core Principles
1. **10% Instant Rebate:** Standard promotional discount calculated as 10.0% of the post-bundle applicable bill amount ($P$).
2. **Rolling Monthly Cap ($\text{Rs } 1,500.00$):** Capped per unique tuple: $(\text{customer\_id}, \text{card\_issuer}, \text{calendar\_month})$.
3. **Decoupled Architecture:** Individual bill transactions (`CashbackTransaction`) are decoupled from aggregated monthly accounts (`LedgerAccount`).
4. **Idempotence & Duplicate Prevention:** The ledger tracks processed bill IDs (`grantedBillIds`), strictly preventing duplicate grants for the same bill.
5. **Reversal Restoration:** When merchandise returns occur within the statutory 7-day window, reversals claw back cashback liability and dynamically restore the customer's remaining cap.
6. **Zero Paise Drift:** Computed using `java.math.BigDecimal` with scale 2 and `RoundingMode.HALF_UP`.

```
       +--------------------------------------------------------------+
       |                         BILL Command                         |
       +--------------------------------------------------------------+
                                      |
                                      v
       +--------------------------------------------------------------+
       |                       CashbackService                        |
       |  1. Check card eligibility: card.isCashbackEligible()        |
       |  2. Calculate gross cashback: P * 0.10 (HALF_UP)             |
       |  3. Fetch account: (customer_id, card_issuer, year_month)    |
       |  4. Query remaining cap: account.getRemainingCap(1500.00)    |
       |  5. Awarded Cashback = min(Gross Cashback, Remaining Cap)    |
       +--------------------------------------------------------------+
                                      |
                       [If Recommended == CASHBACK]
                                      |
                                      v
       +--------------------------------------------------------------+
       |                  CustomerLedgerRepository                    |
       |  1. Verify billId not in grantedBillIds (no duplicates)      |
       |  2. Record CashbackTransaction (type = GRANT)                |
       |  3. Update account.cashbackGranted += awardedAmount          |
       +--------------------------------------------------------------+
                                      ^
                                      | [On 7-Day Valid Return]
       +--------------------------------------------------------------+
       |                   ReturnProcessingService                    |
       |  1. Verify within_window == true && original was CASHBACK    |
       |  2. Record CashbackTransaction (type = REVERSAL)             |
       |  3. Update account.cashbackReversed += reversedAmount        |
       |  4. Remaining Cap increases: (Cap - Granted + Reversed)      |
       +--------------------------------------------------------------+
```

---

## 2. Business Rules Register (BR-11 and BR-12)

| Rule ID | Business Rule Requirement | Implementation Method | Verified Scenarios |
| :--- | :--- | :--- | :--- |
| **BR-02** | Cashback is available only when `cashback_eligible = true` in `cards.csv`. Non-eligible cards (`AMEX`, `SBI`) receive 0.00. | `card.isCashbackEligible()` check | `BILL-0088` (AMEX: 0.00), `BILL-0096` (SBI: 0.00) |
| **BR-11** | Cashback is 10% of the post-bundle bill amount ($P$). | `netBillAmount.multiply(0.10).setScale(2, HALF_UP)` | `BILL-0001` (8599 -> 859.90), `BILL-0026` (5000 -> 500.00) |
| **BR-12** | Maximum cashback is $\text{Rs } 1,500.00$ per customer, per card issuer, per calendar month across all bills. | Cumulative tracking in `LedgerAccount` | `BILL-0012` (900), `BILL-0062` (500), `BILL-0102` (100) |
| **BR-20** | Valid merchandise return ($\le 7$ days) on cashback bill triggers clawback. | Recorded as `REVERSAL` transaction | `RET-001` (1500 reversed), `RET-004` (450 reversed) |
| **BR-21** | Recorded reversals restore customer's monthly cap for subsequent purchases in the same month. | $\text{Remaining Cap} = \text{Cap} - (\text{Granted} - \text{Reversed})$ | Verified in `testReturnRestoresCapForNextPurchase` |

---

## 3. Mathematical Formulations & Ledger Accounting

### 3.1 10% Gross Rebate Calculation
$$\text{Gross Cashback} = P \times \left(\frac{\text{cashback\_pct}}{100}\right) = P \times 0.10$$
- Evaluated on post-bundle net amount ($P = \text{gross} - \text{bundle\_discount}$).
- Rounded with `RoundingMode.HALF_UP` to scale 2.

### 3.2 Monthly Net Liability and Cap Consumption
For an account defined by `(customer_id, card_issuer, year_month)`:
$$\text{Net Liability} = \text{Cashback Granted} - \text{Cashback Reversed}$$
$$\text{Remaining Cap} = \max\left(0.00, \ \text{Monthly Cap} - \text{Net Liability}\right)$$
$$\text{Awarded Cashback} = \min\left(\text{Gross Cashback}, \ \text{Remaining Cap}\right)$$

### 3.3 Proof of Cap Invariant
$$\text{Net Liability} \le \text{Monthly Cap} = \text{Rs } 1,500.00 \quad (\forall \ \text{accounts})$$

---

## 4. Deep-Dive: Teacher's Planted Scenario EC2

In `README.md` (lines 77), the teacher planted the definitive multi-bill cap saturation scenario for customer **`CUST-003`** using **`AXIS`** card in **October 2026**:

```
+------------+------------+------------+---------------+----------------+------------------+-------------------+--------------------+
| Bill ID    | Bill Date  | Card / Cust| Net Bill (P)  | Gross Cashback | Awarded Cashback | Cumulative Grants | Remaining Cap      |
+------------+------------+------------+---------------+----------------+------------------+-------------------+--------------------+
| BILL-0012  | 2026-10-02 | AXIS / 003 | Rs 9,000.00   | Rs 900.00      | Rs 900.00        | Rs 900.00         | Rs 600.00          |
| BILL-0062  | 2026-10-09 | AXIS / 003 | Rs 5,000.00   | Rs 500.00      | Rs 500.00        | Rs 1,400.00       | Rs 100.00          |
| BILL-0102  | 2026-10-15 | AXIS / 003 | Rs 3,000.00   | Rs 300.00      | Rs 100.00 (CAP!) | Rs 1,500.00 (MAX) | Rs 0.00 (EXHAUSTED)|
+------------+------------+------------+---------------+----------------+------------------+-------------------+--------------------+
```

### Exact Output Verifications from Live System:
1. **`BILL-0012` (`offer_summary.csv` line 13):**
   ```csv
   BILL-0012,CUST-003,2026-10-02,AXIS,9000.00,0.00,9000.00,false,,0.00,0.00,0.00,0.00,true,900.00,CASHBACK,900.00
   ```
2. **`BILL-0062` (`offer_summary.csv` line 63):**
   ```csv
   BILL-0062,CUST-003,2026-10-09,AXIS,5000.00,0.00,5000.00,false,,0.00,0.00,0.00,0.00,true,500.00,CASHBACK,500.00
   ```
3. **`BILL-0102` (`offer_summary.csv` line 103):**
   ```csv
   BILL-0102,CUST-003,2026-10-15,AXIS,3000.00,0.00,3000.00,false,,0.00,0.00,0.00,0.00,true,100.00,CASHBACK,100.00
   ```
4. **`cashback_report.csv` Entry for `CUST-003`:**
   ```csv
   2026-10,CUST-003,AXIS,1500.00,0.00,1500.00,0.00
   ```
   *Total Granted: Rs 1,500.00, Reversed: Rs 0.00, Net Liability: Rs 1,500.00, Cap Remaining: Rs 0.00.*

---

## 5. Transaction History & Deterministic Event-Sourced Reconstruction

To ensure enterprise-grade auditability, `CustomerLedgerRepository` maintains:
1. **Atomic Transaction Log (`transactionHistory`):** A strictly ordered sequence of `CashbackTransaction` events recording `billId`, `customerId`, `cardIssuer`, `transactionDate`, `type` (`GRANT` / `REVERSAL`), and `amount`.
2. **Deterministic Replay Method:**
   ```java
   public static Map<LedgerKey, LedgerAccount> reconstructFromTransactions(List<CashbackTransaction> transactions)
   ```
   Given any arbitrary command stream or transaction history log, replaying the events through this method deterministically reconstructs every customer's monthly balance, net liability, and remaining cap down to 0 paise without drift.

---

## 6. Unit Test Suite

The test suite in [`CashbackEngineTest.java`](file:///C:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/src/test/java/com/retail/engine/CashbackEngineTest.java) contains **12 comprehensive unit tests**:

1. `testIneligibleCardCashback`: Non-eligible cards (AMEX, SBI) receive zero cashback.
2. `testStandardTenPercentCashback`: Exact 10% calculation on post-bundle amount.
3. `testNoPreviousUsage`: Fresh customer account has full Rs 1,500.00 cap available.
4. `testPartialUsage`: Progressive grants reduce remaining cap proportionally.
5. `testExactlyRs1500InSingleTransaction`: High-value purchase (Rs 20,000) caps at exactly Rs 1,500.00.
6. `testTeacherScenarioEC2AxisCust003`: Complete end-to-end verification of planted scenario EC2 (`BILL-0012`, `BILL-0062`, `BILL-0102`).
7. `testPreventDuplicateGrantsSameBill`: Committing the same bill twice is idempotently rejected.
8. `testMonthlyCapResetNextMonth`: October usage does not lock or impact November cap.
9. `testCapIsolationAcrossCards`: Same customer using HDFC vs ICICI has separate independent caps.
10. `testCapIsolationAcrossCustomers`: Customer A exhausts cap; Customer B on same card has fresh cap.
11. `testReturnRestoresCapForNextPurchase`: Valid merchandise return reverses cashback and increases remaining cap, enabling subsequent purchase to claim restored cap.
12. `testTransactionHistoryReconstruction`: Replaying transaction stream deterministically reconstructs all ledger accounts.

