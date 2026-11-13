# ECE-035: Merchandise Return & Cashback Clawback Engine Specification

**Project Title:** Electronics Retail No-Cost EMI and Cashback Offer Engine  
**Component:** Merchandise Return Processing & Cap Restoration Subsystem  
**Target Platform:** Java 17+ LTS | Apache Maven | Pure Core Java  
**Specification Reference:** Capstone Brief (BR-19, BR-20, BR-21) & Mentor `README.md` (EC5, TRAP8, TRAP9)

---

## 1. Engine Overview & Core Principles

The **Return Processing Engine** is a core transactional service of ECE-035 responsible for evaluating merchandise returns against statutory promotional clawback rules, enforcing return windows, reversing granted cashbacks, and dynamically restoring customer monthly caps.

### 1.1 Core Principles
1. **Statutory 7-Day Window (BR-19 & BR-21):** Customers may return merchandise within 7 calendar days of purchase ($\text{days\_after\_bill} \le 7$). If the original bill received instant cashback, the cashback is fully clawed back. Returns after 7 days ($\text{days\_after\_bill} > 7$) are accepted for store return, but promotional cashback is **not** reversed.
2. **Cap Usage Restoration (BR-20):** Reversing cashback reduces the merchant's net cashback liability to the bank and restores the customer's monthly cap for subsequent purchases in that month.
3. **Original Bill Month Preservation:** The monthly cap must be restored in the **original bill's** `(customer, card, month)` bucket. A return processed in a subsequent month (e.g., purchased Oct 30, returned Nov 3) must **never** move the reversal into the return date's month bucket.
4. **Idempotence & Multi-Item Safety:** Duplicate return commands are safely ignored. For bills with multiple line items, the first qualifying return absorbs the reversible cashback; subsequent item returns on that bill produce `cashback_reversed = 0.00` to prevent double clawbacks.
5. **EMI Return Isolation (TRAP-9):** Returning merchandise purchased under No-Cost EMI produces `cashback_reversed = 0.00` because the transaction received an upfront retailer interest subvention, not a bank cashback grant.

```
       +--------------------------------------------------------------+
       |                        RETURN Command                        |
       |                        (returns.csv)                         |
       +--------------------------------------------------------------+
                                      |
                                      v
       +--------------------------------------------------------------+
       |                   ReturnProcessingService                    |
       |  1. Calculate duration: return_date - bill_date              |
       |  2. within_window = (days_after_bill <= 7)                   |
       |  3. Check original bill recommendation:                      |
       |     - If EMI: cashback_reversed = 0.00                       |
       |     - If NONE: cashback_reversed = 0.00                      |
       |     - If CASHBACK && within_window == true:                  |
       |         cashback_reversed = min(availablePool, originalCB)   |
       |         availablePool[bill_id] = 0.00 (no double clawback)   |
       |     - If CASHBACK && within_window == false:                 |
       |         cashback_reversed = 0.00                             |
       +--------------------------------------------------------------+
                                      |
                   [If cashback_reversed > 0.00]
                                      |
                                      v
       +--------------------------------------------------------------+
       |                  CustomerLedgerRepository                    |
       |  1. Target Bucket = original bill's (cust, card, billMonth)  |
       |  2. Record CashbackTransaction (type = REVERSAL)             |
       |  3. account.cashbackReversed += cashback_reversed            |
       |  4. account.remainingCap += cashback_reversed                |
       +--------------------------------------------------------------+
                                      |
                                      v
       +--------------------------------------------------------------+
       |                       returns_log.csv                        |
       |  return_id, bill_id, line_no, return_date,                   |
       |  days_after_bill, within_window, cashback_reversed           |
       +--------------------------------------------------------------+
```

---

## 2. Business Rules Register (BR-19 through BR-21)

| Rule ID | Business Rule Requirement | Implementation Method | Verified Scenarios |
| :--- | :--- | :--- | :--- |
| **BR-19** | A qualifying return within 7 days reverses the full cashback previously granted on that bill. | `daysAfterBill <= 7 && originalOption == CASHBACK` | `RET-001` (1500.00), `RET-004` (450.00) |
| **BR-20** | Restore the corresponding cashback cap usage in the ledger after reversal. | Target bucket: `bill.getBillDate()` year-month | `RET-004` (cap increased by 450.00) |
| **BR-21** | A return after 7 calendar days does not qualify for cashback reversal (`cashback_reversed = 0.00`). | `daysAfterBill > 7 -> cashback_reversed = 0.00` | `RET-005` (8 days: 0.00), `RET-014` (20 days: 0.00) |

