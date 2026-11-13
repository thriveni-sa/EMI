package com.retail.engine.service;

import com.retail.engine.model.CashbackReport;
import com.retail.engine.model.EmiSchedule;
import com.retail.engine.model.OfferResult;
import com.retail.engine.model.ReturnLogRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service generating standardized evaluation CSV reports.
 */
public class ReportExportService {

    private final List<OfferResult> offerResults = new ArrayList<>();
    private final List<EmiSchedule> emiSchedules = new ArrayList<>();
    private final List<ReturnLogRecord> returnLogs = new ArrayList<>();

    public void recordOfferResult(OfferResult result) {
        offerResults.add(result);
    }

    public void recordEmiSchedules(List<EmiSchedule> schedules) {
        emiSchedules.addAll(schedules);
    }

    public void recordReturnLog(ReturnLogRecord record) {
        returnLogs.add(record);
    }

    public void clear() {
        offerResults.clear();
        emiSchedules.clear();
        returnLogs.clear();
    }

    public List<OfferResult> getOfferResults() {
        return java.util.Collections.unmodifiableList(offerResults);
    }

    public List<EmiSchedule> getEmiSchedules() {
        return java.util.Collections.unmodifiableList(emiSchedules);
    }

    public List<ReturnLogRecord> getReturnLogs() {
        return java.util.Collections.unmodifiableList(returnLogs);
    }

    public void exportOfferSummary(Path targetFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("bill_id,customer_id,bill_date,card_issuer,gross_amount,bundle_discount,bill_amount," +
                    "emi_available,tenure_months,instalment,last_instalment,processing_fee,interest_saved," +
                    "cashback_eligible,cashback_amount,recommended_option,benefit_amount\n");

            for (OfferResult r : offerResults) {
                String tenureStr = (r.getTenureMonths() == null) ? "" : r.getTenureMonths().toString();
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%b,%s,%s,%s,%s,%s,%b,%s,%s,%s\n",
                        r.getBillId(),
                        r.getCustomerId(),
                        r.getBillDate(),
                        r.getCardIssuer(),
                        r.getGrossAmount().toPlainString(),
                        r.getBundleDiscount().toPlainString(),
                        r.getBillAmount().toPlainString(),
                        r.isEmiAvailable(),
                        tenureStr,
                        r.getInstalment().toPlainString(),
                        r.getLastInstalment().toPlainString(),
                        r.getProcessingFee().toPlainString(),
                        r.getInterestSaved().toPlainString(),
                        r.isCashbackEligible(),
                        r.getCashbackAmount().toPlainString(),
                        r.getRecommendedOption().name(),
                        r.getBenefitAmount().toPlainString()
                ));
            }
        }
    }

    public void exportEmiSchedule(Path targetFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("bill_id,instalment_no,amount\n");
            for (EmiSchedule s : emiSchedules) {
                writer.write(String.format("%s,%d,%s\n",
                        s.getBillId(),
                        s.getInstalmentNo(),
                        s.getAmount().toPlainString()
                ));
            }
        }
    }

    public void exportReturnsLog(Path targetFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("return_id,bill_id,line_no,return_date,days_after_bill,within_window,cashback_reversed\n");
            for (ReturnLogRecord r : returnLogs) {
                writer.write(String.format("%s,%s,%d,%s,%d,%b,%s\n",
                        r.getReturnId(),
                        r.getBillId(),
                        r.getLineNo(),
                        r.getReturnDate(),
                        r.getDaysAfterBill(),
                        r.isWithinWindow(),
                        r.getCashbackReversed().toPlainString()
                ));
            }
        }
    }

    public void exportCashbackReport(Path targetFile, List<CashbackReport> reports) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("month,customer_id,card_issuer,cashback_granted,cashback_reversed,net_liability,cap_remaining\n");
            for (CashbackReport cr : reports) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s\n",
                        cr.getMonth(),
                        cr.getCustomerId(),
                        cr.getCardIssuer(),
                        cr.getCashbackGranted().toPlainString(),
                        cr.getCashbackReversed().toPlainString(),
                        cr.getNetLiability().toPlainString(),
                        cr.getCapRemaining().toPlainString()
                ));
            }
        }
    }
}

