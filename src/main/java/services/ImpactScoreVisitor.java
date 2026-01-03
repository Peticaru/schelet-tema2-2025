package services;
import lombok.Getter;
import models.*;
import utils.Utils;
import visitor.Visitor;

@Getter
public class ImpactScoreVisitor implements Visitor {
    private double score = 0.0;

    @Override
    public void visit(BugTicket t) {
        // Access specific Bug fields without casting
        double base = Utils.getFrequencyValue(t.getFrequency()) * Utils.getPriorityValue(t.getBusinessPriority()) * Utils.getSeverityValue(t.getSeverity());
        this.score = Utils.calculateImpactFinal(base, 48.0);
    }

    @Override
    public void visit(FeatureRequestTicket t) {
        double base = Utils.getBusinessValue(t.getBusinessValue()) * Utils.getCustomerDemandValue(t.getCustomerDemand());
        this.score = Utils.calculateImpactFinal(base, 100.0);
    }

    @Override
    public void visit(UiFeedbackTicket t) {
        double base = Utils.getBusinessValue(t.getBusinessValue()) * t.getUsabilityScore();
        this.score = Utils.calculateImpactFinal(base, 100.0);
    }
}