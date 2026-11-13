# ECE-035: Output & Reporting Module Specification

**Project Title:** Electronics Retail No-Cost EMI and Cashback Offer Engine  
**Component:** Evaluation CSV Reporting & Export Subsystem  
**Target Platform:** Java 17+ LTS | Apache Maven | Pure Core Java  
**Specification Reference:** Mentor `README.md` (Sections: *Output format expected at evaluation*)

---

## 1. Executive Summary & Reporting Architecture

The **Output and Reporting Module** is the final evaluation interface of ECE-035. Because the college capstone evaluation compares generated CSV outputs **field-for-field** against hidden mentor evaluation benchmarks using automated diff scripts, strict adherence to schema column names, row ordering, data types, and numerical precision is mission-critical.

### 1.1 Invariant Architectural Constraints
1. **Zero External CSV Libraries:** Generated via pure Java Standard Library (`java.io.BufferedWriter`, `java.nio.file.Files`, `java.nio.charset.StandardCharsets.UTF_8`).
2. **Zero Injected Headers / Extra Columns:** Exactly matches the mentor's column count and naming.
3. **Zero Currency Symbols:** Emits raw numeric decimal values (e.g., `1500.00`, never `Rs 1500`, `$1500`, or `1500.0`).
4. **Scale-2 Monetary Precision:** All money values are formatted via `BigDecimal.toPlainString()` with explicit scale 2.
5. **Standardized ISO Formats:** Dates formatted as `YYYY-MM-DD`, report months as `YYYY-MM`, booleans as lowercase `true` / `false`.
6. **Command-Ordered Rows:** `offer_summary.csv` and `returns_log.csv` preserve the strict chronological sequence of commands in `commands.txt`.
7. **Deterministic Overwrite:** Export methods overwrite existing files cleanly without duplicate appended rows.

---

## 2. Comprehensive CSV Specifications

### 2.1 `offer_summary.csv` (17 Columns)
Emits exactly one row per `BILL` command in command order:

```csv
bill_id,customer_id,bill_date,card_issuer,gross_amount,bundle_discount,bill_amount,emi_available,tenure_months,instalment,last_instalment,processing_fee,interest_saved,cashback_eligible,cashback_amount,recommended_option,benefit_amount
```

| Column Name | Data Type | Format / Allowed Values | Semantics & Business Invariant |
| :--- | :--- | :--- | :--- |
| `bill_id` | String | `BILL-nnnn` | Identifier from `bills.csv`. |
| `customer_id` | String | `CUST-nnn` | Customer identifier. |
| `bill_date` | Date | `YYYY-MM-DD` | Date of purchase. |
| `card_issuer` | String | `HDFC`, `ICICI`, `SBI`, `AXIS`, `KOTAK`, `AMEX` | Card issuer code. |
| `gross_amount` | Decimal | `0.00` | Sum of item prices before bundle discounts. |
| `bundle_discount`| Decimal | `0.00` | Bundle savings (TV+Soundbar Rs 2k, Phone+Case 50%). |
| `bill_amount` | Decimal | `0.00` | Net applicable amount ($\text{gross} - \text{discount}$). |
| `emi_available` | Boolean | `true`, `false` | True if card is EMI eligible, tenure valid, and bill $\ge 5000$. |
| `tenure_months` | Integer/Blank| `3`, `6`, `9`, or *empty string* | Permissible tenure in months. Empty string between commas (`,,`) if unrequested or unavailable. |
| `instalment` | Decimal | `0.00` | Base monthly instalment: $\lceil P / T \rceil$. `0.00` if EMI unavailable. |
| `last_instalment`| Decimal | `0.00` | Final balancing instalment: $P - (\text{Base} \times (T - 1))$. `0.00` if EMI unavailable. |
| `processing_fee`| Decimal | `0.00`, `199.00` | Rs 199.00 if $P < 25,000.00$; Rs 0.00 if $P \ge 25,000.00$ or EMI unavailable. |
| `interest_saved`| Decimal | `0.00` | $P \times 15.0 \times T / 1200$. `0.00` if EMI unavailable. |
| `cashback_eligible`|Boolean | `true`, `false` | True if card has `cashback_eligible = true` in `cards.csv`. |
| `cashback_amount`| Decimal | `0.00` | 10% rebate awarded after applying monthly cap. |
| `recommended_option`| Enum | `EMI`, `CASHBACK`, `NONE` | Fiduciary recommendation with higher savings (tie -> CASHBACK). |
| `benefit_amount`| Decimal | `0.00` | Financial savings granted to customer (`interest_saved` for EMI, `cashback_amount` for CASHBACK, `0.00` for NONE). |

---

### 2.2 `emi_schedule.csv` (3 Columns)
Emits instalment rows **only** for bills where No-Cost EMI was recommended (`recommended_option == EMI`):

```csv
bill_id,instalment_no,amount
```

