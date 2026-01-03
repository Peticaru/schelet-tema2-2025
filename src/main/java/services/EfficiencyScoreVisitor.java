package services;
import lombok.Getter;
import models.*;
import utils.Utils;
import visitor.Visitor;

public class EfficiencyScoreVisitor implements Visitor {
    @Getter
    private double score = 0.0;
    private final int daysToResolve;

    public EfficiencyScoreVisitor(int daysToResolve) {
        this.daysToResolve = daysToResolve;
    }

    @Override
    public void visit(BugTicket t) {
        if (daysToResolve <= 0) return;
        double base = (Utils.getFrequencyValue(t.getFrequency()) + Utils.getSeverityValue(t.getSeverity())) * 10.0 / daysToResolve;
        this.score = Utils.calculateImpactFinal(base, 70.0);
    }

    @Override
    public void visit(FeatureRequestTicket t) {
        if (daysToResolve <= 0) return;
        double base = (Utils.getBusinessValue(t.getBusinessValue()) + Utils.getCustomerDemandValue(t.getCustomerDemand())) / daysToResolve;
        this.score = Utils.calculateImpactFinal(base, 20.0);
    }

    @Override
    public void visit(UiFeedbackTicket t) {
        if (daysToResolve <= 0) return;
        double base = (t.getUsabilityScore() + Utils.getBusinessValue(t.getBusinessValue())) / daysToResolve;
        this.score = Utils.calculateImpactFinal(base, 20.0);
    }
}