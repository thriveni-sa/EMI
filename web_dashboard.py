import csv
import json
import os
import subprocess
from flask import Flask, jsonify, render_template_string, request

app = Flask(__name__)
BASE_DIR = os.path.abspath(os.path.dirname(__file__))

def load_csv(filename):
    filepath = os.path.join(BASE_DIR, filename)
    if not os.path.exists(filepath):
        return []
    with open(filepath, mode="r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return list(reader)

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ECE-035: Retail Offer Engine Dashboard</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/font/bootstrap-icons.css">
    <style>
        body { background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b; }
        .navbar-custom { background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%); }
        .card { border: none; border-radius: 12px; box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.05), 0 2px 4px -2px rgb(0 0 0 / 0.05); }
        .metric-card { transition: transform 0.15s ease-in-out; }
        .metric-card:hover { transform: translateY(-2px); }
        .badge-emi { background-color: #2563eb; color: white; }
        .badge-cashback { background-color: #16a34a; color: white; }
        .badge-none { background-color: #64748b; color: white; }
        .table-responsive { max-height: 580px; overflow-y: auto; }
        .nav-pills .nav-link.active { background-color: #1e3a8a; }
        .trap-btn { font-size: 0.8rem; margin: 2px; }
        .sticky-top-header th { position: sticky; top: 0; background-color: #f1f5f9; z-index: 10; }
    </style>
</head>
<body>
    <nav class="navbar navbar-custom navbar-dark py-3">
        <div class="container-fluid px-4">
            <span class="navbar-brand mb-0 h1 fs-4 d-flex align-items-center">
                <i class="bi bi-cpu-fill me-2 fs-3 text-info"></i>
                <div>
                    <span class="fw-bold">ECE-035: Retail Offer Engine</span>
                    <span class="badge bg-primary ms-2 fs-6">Team E005 Capstone</span>
                </div>
            </span>
            <div class="d-flex align-items-center">
                <button class="btn btn-outline-light btn-sm me-2" onclick="reRunEngine()">
                    <i class="bi bi-arrow-clockwise me-1"></i> Re-Run Engine (CLI)
                </button>
                <span class="badge bg-success-subtle text-success border border-success px-3 py-2">
                    <i class="bi bi-check-circle-fill me-1"></i> Java 17 LTS Active
                </span>
            </div>
        </div>
    </nav>

    <div class="container-fluid px-4 py-4">
        <!-- Quick Metrics Row -->
        <div class="row g-3 mb-4">
            <div class="col-md-2 col-sm-4">
                <div class="card metric-card p-3 bg-white">
                    <div class="text-muted small fw-semibold">TOTAL BILLS</div>
                    <div class="fs-3 fw-bold text-dark" id="stat-bills">200</div>
                    <div class="text-success small"><i class="bi bi-check2-all"></i> 100% Processed</div>
                </div>
            </div>
            <div class="col-md-2 col-sm-4">
                <div class="card metric-card p-3 bg-white">
                    <div class="text-muted small fw-semibold">NO-COST EMI</div>
                    <div class="fs-3 fw-bold text-primary" id="stat-emi">-</div>
                    <div class="text-muted small">Recommended</div>
                </div>
            </div>
            <div class="col-md-2 col-sm-4">
                <div class="card metric-card p-3 bg-white">
                    <div class="text-muted small fw-semibold">INSTANT CASHBACK</div>
                    <div class="fs-3 fw-bold text-success" id="stat-cashback">-</div>
                    <div class="text-muted small">Recommended</div>
                </div>
            </div>
            <div class="col-md-2 col-sm-4">
                <div class="card metric-card p-3 bg-white">
                    <div class="text-muted small fw-semibold">NO OFFER (NONE)</div>
                    <div class="fs-3 fw-bold text-secondary" id="stat-none">-</div>
                    <div class="text-muted small">Ineligible / Unrequested</div>
                </div>
            </div>
            <div class="col-md-2 col-sm-4">
                <div class="card metric-card p-3 bg-white">
                    <div class="text-muted small fw-semibold">BUNDLE SAVINGS</div>
                    <div class="fs-4 fw-bold text-info" id="stat-bundles">-</div>
                    <div class="text-muted small">TV & Phone Combos</div>
                </div>
            </div>
            <div class="col-md-2 col-sm-4">
                <div class="card metric-card p-3 bg-white">
                    <div class="text-muted small fw-semibold">RETURNS PROCESSED</div>
                    <div class="fs-3 fw-bold text-warning" id="stat-returns">15</div>
                    <div class="text-muted small">Clawback Enforced</div>
                </div>
            </div>
        </div>

        <!-- Planted Traps & Quick Scenarios Bar -->
        <div class="card mb-4 bg-white p-3">
            <div class="d-flex align-items-center flex-wrap gap-1">
                <span class="fw-bold me-2 text-dark small"><i class="bi bi-bookmark-star-fill text-warning me-1"></i>Planted Scenario Inspector:</span>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0026')">EC-1: Rs 5k (Fee)</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0039')">EC-1: Rs 25k (Waived)</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('CUST-003')">EC-2: Cap Saturation</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0052')">EC-3: Non-Divisible EMI</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0016')">EC-4: Exact Tie (CB Wins)</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0030')">TRAP-1: Rs 4999 Min</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0053')">TRAP-2: Rs 24999 Fee</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0066')">TRAP-3: Bundle Post-Waiver</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0088')">TRAP-4: AMEX None</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0096')">TRAP-5: Tenure 12 None</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0099')">TRAP-6: AXIS CB-Only</button>
                <button class="btn btn-outline-secondary trap-btn" onclick="filterSearch('BILL-0119')">TRAP-7: Multi-Case 50%</button>
                <button class="btn btn-outline-primary trap-btn ms-auto" onclick="resetFilters()"><i class="bi bi-x-circle me-1"></i>Reset Filters</button>
            </div>
        </div>

        <!-- Main Tabs -->
        <ul class="nav nav-pills mb-3" id="pills-tab" role="tablist">
            <li class="nav-item" role="presentation">
                <button class="nav-link active fw-semibold" id="pills-summary-tab" data-bs-toggle="pill" data-bs-target="#pills-summary" type="button" role="tab">
                    <i class="bi bi-file-earmark-spreadsheet me-1"></i> 1. Offer Summary (200 Bills)
                </button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link fw-semibold" id="pills-schedule-tab" data-bs-toggle="pill" data-bs-target="#pills-schedule" type="button" role="tab">
                    <i class="bi bi-calendar-month me-1"></i> 2. EMI Schedule (228 Instalments)
                </button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link fw-semibold" id="pills-returns-tab" data-bs-toggle="pill" data-bs-target="#pills-returns" type="button" role="tab">
                    <i class="bi bi-arrow-return-left me-1"></i> 3. Returns Log (15 Returns)
                </button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link fw-semibold" id="pills-cashback-tab" data-bs-toggle="pill" data-bs-target="#pills-cashback" type="button" role="tab">
                    <i class="bi bi-wallet2 me-1"></i> 4. Cashback Ledger Report (70 Accounts)
                </button>
            </li>
        </ul>

        <div class="tab-content" id="pills-tabContent">
            <!-- TAB 1: OFFER SUMMARY -->
            <div class="tab-pane fade show active" id="pills-summary" role="tabpanel">
                <div class="card p-3 bg-white">
                    <div class="row g-2 mb-3 align-items-center">
                        <div class="col-md-4">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text"><i class="bi bi-search"></i></span>
                                <input type="text" id="summarySearch" class="form-control" placeholder="Search Bill ID, Customer, Card..." onkeyup="applySummaryFilter()">
                            </div>
                        </div>
                        <div class="col-md-3">
                            <select id="issuerFilter" class="form-select form-select-sm" onchange="applySummaryFilter()">
                                <option value="">All Card Issuers</option>
                                <option value="HDFC">HDFC Bank</option>
                                <option value="ICICI">ICICI Bank</option>
                                <option value="SBI">SBI Card (EMI Only)</option>
                                <option value="AXIS">AXIS Bank (CB Only)</option>
                                <option value="KOTAK">KOTAK Bank</option>
                                <option value="AMEX">AMEX (Non-Partner)</option>
                            </select>
                        </div>
                        <div class="col-md-3">
                            <select id="optionFilter" class="form-select form-select-sm" onchange="applySummaryFilter()">
                                <option value="">All Recommendations</option>
                                <option value="EMI">No-Cost EMI</option>
                                <option value="CASHBACK">Instant Cashback</option>
                                <option value="NONE">None</option>
                            </select>
                        </div>
                        <div class="col-md-2 text-end text-muted small">
                            Showing <span id="summary-count" class="fw-bold text-dark">0</span> rows
                        </div>
                    </div>
                    <div class="table-responsive">
                        <table class="table table-hover table-sm align-middle small sticky-top-header">
                            <thead>
                                <tr>
                                    <th>Bill ID</th>
                                    <th>Customer</th>
                                    <th>Date</th>
                                    <th>Card</th>
                                    <th class="text-end">Gross</th>
                                    <th class="text-end">Bundle Disc.</th>
                                    <th class="text-end">Net Bill</th>
                                    <th>EMI?</th>
                                    <th>Tenure</th>
                                    <th class="text-end">Instalment</th>
                                    <th class="text-end">Last Inst.</th>
                                    <th class="text-end">Fee</th>
                                    <th class="text-end">Interest Saved</th>
                                    <th>CB?</th>
                                    <th class="text-end">Cashback</th>
                                    <th>Recommended</th>
                                    <th class="text-end">Benefit</th>
                                </tr>
                            </thead>
                            <tbody id="summaryTableBody">
                                <!-- Populated dynamically -->
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- TAB 2: EMI SCHEDULE -->
            <div class="tab-pane fade" id="pills-schedule" role="tabpanel">
                <div class="card p-3 bg-white">
                    <div class="row g-2 mb-3 align-items-center">
                        <div class="col-md-4">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text"><i class="bi bi-search"></i></span>
                                <input type="text" id="scheduleSearch" class="form-control" placeholder="Search Bill ID..." onkeyup="applyScheduleFilter()">
                            </div>
                        </div>
                        <div class="col-md-8 text-end text-muted small">
                            Instalment 1 includes Rs 199.00 fee when applicable | Showing <span id="schedule-count" class="fw-bold text-dark">0</span> rows
                        </div>
                    </div>
                    <div class="table-responsive">
                        <table class="table table-hover table-sm align-middle small sticky-top-header">
                            <thead>
                                <tr>
                                    <th>Bill ID</th>
                                    <th>Instalment No</th>
                                    <th class="text-end">Monthly Repayment (Rs)</th>
                                </tr>
                            </thead>
                            <tbody id="scheduleTableBody">
                                <!-- Populated dynamically -->
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- TAB 3: RETURNS LOG -->
            <div class="tab-pane fade" id="pills-returns" role="tabpanel">
                <div class="card p-3 bg-white">
                    <div class="row g-2 mb-3 align-items-center">
                        <div class="col-md-4">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text"><i class="bi bi-search"></i></span>
                                <input type="text" id="returnsSearch" class="form-control" placeholder="Search Return ID or Bill ID..." onkeyup="applyReturnsFilter()">
                            </div>
                        </div>
                        <div class="col-md-8 text-end text-muted small">
                            Statutory 7-Day Window (&le; 7 days = clawback; > 7 days = keep) | Showing <span id="returns-count" class="fw-bold text-dark">0</span> rows
                        </div>
                    </div>
                    <div class="table-responsive">
                        <table class="table table-hover table-sm align-middle small sticky-top-header">
                            <thead>
                                <tr>
                                    <th>Return ID</th>
                                    <th>Bill ID</th>
                                    <th>Item Line</th>
                                    <th>Return Date</th>
                                    <th>Days Elapsed</th>
                                    <th>Within 7-Day Window?</th>
                                    <th class="text-end">Cashback Reversed (Rs)</th>
                                </tr>
                            </thead>
                            <tbody id="returnsTableBody">
                                <!-- Populated dynamically -->
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            <!-- TAB 4: CASHBACK LEDGER REPORT -->
            <div class="tab-pane fade" id="pills-cashback" role="tabpanel">
                <div class="card p-3 bg-white">
                    <div class="row g-2 mb-3 align-items-center">
                        <div class="col-md-4">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text"><i class="bi bi-search"></i></span>
                                <input type="text" id="cashbackSearch" class="form-control" placeholder="Search Customer or Card..." onkeyup="applyCashbackFilter()">
                            </div>
                        </div>
                        <div class="col-md-8 text-end text-muted small">
                            Cap: Rs 1,500.00 / cust / card / month | Net Liability = Granted - Reversed | Showing <span id="cashback-count" class="fw-bold text-dark">0</span> rows
                        </div>
                    </div>
                    <div class="table-responsive">
                        <table class="table table-hover table-sm align-middle small sticky-top-header">
                            <thead>
                                <tr>
                                    <th>Month</th>
                                    <th>Customer ID</th>
                                    <th>Card Issuer</th>
                                    <th class="text-end">Cashback Granted (Rs)</th>
                                    <th class="text-end">Cashback Reversed (Rs)</th>
                                    <th class="text-end">Net Merchant Liability (Rs)</th>
                                    <th class="text-end">Cap Remaining (Rs)</th>
                                </tr>
                            </thead>
                            <tbody id="cashbackTableBody">
                                <!-- Populated dynamically -->
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        let summaryData = [];
        let scheduleData = [];
        let returnsData = [];
        let cashbackData = [];

        async function loadAllData() {
            try {
                const res = await fetch('/api/data');
                const data = await res.json();
                summaryData = data.summary;
                scheduleData = data.schedule;
                returnsData = data.returns;
                cashbackData = data.cashback;

                updateMetrics();
                renderSummary(summaryData);
                renderSchedule(scheduleData);
                renderReturns(returnsData);
                renderCashback(cashbackData);
            } catch (err) {
                console.error("Failed to load data:", err);
            }
        }

        function updateMetrics() {
            let emiCount = 0, cbCount = 0, noneCount = 0;
            let totalBundles = 0;

            summaryData.forEach(r => {
                if (r.recommended_option === 'EMI') emiCount++;
                else if (r.recommended_option === 'CASHBACK') cbCount++;
                else noneCount++;

                totalBundles += parseFloat(r.bundle_discount || 0);
            });

            document.getElementById('stat-bills').innerText = summaryData.length;
            document.getElementById('stat-emi').innerText = emiCount;
            document.getElementById('stat-cashback').innerText = cbCount;
            document.getElementById('stat-none').innerText = noneCount;
            document.getElementById('stat-bundles').innerText = 'Rs ' + totalBundles.toLocaleString('en-IN', {minimumFractionDigits: 2});
            document.getElementById('stat-returns').innerText = returnsData.length;
        }

        function renderSummary(rows) {
            const tbody = document.getElementById('summaryTableBody');
            tbody.innerHTML = '';
            document.getElementById('summary-count').innerText = rows.length;

            rows.forEach(r => {
                const tr = document.createElement('tr');
                const optBadge = r.recommended_option === 'EMI' ? 'badge-emi' :
                                 r.recommended_option === 'CASHBACK' ? 'badge-cashback' : 'badge-none';
                
                tr.innerHTML = `
                    <td class="fw-bold">${r.bill_id}</td>
                    <td>${r.customer_id}</td>
                    <td>${r.bill_date}</td>
                    <td><span class="badge bg-light text-dark border">${r.card_issuer}</span></td>
                    <td class="text-end">${r.gross_amount}</td>
                    <td class="text-end text-danger">${r.bundle_discount !== '0.00' ? '- ' + r.bundle_discount : '-'}</td>
                    <td class="text-end fw-semibold">${r.bill_amount}</td>
                    <td>${r.emi_available === 'true' ? '<i class="bi bi-check-circle-fill text-success"></i>' : '<i class="bi bi-x-circle text-muted"></i>'}</td>
                    <td>${r.tenure_months || '-'}</td>
                    <td class="text-end">${r.instalment !== '0.00' ? r.instalment : '-'}</td>
                    <td class="text-end">${r.last_instalment !== '0.00' ? r.last_instalment : '-'}</td>
                    <td class="text-end text-warning">${r.processing_fee !== '0.00' ? r.processing_fee : 'Waived'}</td>
                    <td class="text-end text-primary">${r.interest_saved !== '0.00' ? r.interest_saved : '-'}</td>
                    <td>${r.cashback_eligible === 'true' ? '<i class="bi bi-check-circle-fill text-success"></i>' : '<i class="bi bi-x-circle text-muted"></i>'}</td>
                    <td class="text-end text-success">${r.cashback_amount !== '0.00' ? r.cashback_amount : '-'}</td>
                    <td><span class="badge ${optBadge}">${r.recommended_option}</span></td>
                    <td class="text-end fw-bold text-success">Rs ${r.benefit_amount}</td>
                `;
                tbody.appendChild(tr);
            });
        }

        function renderSchedule(rows) {
            const tbody = document.getElementById('scheduleTableBody');
            tbody.innerHTML = '';
            document.getElementById('schedule-count').innerText = rows.length;

            rows.forEach(r => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td class="fw-bold">${r.bill_id}</td>
                    <td><span class="badge bg-secondary-subtle text-dark">Month ${r.instalment_no}</span></td>
                    <td class="text-end fw-semibold text-primary">Rs ${r.amount}</td>
                `;
                tbody.appendChild(tr);
            });
        }

        function renderReturns(rows) {
            const tbody = document.getElementById('returnsTableBody');
            tbody.innerHTML = '';
            document.getElementById('returns-count').innerText = rows.length;

            rows.forEach(r => {
                const tr = document.createElement('tr');
                const winBadge = r.within_window === 'true' ? 
                    '<span class="badge bg-success"><i class="bi bi-check-lg me-1"></i>Within Window (&le; 7d)</span>' : 
                    '<span class="badge bg-danger"><i class="bi bi-x-lg me-1"></i>Outside Window (> 7d)</span>';
                tr.innerHTML = `
                    <td class="fw-bold">${r.return_id}</td>
                    <td>${r.bill_id}</td>
                    <td>Line ${r.line_no}</td>
                    <td>${r.return_date}</td>
                    <td><span class="badge bg-light text-dark border">${r.days_after_bill} days</span></td>
                    <td>${winBadge}</td>
                    <td class="text-end fw-bold ${r.cashback_reversed !== '0.00' ? 'text-danger' : 'text-muted'}">Rs ${r.cashback_reversed}</td>
                `;
                tbody.appendChild(tr);
            });
        }

        function renderCashback(rows) {
            const tbody = document.getElementById('cashbackTableBody');
            tbody.innerHTML = '';
            document.getElementById('cashback-count').innerText = rows.length;

            rows.forEach(r => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><span class="badge bg-dark">${r.month}</span></td>
                    <td class="fw-bold">${r.customer_id}</td>
                    <td><span class="badge bg-light text-dark border">${r.card_issuer}</span></td>
                    <td class="text-end text-success">${r.cashback_granted}</td>
                    <td class="text-end text-danger">${r.cashback_reversed}</td>
                    <td class="text-end fw-bold text-dark">Rs ${r.net_liability}</td>
                    <td class="text-end text-primary fw-semibold">Rs ${r.cap_remaining}</td>
                `;
                tbody.appendChild(tr);
            });
        }

        function applySummaryFilter() {
            const q = document.getElementById('summarySearch').value.toLowerCase();
            const issuer = document.getElementById('issuerFilter').value;
            const option = document.getElementById('optionFilter').value;

            const filtered = summaryData.filter(r => {
                const matchQ = !q || r.bill_id.toLowerCase().includes(q) || r.customer_id.toLowerCase().includes(q) || r.card_issuer.toLowerCase().includes(q);
                const matchIssuer = !issuer || r.card_issuer === issuer;
                const matchOpt = !option || r.recommended_option === option;
                return matchQ && matchIssuer && matchOpt;
            });
            renderSummary(filtered);
        }

        function applyScheduleFilter() {
            const q = document.getElementById('scheduleSearch').value.toLowerCase();
            const filtered = scheduleData.filter(r => !q || r.bill_id.toLowerCase().includes(q));
            renderSchedule(filtered);
        }

        function applyReturnsFilter() {
            const q = document.getElementById('returnsSearch').value.toLowerCase();
            const filtered = returnsData.filter(r => !q || r.return_id.toLowerCase().includes(q) || r.bill_id.toLowerCase().includes(q));
            renderReturns(filtered);
        }

        function applyCashbackFilter() {
            const q = document.getElementById('cashbackSearch').value.toLowerCase();
            const filtered = cashbackData.filter(r => !q || r.customer_id.toLowerCase().includes(q) || r.card_issuer.toLowerCase().includes(q));
            renderCashback(filtered);
        }

        function filterSearch(term) {
            // Activate Tab 1
            const tabBtn = document.getElementById('pills-summary-tab');
            const tab = new bootstrap.Tab(tabBtn);
            tab.show();

            document.getElementById('summarySearch').value = term;
            document.getElementById('issuerFilter').value = '';
            document.getElementById('optionFilter').value = '';
            applySummaryFilter();
        }

        function resetFilters() {
            document.getElementById('summarySearch').value = '';
            document.getElementById('issuerFilter').value = '';
            document.getElementById('optionFilter').value = '';
            applySummaryFilter();
        }

        async function reRunEngine() {
            try {
                const btn = event.target;
                btn.disabled = true;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Running...';
                await fetch('/api/run', { method: 'POST' });
                await loadAllData();
                btn.disabled = false;
                btn.innerHTML = '<i class="bi bi-arrow-clockwise me-1"></i> Re-Run Engine (CLI)';
                alert('Engine executed successfully! Reports refreshed.');
            } catch (err) {
                alert('Failed to re-run engine: ' + err);
            }
        }

        window.onload = loadAllData;
    </script>
</body>
</html>
"""

@app.route("/")
def index():
    return render_template_string(HTML_TEMPLATE)

@app.route("/api/data")
def get_data():
    return jsonify({
        "summary": load_csv("offer_summary.csv"),
        "schedule": load_csv("emi_schedule.csv"),
        "returns": load_csv("returns_log.csv"),
        "cashback": load_csv("cashback_report.csv")
    })

@app.route("/api/run", methods=["POST"])
def run_cli():
    cmd = ["java", "-jar", "target/ece-035-offer-engine-1.0.0.jar", "commands.txt"]
    env = os.environ.copy()
    env["JAVA_HOME"] = r"C:\Users\Thriveni S A\jdks\jdk-24.0.2"
    env["PATH"] = r"C:\Users\Thriveni S A\jdks\jdk-24.0.2\bin;" + env["PATH"]
    res = subprocess.run(cmd, cwd=BASE_DIR, env=env, capture_output=True, text=True)
    return jsonify({
        "success": res.returncode == 0,
        "stdout": res.stdout,
        "stderr": res.stderr
    })

if __name__ == "__main__":
    print("Starting ECE-035 Retail Offer Engine Web Dashboard on http://localhost:8080 ...")
    app.run(host="127.0.0.1", port=8080, debug=False)

