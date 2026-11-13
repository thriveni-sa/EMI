# ECE-035 – Electronics Retail No-Cost EMI and Cashback Offer Engine — Sample Data Pack

Prepared by 10 SECONDS for Team E005.

## Files

| File | What it holds | Row count |
|---|---|---|
| `cards.csv` | Card issuers and which offers they are eligible for | 6 |
| `offer_config.csv` | Offer parameters (one per row) | 10 |
| `bills.csv` | Bill headers for October 2026 | 200 |
| `bill_items.csv` | Line items of every bill | 237 |
| `returns.csv` | Item returns (reverse cashback if within 7 days) | 15 |
| `commands.txt` | Commands to process, in file order (comment lines start with #) | 218 commands |

## Column reference

Conventions: dates `YYYY-MM-DD`; timestamps `YYYY-MM-DD HH:MM:SS` (24h, IST, no timezone suffix); money in Rs as plain decimals (no commas, no symbol); percentages as plain numbers (12.5 = 12.5%); booleans `true`/`false`; IDs uppercase with a fixed prefix.

### cards.csv
| column | type | units / format | allowed values | notes |
|---|---|---|---|---|
| issuer_code | string | | HDFC, ICICI, SBI, AXIS, KOTAK, AMEX | primary key |
| issuer_name | string | | | |
| emi_eligible | boolean | | true/false | card is in the no-cost EMI list |
| cashback_eligible | boolean | | true/false | card is in the cashback list |

### offer_config.csv
| column | type | units / format | allowed values | notes |
|---|---|---|---|---|
| parameter | string | | see file | EMI_TENURES is `;`-separated; PCT values are plain percentages |
| value | string | | | parse by parameter |

### bills.csv
| column | type | units / format | allowed values | notes |
|---|---|---|---|---|
| bill_id | string | `BILL-nnnn` | unique | |
| customer_id | string | `CUST-nnn` | | cashback cap is per customer per card per calendar month |
| bill_date | date | YYYY-MM-DD | Oct 2026 | |
| card_issuer | string | | issuer_code from cards.csv | |
| tenure_months | integer | months | blank, 3, 6, 9 (one invalid 12 planted) | blank = customer did not ask for EMI |

### bill_items.csv
| column | type | units / format | allowed values | notes |
|---|---|---|---|---|
| bill_id | string | | from bills.csv | |
| line_no | integer | | 1.. | unique within a bill |
| category | string | | TV, SOUNDBAR, PHONE, CASE, LAPTOP, HEADPHONES, TABLET, AC, FRIDGE, SPEAKER, SMARTWATCH, MIXER | bundles use TV+SOUNDBAR and PHONE+CASE |
| item_name | string | | | |
| price | decimal | Rs | > 0 | gross price before any discount |

### returns.csv
| column | type | units / format | allowed values | notes |
|---|---|---|---|---|
| return_id | string | `RET-nnn` | | |
| bill_id | string | | from bills.csv | |
| line_no | integer | | | item line returned |
| return_date | date | YYYY-MM-DD | | within window when return_date − bill_date ≤ 7 days |

## Command file format

One command per line, `COMMAND_NAME|arg1|arg2|...`; lines starting with `#` are comments. Commands must be processed in file order (bills and returns are interleaved by date so that the monthly cap is updated in the right sequence).

| command | arguments | meaning |
|---|---|---|
| `LOAD_CONFIG` | file | load offer_config.csv |
| `LOAD_CARDS` | file | load cards.csv |
| `BILL` | bill_id | evaluate that bill (header in bills.csv, lines in bill_items.csv) and grant the recommended option |
| `RETURN` | return_id | process that return (returns.csv) |
| `PRINT_CASHBACK_REPORT` | YYYY-MM | print the monthly cashback liability report |

## Planted scenarios

Every edge case in the brief (EC1…) plus extra traps (TRAP1…). Line numbers count the header as line 1.

- **EC1** – Bill of exactly Rs 5,000 and exactly Rs 25,000 — 5000.00 on HDFC tenure 3 (EMI available, Rs 199 fee); 25000.00 on ICICI tenure 6 (fee waived) → `bills.csv` line 27 (BILL-0026); `bills.csv` line 40 (BILL-0039)
- **EC2** – Customer whose monthly cashback already stands at Rs 1,400 making a Rs 3,000 purchase — CUST-003 on AXIS: 9000 → 900, 5000 → 500 (cap used 1400), then 3000 → only Rs 100 cashback → `bills.csv` line 13 (BILL-0012); `bills.csv` line 63 (BILL-0062); `bills.csv` line 103 (BILL-0102)
- **EC3** – Bill amount not divisible by tenure (last instalment adjustment) — 10000.00 on SBI (EMI-only card) tenure 3 → 3334, 3334, 3332 → `bills.csv` line 53 (BILL-0052)
- **EC4** – Interest saved exactly equal to cashback — 20000.00 on KOTAK tenure 6: interest saved 1500.00 = capped cashback 1500.00 → tie goes to CASHBACK → `bills.csv` line 17 (BILL-0016)
- **EC5** – Return processed on the 7th day versus 8th day — both bills dated 2026-10-03; return on 2026-10-10 (7th day → reverse) and 2026-10-11 (8th day → keep) → `returns.csv` line 5 (BILL-0015); `returns.csv` line 6 (BILL-0019)
- **TRAP1** – 4999.00 on an EMI card with tenure chosen → EMI not available → `bills.csv` line 31 (BILL-0030)
- **TRAP2** – 24999.00 tenure 9 → processing fee applies → `bills.csv` line 54 (BILL-0053)
- **TRAP3** – TV 20000 + SOUNDBAR 7000 − 2000 bundle = exactly 25000.00 → fee waiver decided after bundle discount → `bills.csv` line 67 (BILL-0066)
- **TRAP4** – AMEX is in neither list → no offer (recommendation NONE) even with a tenure chosen → `bills.csv` line 89 (BILL-0088)
- **TRAP5** – tenure 12 is not an allowed tenure → EMI not available → `bills.csv` line 97 (BILL-0096)
- **TRAP6** – tenure chosen on a cashback-only card (AXIS) → no EMI, cashback evaluated → `bills.csv` line 100 (BILL-0099)
- **TRAP7** – PHONE with two CASE lines — brief says 50% off 'the case'; apply to each CASE line and document it → `bills.csv` line 120 (BILL-0119)
- **TRAP8** – return 20 days after bill → outside window, no reversal → `returns.csv` line 15 (RET-014)
- **TRAP9** – return on an EMI bill (no cashback granted) → nothing to reverse → `returns.csv` line 4 (RET-003)

## What is NOT in this pack

Expected outputs are not provided for this set. Validate your engine with your own test suite against the rules in your brief (BR-01 … BR-nn). A separate hidden input set with the same schema will be used at final evaluation; outputs are compared field-for-field, so follow the column formats above exactly in your own output.

## Output format expected at evaluation

**`offer_summary.csv`** — one row per BILL command, in command order:
`bill_id, customer_id, bill_date, card_issuer, gross_amount, bundle_discount, bill_amount, emi_available, tenure_months, instalment, last_instalment, processing_fee, interest_saved, cashback_eligible, cashback_amount, recommended_option, benefit_amount`
- bill_amount = gross − bundle discount; emi_available / cashback_eligible are true/false; cashback_amount is after the monthly cap.
- recommended_option = EMI, CASHBACK or NONE; benefit_amount = interest_saved for EMI, cashback_amount for CASHBACK, 0.00 for NONE.

**`emi_schedule.csv`** — one row per instalment for bills where EMI was recommended:
`bill_id, instalment_no, amount` (first instalment includes the processing fee when applicable).

**`returns_log.csv`** — one row per RETURN command:
`return_id, bill_id, line_no, return_date, days_after_bill, within_window, cashback_reversed`

**`cashback_report.csv`** — one row per customer × card that had cashback activity in the month:
`month, customer_id, card_issuer, cashback_granted, cashback_reversed, net_liability, cap_remaining`