---

## 3. Elapsed Duration & Window Formulation

### 3.1 Calendar Days Calculation
$$\text{days\_after\_bill} = \text{ChronoUnit.DAYS.between}(\text{bill\_date}, \ \text{return\_date})$$
- Calculated using ISO-8601 calendar dates without time-zone or daylight saving ambiguity.
- **Statutory Window Predicate:**
  $$\text{within\_window} = (\text{days\_after\_bill} \le \text{return\_window\_days}) = (\text{days\_after\_bill} \le 7)$$

### 3.2 Clawback Determination
$$\text{cashback\_reversed} = \begin{cases} 
\text{Awarded Cashback}, & \text{if } \text{within\_window} \land \text{Recommended} = \text{CASHBACK} \land \text{Pool} > 0 \\
0.00, & \text{otherwise}
\end{cases}$$

---

## 4. Deep-Dive: Planted Return Scenarios

In `README.md` (lines 80, 88, 89), the teacher planted 4 critical edge cases and traps to evaluate return processing correctness:

```
+-----------+------------+------------+-------------+---------------+-----------------+---------------+-------------------+-------------------------------------------------------------+
| Scenario  | Return ID  | Bill ID    | Bill Date   | Return Date   | days_after_bill | within_window | cashback_reversed | Architectural & Business Defense                            |
+-----------+------------+------------+-------------+---------------+-----------------+---------------+-------------------+-------------------------------------------------------------+
| EC-5      | RET-004    | BILL-0015  | 2026-10-03  | 2026-10-10    | 7 days          | true          | Rs 450.00         | Exact 7th day boundary qualifies. Cashback is reversed.     |
| EC-5      | RET-005    | BILL-0019  | 2026-10-03  | 2026-10-11    | 8 days          | false         | Rs 0.00           | Exact 8th day boundary fails. Customer keeps cashback.      |
| TRAP-8    | RET-014    | BILL-0012  | 2026-10-02  | 2026-10-22    | 20 days         | false         | Rs 0.00           | Late return outside statutory window. No clawback.          |
| TRAP-9    | RET-003    | BILL-0052  | 2026-10-07  | 2026-10-09    | 2 days          | true          | Rs 0.00           | Return on EMI bill. No cashback was awarded -> 0 reversal.  |
+-----------+------------+------------+-------------+---------------+-----------------+---------------+-------------------+-------------------------------------------------------------+
```

### Exact Output Rows from Live Generated `returns_log.csv`:
```csv
return_id,bill_id,line_no,return_date,days_after_bill,within_window,cashback_reversed
RET-001,BILL-0017,1,2026-10-05,2,true,1500.00
RET-002,BILL-0004,1,2026-10-06,5,true,1049.90
RET-003,BILL-0052,1,2026-10-09,2,true,0.00
RET-004,BILL-0015,1,2026-10-10,7,true,450.00
RET-005,BILL-0019,1,2026-10-11,8,false,0.00
RET-006,BILL-0027,1,2026-10-12,8,false,0.00
RET-007,BILL-0078,1,2026-10-12,1,true,1500.00
RET-008,BILL-0075,1,2026-10-17,6,true,640.10
RET-009,BILL-0112,1,2026-10-17,2,true,1500.00
RET-010,BILL-0113,1,2026-10-20,5,true,279.90
RET-011,BILL-0126,1,2026-10-20,2,true,1309.90
RET-012,BILL-0116,1,2026-10-21,5,true,1369.90
RET-013,BILL-0118,1,2026-10-21,5,true,1500.00
RET-014,BILL-0012,1,2026-10-22,20,false,0.00
RET-015,BILL-0133,1,2026-10-22,3,true,1500.00
```

---

## 5. Preservation of Original Bill Month in Cross-Month Returns

