package com.retail.engine.service;

import com.retail.engine.config.OfferConfiguration;
import com.retail.engine.model.Bill;
import com.retail.engine.model.CardIssuer;
import com.retail.engine.model.EmiSchedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain service evaluating No-Cost EMI calculations, fee waivers, subventions, and repayment schedules.
 */
public class EmiCalculatorService {

    private static final BigDecimal ZERO_SCALE_TWO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public record EmiResult(
            boolean available,
            Integer tenureMonths,
            BigDecimal instalment,
            BigDecimal lastInstalment,
            BigDecimal processingFee,
            BigDecimal interestSaved
    ) {
        public static EmiResult unavailable() {
            return new EmiResult(false, null, ZERO_SCALE_TWO, ZERO_SCALE_TWO, ZERO_SCALE_TWO, ZERO_SCALE_TWO);
        }
    }

    public EmiResult evaluateEmi(Bill bill, BigDecimal netBillAmount, CardIssuer card, OfferConfiguration config) {
        // BR-01: Card must be EMI eligible
        if (card == null || !card.isEmiEligible()) {
            return EmiResult.unavailable();
        }

        // BR-06: Customer must have requested a tenure
        if (!bill.hasTenureRequested()) {
            return EmiResult.unavailable();
        }

        int tenure = bill.getTenureMonths().get();

        // BR-08 & TRAP-05: Tenure must be permissible (3, 6, 9)
        if (!config.isTenurePermissible(tenure)) {
            return EmiResult.unavailable();
        }

        // BR-07 & TRAP-01: Net bill amount must meet minimum threshold (>= 5000.00)
        if (netBillAmount.compareTo(config.getEmiMinBill()) < 0) {
            return EmiResult.unavailable();
        }

        // BR-09, EC-01 & TRAP-02, TRAP-03: Processing fee waiver threshold (>= 25000.00)
        BigDecimal fee = ZERO_SCALE_TWO;
        if (netBillAmount.compareTo(config.getProcessingFeeWaivedFrom()) < 0) {
            fee = config.getProcessingFee().setScale(2, RoundingMode.HALF_UP);
        }

        // BR-05 & BR-10: Interest Saved = Net * Rate * Tenure / 1200 (15% annual flat interest)
        BigDecimal interestSaved = netBillAmount
                .multiply(config.getEmiInterestPaPct())
                .multiply(BigDecimal.valueOf(tenure))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);

        // BR-11 & EC-03: Instalment ceiling and last instalment adjustment
        BigDecimal baseInstalment = netBillAmount.divide(BigDecimal.valueOf(tenure), 0, RoundingMode.CEILING).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalExceptLast = baseInstalment.multiply(BigDecimal.valueOf(tenure - 1));
        BigDecimal lastInstalment = netBillAmount.subtract(totalExceptLast).setScale(2, RoundingMode.HALF_UP);

        return new EmiResult(true, tenure, baseInstalment, lastInstalment, fee, interestSaved);
    }

    public List<EmiSchedule> generateSchedule(String billId, EmiResult emi) {
        List<EmiSchedule> schedule = new ArrayList<>();
        if (!emi.available() || emi.tenureMonths() == null) {
            return schedule;
        }

        int tenure = emi.tenureMonths();
        for (int i = 1; i <= tenure; i++) {
            BigDecimal amount;
            if (i == 1) {
                // BR-12: First instalment includes processing fee
                amount = emi.instalment().add(emi.processingFee()).setScale(2, RoundingMode.HALF_UP);
            } else if (i == tenure) {
                amount = emi.lastInstalment().setScale(2, RoundingMode.HALF_UP);
            } else {
                amount = emi.instalment().setScale(2, RoundingMode.HALF_UP);
            }
            schedule.add(new EmiSchedule(billId, i, amount));
        }

        return schedule;
    }
}
