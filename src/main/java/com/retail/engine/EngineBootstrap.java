package com.retail.engine;

import com.retail.engine.command.Command;
import com.retail.engine.command.CommandProcessor;
import com.retail.engine.command.CommandType;
import com.retail.engine.config.ConfigLoader;
import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.exception.DataNotFoundException;
import com.retail.engine.exception.EngineException;
import com.retail.engine.model.*;
import com.retail.engine.repository.BillRepository;
import com.retail.engine.repository.CardRepository;
import com.retail.engine.repository.CustomerLedgerRepository;
import com.retail.engine.repository.ReturnRepository;
import com.retail.engine.service.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Orchestrator and lifecycle bootstrap for the ECE-035 Offer Engine.
 * Ensures strict sequential command execution and decoupled domain processing.
 */
public class EngineBootstrap {

    private final Path baseDirectory;

    private OfferConfiguration config;
    private final CardRepository cardRepository = new CardRepository();
    private final BillRepository billRepository = new BillRepository();
    private final ReturnRepository returnRepository = new ReturnRepository();
    private final CustomerLedgerRepository ledgerRepository = new CustomerLedgerRepository();

    private final BundleDiscountService bundleService = new BundleDiscountService();
    private final EmiCalculatorService emiService = new EmiCalculatorService();
    private CashbackService cashbackService;
    private OfferArbitrationEngine arbitrationEngine;
    private ReturnProcessingService returnService;
    private final ReportExportService reportExportService = new ReportExportService();

    private final Map<String, OfferResult> evaluatedBills = new LinkedHashMap<>();

    public EngineBootstrap(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public void initialize() {
        evaluatedBills.clear();
        reportExportService.clear();
        ledgerRepository.clear();

        this.cashbackService = new CashbackService(ledgerRepository);
        this.arbitrationEngine = new OfferArbitrationEngine(bundleService, emiService, cashbackService);
        this.returnService = new ReturnProcessingService(ledgerRepository);

        // Preload master bills, bill items, and returns
        Path billsPath = resolveFile("bills.csv");
        Path itemsPath = resolveFile("bill_items.csv");
        Path returnsPath = resolveFile("returns.csv");

        billRepository.loadBills(billsPath);
        billRepository.loadBillItems(itemsPath);
        returnRepository.load(returnsPath);

        // Validate referential integrity between returns, bills, and line items
        returnRepository.validateReferentialIntegrity(billRepository);
    }

    public void run(Path commandFile) {
        initialize();
        processCommands(commandFile);
        exportRemainingReports();
    }

    public void processCommands(Path commandFile) {
        List<Command> commands = CommandProcessor.parseCommands(commandFile);
        for (Command cmd : commands) {
            executeCommand(cmd);
        }
    }

    private void executeCommand(Command command) {
        switch (command.type()) {
            case LOAD_CONFIG -> {
                Path path = resolveFile(command.getArgument(0));
                this.config = ConfigLoader.load(path);
            }
            case LOAD_CARDS -> {
                Path path = resolveFile(command.getArgument(0));
                cardRepository.load(path);
            }
            case BILL -> {
                String billId = command.getArgument(0);
                handleBill(billId);
            }
            case RETURN -> {
                String returnId = command.getArgument(0);
                handleReturn(returnId);
            }
            case PRINT_CASHBACK_REPORT -> {
                String month = command.getArgument(0);
                handleCashbackReport(month);
            }
        }
    }

    private void handleBill(String billId) {
        // Retrieve bill header
        Bill bill = billRepository.findBillById(billId)
                .orElseThrow(() -> new DataNotFoundException("Bill not found in repository: " + billId));

        // Retrieve all related item lines
        List<BillItem> items = billRepository.getItemsForBill(billId);
        if (items.isEmpty()) {
            throw new DataNotFoundException("No line items found for bill: " + billId);
        }

        CardIssuer card = cardRepository.findByCode(bill.getCardIssuer()).orElse(null);

        // Pass decoupled domain objects to arbitration engine
        OfferResult result = arbitrationEngine.evaluateBill(bill, items, card, config);
        evaluatedBills.put(billId, result);
        reportExportService.recordOfferResult(result);

        if (result.getRecommendedOption() == OfferType.EMI) {
            List<EmiSchedule> schedule = emiService.generateSchedule(
                    billId,
                    new EmiCalculatorService.EmiResult(
                            result.isEmiAvailable(),
                            result.getTenureMonths(),
                            result.getInstalment(),
                            result.getLastInstalment(),
                            result.getProcessingFee(),
                            result.getInterestSaved()
                    )
            );
            reportExportService.recordEmiSchedules(schedule);
        }
    }

    private void handleReturn(String returnId) {
        // Retrieve return transaction
        ReturnTransaction ret = returnRepository.findById(returnId)
                .orElseThrow(() -> new DataNotFoundException("Return not found in repository: " + returnId));

        // Retrieve original bill header
        Bill bill = billRepository.findBillById(ret.getBillId())
                .orElseThrow(() -> new DataNotFoundException("Bill not found for return: " + ret.getBillId()));

        // Retrieve original bill item
        BillItem item = billRepository.getItem(ret.getBillId(), ret.getLineNo())
                .orElseThrow(() -> new DataNotFoundException("Bill line item not found for return: " + ret.getBillId() + ", line: " + ret.getLineNo()));

        OfferResult evaluated = evaluatedBills.get(ret.getBillId());
        if (evaluated == null) {
            throw new EngineException("Return processed for unevaluated bill: " + ret.getBillId());
        }

        ReturnLogRecord logRecord = returnService.processReturn(ret, bill, evaluated, config);
        reportExportService.recordReturnLog(logRecord);
    }

    private void handleCashbackReport(String month) {
        List<CashbackReport> reports = ledgerRepository.generateMonthlyReport(
                month,
                config.getCashbackCapPerCardCustomerMonth()
        );
        try {
            reportExportService.exportCashbackReport(baseDirectory.resolve("cashback_report.csv"), reports);
        } catch (IOException e) {
            throw new EngineException("Failed to write cashback report: " + e.getMessage(), e);
        }
    }

    public void exportRemainingReports() {
        try {
            reportExportService.exportOfferSummary(baseDirectory.resolve("offer_summary.csv"));
            reportExportService.exportEmiSchedule(baseDirectory.resolve("emi_schedule.csv"));
            reportExportService.exportReturnsLog(baseDirectory.resolve("returns_log.csv"));
        } catch (IOException e) {
            throw new EngineException("Failed to export evaluation reports: " + e.getMessage(), e);
        }
    }

    public OfferConfiguration getConfig() { return config; }
    public CardRepository getCardRepository() { return cardRepository; }
    public BillRepository getBillRepository() { return billRepository; }
    public ReturnRepository getReturnRepository() { return returnRepository; }

    private Path resolveFile(String filename) {
        Path direct = baseDirectory.resolve(filename);
        if (Files.exists(direct)) {
            return direct;
        }
        Path dataSubdir = baseDirectory.resolve("data").resolve(filename);
        if (Files.exists(dataSubdir)) {
            return dataSubdir;
        }
        return direct;
    }
}