A subtle trap in banking promotional reconciliation occurs when a purchase is made near month-end and returned in the following month:
- **Scenario:** Customer purchases Rs 10,000 on **2026-10-30** (earning Rs 1,000 cashback in October). The customer returns the item on **2026-11-03** (4 days later).
- **Correct Accounting:** The return occurred within 4 days ($\le 7$). The Rs 1,000 clawback pertains to the **October** ledger bucket. It must restore the customer's October cap so that October financial statements balance to 0 paise.
- **Buggy Implementation:** Using `returnTx.getReturnDate()` creates an account in November (`2026-11`) with negative liability / inflated cap, leaving October's net liability unresolved.
- **Engine Invariant:** In `ReturnProcessingService.java`, the ledger key is derived strictly from the original bill's bill date:
  ```java
  String billYearMonth = String.format("%04d-%02d", bill.getBillDate().getYear(), bill.getBillDate().getMonthValue());
  CashbackTransaction reversalTx = new CashbackTransaction(
          returnTx.getBillId(),
          bill.getCustomerId(),
          bill.getCardIssuer(),
          returnTx.getReturnDate(), // event date
          billYearMonth,            // ledger bucket = original bill's month!
          CashbackTransaction.TransactionType.REVERSAL,
          cashbackReversed
  );
  ledgerRepository.recordReversal(reversalTx);
  ```

---

## 6. Multi-Item Returns & Duplicate Return Handling

1. **Reversible Pool Tracking (`remainingReversibleByBill`):**
   - When a bill is evaluated, its reversible pool is initialized to `evaluatedBill.getCashbackAmount()`.
   - When item 1 is returned within the 7-day window, the reversible pool is absorbed (`pool -> 0.00`).
   - If item 2 of the same bill is subsequently returned, `availableToReverse` is `0.00`, emitting `cashback_reversed = 0.00`.
   - This prevents double or multiple clawbacks on the same bill.
2. **Idempotent Return Commands (`processedReturnIds`):**
   - If an identical `returnId` is processed twice, the engine detects the duplicate and returns the original recorded result without re-executing ledger updates.

---

## 7. Ambiguity Analysis & Mentor Confirmation Request

> [!NOTE]
> **Architectural Gray Area: Partial vs Full Bill Returns**
> - **Mentor Specification (BR-19):** *"A qualifying return within 7 days reverses the full cashback previously granted on that bill."*
> - **Codebase Adherence:** Our engine adheres strictly to the literal text of BR-19: the first valid return line on a cashback bill claws back the full cashback granted on that bill.
> - **Commercial Alternative (Proportional Clawback):** In some enterprise retail systems, returning 1 item of a 3-item bill claws back only proportional cashback:
>   $$\text{Clawback} = \text{Cashback}_{\text{bill}} \times \left(\frac{\text{Price}_{\text{returned}}}{\text{Gross}_{\text{bill}}}\right)$$
> - **Recommendation:** We have strictly adhered to the mentor's explicit requirement of reversing the full cashback previously granted on that bill while guaranteeing that multi-item returns never double-clawback. We have raised this formal question in [`DECISION_LOG.md`](file:///C:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/DECISION_LOG.md) for mentor clarification during the viva.

---

## 8. Unit & Integration Test Suite

The test suite in [`ReturnProcessingEngineTest.java`](file:///C:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/src/test/java/com/retail/engine/ReturnProcessingEngineTest.java) contains **8 targeted unit tests**:

1. `testReturnOnDay7ReversesCashback`: Day 7 boundary (`RET-004`) reverses cashback.
2. `testReturnOnDay8RejectsReversal`: Day 8 boundary (`RET-005`) rejects reversal.
3. `testLateReturn20Days`: Day 20 (`RET-014`) rejects reversal.
4. `testReturnOnEmiBillHasZeroClawback`: EMI return (`RET-003`) produces 0.00 reversal.
5. `testReturnOnZeroCashbackAwarded`: Bill with no cashback awards 0.00 reversal.
6. `testDuplicateReturnProcessing`: Duplicate `returnId` is idempotently handled.
7. `testMultipleReturnedItemsOnSameBill`: Returning multiple items on the same bill never double-reverses cashback.
8. `testCrossMonthReturnRestoresOriginalBillMonthBucket`: Cross-month return restores cap in the original bill's month bucket.

