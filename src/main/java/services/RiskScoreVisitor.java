package services;

import models.*;
import visitor.Visitor;

public class RiskScoreVisitor implements Visitor {
    private double score = 0.0;

    public double getScore() {
        return score;
    }

    @Override
    public void visit(BugTicket bug) {
        double base = getFrequencyValue(bug.getFrequency()) * getSeverityValue(bug.getSeverity());
        double max = 12.0; // 4 * 3
        this.score = calculateImpactFinal(base, max);
    }

    @Override
    public void visit(FeatureRequestTicket fr) {
        double base = getBusinessValue(fr.getBusinessValue()) + getCustomerDemandValue(fr.getCustomerDemand());
        double max = 20.0; // 10 + 10
        this.score = calculateImpactFinal(base, max);
    }

    @Override
    public void visit(UiFeedbackTicket ui) {
        double base = (11.0 - ui.getUsabilityScore()) * getBusinessValue(ui.getBusinessValue());
        double max = 100.0; // (11-1)*10
        this.score = calculateImpactFinal(base, max);
    }

    // Helper methods moved/copied from CommandRunner logic
    private double calculateImpactFinal(double baseScore, double maxValue) {
        if (maxValue == 0) return 0.0;
        return Math.min(100.0, (baseScore * 100.0) / maxValue);
    }

    private double getSeverityValue(Severity s) {
        if (s == Severity.MINOR) return 1;
        if (s == Severity.MODERATE) return 2;
        if (s == Severity.SEVERE) return 3;
        return 0;
    }

    private double getFrequencyValue(Frequency f) {
        if (f == Frequency.RARE) return 1;
        if (f == Frequency.OCCASIONAL) return 2;
        if (f == Frequency.FREQUENT) return 3;
        if (f == Frequency.ALWAYS) return 4;
        return 0;
    }

    private double getBusinessValue(BusinessValue s) {
        if (s == BusinessValue.S) return 1;
        if (s == BusinessValue.M) return 3;
        if (s == BusinessValue.L) return 6;
        if (s == BusinessValue.XL) return 10;
        return 0;
    }

    private double getCustomerDemandValue(CustomerDemand s) {
        if (s == CustomerDemand.LOW) return 1;
        if (s == CustomerDemand.MEDIUM) return 3;
        if (s == CustomerDemand.HIGH) return 6;
        if (s == CustomerDemand.VERY_HIGH) return 10;
        return 0;
    }
}