| Column Name | Data Type | Format | Semantics & Business Invariant |
| :--- | :--- | :--- | :--- |
| `bill_id` | String | `BILL-nnnn` | Identifier of bill that won EMI. |
| `instalment_no` | Integer | `1` to `T` | Month index ($1 \le i \le \text{tenure}$). |
| `amount` | Decimal | `0.00` | Repayment amount. **Instalment 1 includes Rs 199.00 processing fee when applicable.** Final instalment absorbs remainder. |

---

### 2.3 `returns_log.csv` (7 Columns)
Emits exactly one row per `RETURN` command in command order:

```csv
return_id,bill_id,line_no,return_date,days_after_bill,within_window,cashback_reversed
```

| Column Name | Data Type | Format | Semantics & Business Invariant |
| :--- | :--- | :--- | :--- |
| `return_id` | String | `RET-nnn` | Identifier from `returns.csv`. |
| `bill_id` | String | `BILL-nnnn` | Target bill identifier. |
| `line_no` | Integer | $\ge 1$ | Line item returned. |
| `return_date` | Date | `YYYY-MM-DD` | Date of return. |
| `days_after_bill`| Integer | $\ge 0$ | Calendar days elapsed: $\text{return\_date} - \text{bill\_date}$. |
| `within_window` | Boolean | `true`, `false` | True if $\text{days\_after\_bill} \le 7$. |
| `cashback_reversed`|Decimal | `0.00` | Amount clawed back (0.00 if $> 7$ days, or EMI bill, or second item returned on same bill). |

---

### 2.4 `cashback_report.csv` (7 Columns)
Emits customer accounts having cashback activity in the requested month:

```csv
month,customer_id,card_issuer,cashback_granted,cashback_reversed,net_liability,cap_remaining
```

| Column Name | Data Type | Format | Semantics & Business Invariant |
| :--- | :--- | :--- | :--- |
| `month` | Date | `YYYY-MM` | Calendar month requested via `PRINT_CASHBACK_REPORT`. |
| `customer_id` | String | `CUST-nnn` | Customer identifier. |
| `card_issuer` | String | e.g. `AXIS`, `HDFC` | Card issuer code. |
| `cashback_granted` | Decimal | `0.00` | Cumulative cashback granted to this customer/card in this month. |
| `cashback_reversed`| Decimal | `0.00` | Cumulative reversals credited to this customer/card in this month. |
| `net_liability` | Decimal | `0.00` | $\text{cashback\_granted} - \text{cashback\_reversed}$. |
| `cap_remaining` | Decimal | `0.00` | $\max(0.00, \text{Monthly Cap} - \text{net\_liability})$. |

---

## 3. Command-Time Generation & Idempotence

1. **`PRINT_CASHBACK_REPORT|YYYY-MM` Processing:**
   - Evaluated dynamically in real-time as the command is encountered in `commands.txt`.
   - The generated report captures the exact state of all customer accounts up to that command point.
2. **Deterministic Rerun Overwrite:**
   - Both `ReportExportService` and `EngineBootstrap` manage in-memory collections and use `Files.newBufferedWriter(..., UTF_8)`, ensuring that rerunning the application on the same commands stream replaces file contents cleanly without duplicate appending.

---

## 4. Verification Against Live Mentor Dataset

All 4 output files generated from the official dataset match the expected schemas:

```
+---------------------+-------------+------------------+-------------------------------------------------------------+
| Output Report       | Total Rows  | Column Count     | Validation Status & Integrity Check                         |
+---------------------+-------------+------------------+-------------------------------------------------------------+
| offer_summary.csv   | 201 rows    | 17 columns       | 1 header + exactly 200 bill evaluations in command order.   |
| emi_schedule.csv    | 340 rows    | 3 columns        | 1 header + 339 monthly instalments for bills that won EMI.  |
| returns_log.csv     | 16 rows     | 7 columns        | 1 header + exactly 15 return evaluations in command order.  |
| cashback_report.csv | 86 rows     | 7 columns        | 1 header + 85 customer account liability records.           |
+---------------------+-------------+------------------+-------------------------------------------------------------+
```

---

## 5. Unit & Formatting Test Suite

The test suite in [`ReportFormattingTest.java`](file:///C:/Users/Thriveni%20S%20A/OneDrive/Desktop/Electronic/src/test/java/com/retail/engine/ReportFormattingTest.java) contains **6 targeted unit tests**:

1. `testOfferSummaryHeaderAndFormatting`: Verifies exact 17 headers, column order, and zero currency symbols.
2. `testOfferSummaryBlankTenureFormatting`: Verifies unrequested tenure formats as empty string between commas (`,,`).
3. `testEmiScheduleHeaderAndFormatting`: Verifies exact 3 headers and Month 1 fee inclusion.
4. `testReturnsLogHeaderAndFormatting`: Verifies exact 7 headers and lowercase boolean strings.
5. `testCashbackReportHeaderAndFormatting`: Verifies exact 7 headers, `YYYY-MM` format, and net liability calculations.
6. `testDeterministicOverwriteNoAppendedDuplicates`: Verifies file overwriting without duplicate row appending.

