package utils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.EfficiencyScoreVisitor;
import services.ImpactScoreVisitor;
import services.RiskScoreVisitor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static double getFrequencyValue(Frequency f) {
        if (f == Frequency.RARE) return 1;
        if (f == Frequency.OCCASIONAL) return 2;
        if (f == Frequency.FREQUENT) return 3;
        if (f == Frequency.ALWAYS) return 4;
        return 0;
    }
    public static double getMaxFrequencyValue() { return 4; }

    // Priority: LOW=1, MEDIUM=2, HIGH=3, CRITICAL=4
    public static double getPriorityValue(Priority p) {
        if (p == Priority.LOW) return 1;
        if (p == Priority.MEDIUM) return 2;
        if (p == Priority.HIGH) return 3;
        if (p == Priority.CRITICAL) return 4;
        return 0;
    }

    public static double getBusinessValue(BusinessValue s) {
        if (s == BusinessValue.S) return 1;
        if (s == BusinessValue.M) return 3;
        if (s == BusinessValue.L) return 6;
        if (s == BusinessValue.XL) return 10;
        return 0;
    }
    private static double getMaxCustomerDemandValue() { return 10; }

    public static double getMaxBusinessValue() { return 10; }

    // CustomerDemand: LOW=1, MEDIUM=3, HIGH=6, VERY_HIGH=10
    public static double getCustomerDemandValue(CustomerDemand s) {
        if (s == CustomerDemand.LOW) return 1;
        if (s == CustomerDemand.MEDIUM) return 3;
        if (s == CustomerDemand.HIGH) return 6;
        if (s == CustomerDemand.VERY_HIGH) return 10;
        return 0;
    }

    public static double calculateImpactFinal(double baseScore, double maxValue) {
        if (maxValue == 0) return 0.0;
        return Math.min(100.0, (baseScore * 100.0) / maxValue);
    }

    public static double getSeverityValue(Severity s) {
        if (s == Severity.MINOR) return 1;
        if (s == Severity.MODERATE) return 2;
        if (s == Severity.SEVERE) return 3;
        return 0;
    }

    public static boolean isSeniorityMatch(Developer dev, Ticket ticket) {
        Priority p = ticket.getBusinessPriority();
        Seniority s = dev.getSeniority();
        if (p == Priority.CRITICAL) return s == Seniority.SENIOR;
        if (p == Priority.HIGH) return s == Seniority.MID || s == Seniority.SENIOR;
        return true;
    }

    public static String getRequiredSeniorityString(Ticket ticket) {
        Priority p = ticket.getBusinessPriority();
        if (p == Priority.CRITICAL) return "SENIOR";
        if (p == Priority.HIGH) return "MID, SENIOR";
        return "JUNIOR, MID, SENIOR";
    }

    public static boolean isExpertiseMatch(Developer dev, Ticket ticket) {
        ExpertiseArea devExp = dev.getExpertiseArea();
        ExpertiseArea ticketExp = ticket.getExpertiseArea();
        if (devExp == ExpertiseArea.FULLSTACK) return true;
        if (devExp == ticketExp) return true;
        if (devExp == ExpertiseArea.BACKEND && ticketExp == ExpertiseArea.DB) return true;
        return devExp == ExpertiseArea.FRONTEND && ticketExp == ExpertiseArea.DESIGN;
    }

    public static String getRequiredExpertiseString(ExpertiseArea ticketExp) {
        if (ticketExp == ExpertiseArea.DB || ticketExp == ExpertiseArea.BACKEND) return "BACKEND, DB, FULLSTACK";
        if (ticketExp == ExpertiseArea.DESIGN || ticketExp == ExpertiseArea.FRONTEND) return "DESIGN, FRONTEND, FULLSTACK";
        if (ticketExp == ExpertiseArea.DEVOPS) return "DEVOPS, FULLSTACK";
        return ticketExp + ", FULLSTACK";
    }

    public static LocalDate whereClosed(Ticket t) {
        LocalDate last = null;
        for (HistoryEntry h : t.getHistory()) {
            LocalDate d = LocalDate.parse(h.getTimestamp());
            if (last == null || d.isAfter(last)) last = d;
        }
        return last;
    }

    private static double standardDeviation(int bug, int feature, int ui) {
        double mean = (bug + feature + ui) / 3.0;
        double variance = (Math.pow(bug - mean, 2) + Math.pow(feature - mean, 2) + Math.pow(ui - mean, 2)) / 3.0;
        return Math.sqrt(variance);
    }

    private static double ticketDiversityFactor(int bug, int feature, int ui) {
        double mean = (bug + feature + ui) / 3.0;
        double std = standardDeviation(bug, feature, ui);
        return std / mean;
    }

    public static double computePerformanceScore(Seniority s,
                                           int closedTickets,
                                           int highPriorityTickets,
                                           double avgResolutionTime,
                                           int bugTickets, int featureTickets, int uiTickets) {
        if (closedTickets == 0) {
            return 0.0;
        }

        double bonus = switch (s) {
            case JUNIOR -> 5.0;
            case MID -> 15.0;
            case SENIOR -> 30.0;
        };

        if (s == Seniority.JUNIOR) {
            double diversity = ticketDiversityFactor(bugTickets, featureTickets, uiTickets);
            return Math.max(0.0, 0.5 * closedTickets - diversity) + bonus;
        }

        if (s == Seniority.MID) {
            return Math.max(0.0, 0.5 * closedTickets + 0.7 * highPriorityTickets - 0.3 * avgResolutionTime) + bonus;
        }

        return Math.max(0.0, 0.5 * closedTickets + 1.0 * highPriorityTickets - 0.5 * avgResolutionTime) + bonus;
    }

    public static double riskScoreNormalized(Ticket t) {
        RiskScoreVisitor visitor = new RiskScoreVisitor();
        t.accept(visitor);
        return visitor.getScore();
    }

    public static String riskQualifier(double riskAvg) {
        if (riskAvg <= 24.0) return "NEGLIGIBLE";
        if (riskAvg <= 49.0) return "MODERATE";
        if (riskAvg <= 74.0) return "SIGNIFICANT";
        return "MAJOR";
    }

    public static double avgRiskForType(List<Ticket> tickets, String type) {
        List<Double> scores = new ArrayList<>();
        for (Ticket t : tickets) {
            if (type.equals(t.getType())) {
                scores.add(riskScoreNormalized(t));
            }
        }
        if (scores.isEmpty()) return 0.0;
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public static double calculateAverageImpact(List<Ticket> allTickets, String type) {
        List<Double> scores = new ArrayList<>();
        ImpactScoreVisitor visitor = new ImpactScoreVisitor();
        for (Ticket t : allTickets) {
            if (t.getType().equals(type)) {
                t.accept(visitor);
                scores.add(visitor.getScore());
            }
        }
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return Math.round(avg * 100.0) / 100.0;
    }
    public static String computeStability(List<Ticket> open,
                                    String bugRisk, String featureRisk, String uiRisk,
                                    ObjectNode impactByType) {

        if ("SIGNIFICANT".equals(bugRisk) || "SIGNIFICANT".equals(featureRisk) || "SIGNIFICANT".equals(uiRisk)) {
            return "UNSTABLE";
        }

        boolean allNegligible = "NEGLIGIBLE".equals(bugRisk) && "NEGLIGIBLE".equals(featureRisk) && "NEGLIGIBLE".equals(uiRisk);
        double featureImpact = impactByType.get("FEATURE_REQUEST").asDouble();
        double uiImpact = impactByType.get("UI_FEEDBACK").asDouble();
        boolean allImpactBelow50 =  featureImpact < 50.0 && uiImpact < 50.0;

        if (allNegligible && allImpactBelow50) return "STABLE";

        return "PARTIALLY STABLE";
    }

    public static double avgEfficiencyForType(List<Ticket> tickets, String type) {
        List<Double> vals = new ArrayList<>();
        for (Ticket t : tickets) {
            if (type.equals(t.getType())) {
                int days = daysToResolve(t);
                EfficiencyScoreVisitor visitor = new EfficiencyScoreVisitor(days);
                t.accept(visitor);
                vals.add(visitor.getScore());
            }
        }
        return vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public static int daysToResolve(Ticket t) {
        LocalDate assigned = LocalDate.parse(t.getAssignedAt());
        LocalDate end = whereClosed(t);
        return (int) ChronoUnit.DAYS.between(assigned, end) + 1;
    }



}
