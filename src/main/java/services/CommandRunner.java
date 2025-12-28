package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import commands.CommandInput;
import models.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class CommandRunner {
    private final TicketSystem system;
    private final ObjectMapper mapper;

    public CommandRunner() {
        this.system = TicketSystem.getInstance();
        this.mapper = new ObjectMapper();
    }

    public void execute(CommandInput input, List<ObjectNode> outputs) {
        system.updateTime(input.getTimestamp());

        if (system.isInvestorsLost()) return;

        User user = system.getUsers().get(input.getUsername());
        if (user == null) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user " + input.getUsername() + " does not exist.", input.getTimestamp());
            return;
        }

        switch (input.getCommand()) {
            case "reportTicket": handleReportTicket(input, user, outputs); break;
            case "viewTickets": handleViewTickets(input, user, outputs); break;
            case "createMilestone": handleCreateMilestone(input, user, outputs); break;
            case "viewMilestones": handleViewMilestones(input, user, outputs); break;
            case "assignTicket": handleAssignTicket(input, user, outputs); break;
            case "undoAssignTicket": handleUndoAssignTicket(input, user, outputs); break;
            case "viewAssignedTickets": handleViewAssignedTickets(input, user, outputs); break;
            case "addComment": handleAddComment(input, user, outputs); break;
            case "undoAddComment": handleUndoAddComment(input, user, outputs); break;
            case "changeStatus": handleChangeStatus(input, user, outputs); break;
            case "undoChangeStatus": handleUndoChangeStatus(input, user, outputs); break;
            case "viewTicketHistory": handleViewTicketHistory(input, user, outputs); break;
            case "search": handleSearch(input, user, outputs); break;
            case "viewNotifications": handleViewNotifications(input, user, outputs); break;
            case "generateCustomerImpactReport": handleGenerateCustomerImpactReport(input, user, outputs); break;
            case "generateTicketRiskReport": handleGenerateTicketRiskReport(input, user, outputs); break;
            case "generateResolutionEfficiencyReport": handleGenerateResolutionEfficiencyReport(input, user, outputs); break;
            case "appStabilityReport": handleAppStabilityReport(input, user, outputs); break;
            case "generatePerformanceReport": handleGeneratePerformanceReport(input, user, outputs); break;
            case "lostInvestors": system.setInvestorsLost(true); break;
        }
    }

    private void handleGeneratePerformanceReport(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".",
                    input.getTimestamp());
            return;
        }

        Manager manager = (Manager) user;

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generatePerformanceReport");
        res.put("username", manager.getUsername());
        res.put("timestamp", input.getTimestamp());

        ArrayNode reportArr = mapper.createArrayNode();

        // luna anterioara timestamp-ului comenzii
        LocalDate now = LocalDate.parse(input.getTimestamp());
        int prevMonth = now.minusMonths(1).getMonthValue();
        int prevYear  = now.minusMonths(1).getYear();

        List<String> subs = manager.getSubordinates() == null ? new ArrayList<>() : new ArrayList<>(manager.getSubordinates());
        Collections.sort(subs); // output: devii in ordine lexicografica

        for (String devUsername : subs) {
            User u = system.getUsers().get(devUsername);
            if (!(u instanceof Developer)) continue; // defensiv
            Developer dev = (Developer) u;

            // tichete CLOSED in luna anterioara, asignate dev-ului
            List<Ticket> closedLastMonth = system.getTickets().values().stream()
                    .filter(t -> devUsername.equals(t.getAssignedTo()))
                    .filter(t -> t.getStatus() == Status.CLOSED)
                    .filter(t -> {
                        LocalDate closedAt = getClosedAtFromHistory(t);
                        if (closedAt == null) return false;
                        return closedAt.getYear() == prevYear && closedAt.getMonthValue() == prevMonth;
                    })
                    .toList();

            int closedTickets = closedLastMonth.size();

            // averageResolutionTime = media (solvedAt - assignedAt + 1)
            double avgResolutionTime = 0.0;
            if (closedTickets > 0) {
                double sum = 0.0;
                for (Ticket t : closedLastMonth) {
                    int days = resolutionDaysAssignedToSolved(t);
                    sum += days;
                }
                avgResolutionTime = sum / closedTickets;
            }

            // highPriorityTickets = HIGH sau CRITICAL (foloseste priority-ul curent al tichetului, care poate fi crescut de milestone rules)
            int highPriorityTickets = 0;
            for (Ticket t : closedLastMonth) {
                if (t.getBusinessPriority() == Priority.HIGH || t.getBusinessPriority() == Priority.CRITICAL) {
                    highPriorityTickets++;
                }
            }

            // pentru JUNIOR: diversity pe tipuri
            int bug = 0, feature = 0, ui = 0;
            for (Ticket t : closedLastMonth) {
                if ("BUG".equals(t.getType())) bug++;
                else if ("FEATURE_REQUEST".equals(t.getType())) feature++;
                else if ("UI_FEEDBACK".equals(t.getType())) ui++;
            }

            double performanceScore;
            if (closedTickets == 0) {
                performanceScore = 0.0;
            } else {
                performanceScore = computePerformanceScore(dev.getSeniority(), closedTickets, highPriorityTickets, avgResolutionTime, bug, feature, ui);
            }


            // IMPORTANT: update pentru comanda search (performanceScoreAbove/Below)
            dev.setPerformanceScore(round2(performanceScore));

            ObjectNode dn = mapper.createObjectNode();
            dn.put("username", dev.getUsername());
            dn.put("closedTickets", closedTickets);
            dn.put("averageResolutionTime", round2(avgResolutionTime));
            dn.put("performanceScore", round2(performanceScore));
            dn.put("seniority", dev.getSeniority().toString());

            reportArr.add(dn);
        }

        res.set("report", reportArr);
        outputs.add(res);
    }

    private int resolutionDaysAssignedToSolved(Ticket t) {
        if (t.getAssignedAt() == null || t.getAssignedAt().isEmpty()) return 0;
        if (t.getSolvedAt() == null || t.getSolvedAt().isEmpty()) return 0;

        LocalDate assigned = LocalDate.parse(t.getAssignedAt());
        LocalDate solved = LocalDate.parse(t.getSolvedAt());
        return (int) ChronoUnit.DAYS.between(assigned, solved) + 1;
    }

    private double computePerformanceScore(Seniority s,
                                           int closedTickets,
                                           int highPriorityTickets,
                                           double avgResolutionTime,
                                           int bugTickets, int featureTickets, int uiTickets) {

        // IMPORTANT: conform testelor, daca nu ai inchis nimic => scor 0, fara bonus.
        if (closedTickets <= 0) {
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

        // SENIOR
        return Math.max(0.0, 0.5 * closedTickets + 1.0 * highPriorityTickets - 0.5 * avgResolutionTime) + bonus;
    }


    private static double averageResolvedTicketType(int bug, int feature, int ui) {
        return (bug + feature + ui) / 3.0;
    }

    private static double standardDeviation(int bug, int feature, int ui) {
        double mean = averageResolvedTicketType(bug, feature, ui);
        double variance = (Math.pow(bug - mean, 2) + Math.pow(feature - mean, 2) + Math.pow(ui - mean, 2)) / 3.0;
        return Math.sqrt(variance);
    }

    private static double ticketDiversityFactor(int bug, int feature, int ui) {
        double mean = averageResolvedTicketType(bug, feature, ui);
        if (mean == 0.0) return 0.0;
        double std = standardDeviation(bug, feature, ui);
        return std / mean;
    }



    private LocalDate getClosedAtFromHistory(Ticket t) {
        LocalDate last = null;
        for (HistoryEntry h : t.getHistory()) {
            if (!"STATUS_CHANGED".equals(h.getAction())) continue;
            if (!"CLOSED".equals(h.getTo())) continue;

            LocalDate d = LocalDate.parse(h.getTimestamp());
            if (last == null || d.isAfter(last)) last = d;
        }
        return last;
    }


    private void handleAppStabilityReport(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".",
                    input.getTimestamp());
            return;
        }

        List<Ticket> open = system.getTickets().values().stream()
                .filter(t -> t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS)
                .collect(Collectors.toList());

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "appStabilityReport");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ObjectNode report = mapper.createObjectNode();

        report.put("totalOpenTickets", open.size());

        // openTicketsByType
        ObjectNode byType = mapper.createObjectNode();
        byType.put("BUG", open.stream().filter(t -> "BUG".equals(t.getType())).count());
        byType.put("FEATURE_REQUEST", open.stream().filter(t -> "FEATURE_REQUEST".equals(t.getType())).count());
        byType.put("UI_FEEDBACK", open.stream().filter(t -> "UI_FEEDBACK".equals(t.getType())).count());
        report.set("openTicketsByType", byType);

        // openTicketsByPriority
        ObjectNode byPriority = mapper.createObjectNode();
        byPriority.put("LOW", open.stream().filter(t -> t.getBusinessPriority() == Priority.LOW).count());
        byPriority.put("MEDIUM", open.stream().filter(t -> t.getBusinessPriority() == Priority.MEDIUM).count());
        byPriority.put("HIGH", open.stream().filter(t -> t.getBusinessPriority() == Priority.HIGH).count());
        byPriority.put("CRITICAL", open.stream().filter(t -> t.getBusinessPriority() == Priority.CRITICAL).count());
        report.set("openTicketsByPriority", byPriority);

        // riskByType (calificativ pe media scorurilor normalizate)
        ObjectNode riskByType = mapper.createObjectNode();
        String bugRisk = riskQualifier(avgRiskForType(open, "BUG"));
        String featureRisk = riskQualifier(avgRiskForType(open, "FEATURE_REQUEST"));
        String uiRisk = riskQualifier(avgRiskForType(open, "UI_FEEDBACK"));
        riskByType.put("BUG", bugRisk);
        riskByType.put("FEATURE_REQUEST", featureRisk);
        riskByType.put("UI_FEEDBACK", uiRisk);
        report.set("riskByType", riskByType);

        // impactByType (media scorurilor normalizate, rotunjita 2 zecimale)
        ObjectNode impactByType = mapper.createObjectNode();
        impactByType.put("BUG", calculateAverageImpact(open, "BUG"));
        impactByType.put("FEATURE_REQUEST", calculateAverageImpact(open, "FEATURE_REQUEST"));
        impactByType.put("UI_FEEDBACK", calculateAverageImpact(open, "UI_FEEDBACK"));
        report.set("impactByType", impactByType);

        // appStability rules (conform enunt)
        String stability = computeStability(open, bugRisk, featureRisk, uiRisk, impactByType);
        report.put("appStability", stability);

        res.set("report", report);
        outputs.add(res);

        // daca e STABLE, aplicatia se incheie => nu mai procesezi comenzi
        if ("STABLE".equals(stability)) {
            system.setInvestorsLost(true); // reuse flag ca "stop processing"
        }
    }

    private String computeStability(List<Ticket> open,
                                    String bugRisk, String featureRisk, String uiRisk,
                                    ObjectNode impactByType) {

        // 1) daca nu exista OPEN / IN_PROGRESS => STABLE
        if (open.isEmpty()) return "STABLE";

        // 3) daca exista cel putin o categorie SIGNIFICANT => UNSTABLE
        if ("SIGNIFICANT".equals(bugRisk) || "SIGNIFICANT".equals(featureRisk) || "SIGNIFICANT".equals(uiRisk)) {
            return "UNSTABLE";
        }

        // 2) daca toate riscurile sunt NEGLIGIBLE si toate impacturile < 50 => STABLE
        boolean allNegligible = "NEGLIGIBLE".equals(bugRisk) && "NEGLIGIBLE".equals(featureRisk) && "NEGLIGIBLE".equals(uiRisk);

        double bugImpact = impactByType.get("BUG").asDouble();
        double featureImpact = impactByType.get("FEATURE_REQUEST").asDouble();
        double uiImpact = impactByType.get("UI_FEEDBACK").asDouble();
        boolean allImpactBelow50 = bugImpact < 50.0 && featureImpact < 50.0 && uiImpact < 50.0;

        if (allNegligible && allImpactBelow50) return "STABLE";

        // 4) altfel => PARTIALLY STABLE
        return "PARTIALLY STABLE";
    }


    private double avgEfficiencyForType(List<Ticket> tickets, String type) {
        List<Double> vals = new ArrayList<>();
        for (Ticket t : tickets) {
            if (type.equals(t.getType())) {
                vals.add(efficiencyScoreNormalized(t));
            }
        }
        if (vals.isEmpty()) return 0.0;
        return vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private int daysToResolve(Ticket t) {
        if (t.getAssignedAt() == null || t.getAssignedAt().isEmpty()) return 0;

        LocalDate assigned = LocalDate.parse(t.getAssignedAt());
        LocalDate end = getLastResolvedOrClosedDateFromHistory(t);

        if (end == null) return 0;

        return (int) ChronoUnit.DAYS.between(assigned, end) + 1;
    }

    private LocalDate getLastResolvedOrClosedDateFromHistory(Ticket t) {
        LocalDate last = null;

        for (HistoryEntry h : t.getHistory()) {
            if (!"STATUS_CHANGED".equals(h.getAction())) continue;
            String to = h.getTo();
            if (!"RESOLVED".equals(to) && !"CLOSED".equals(to)) continue;

            LocalDate d = LocalDate.parse(h.getTimestamp());
            if (last == null || d.isAfter(last)) last = d;
        }

        // fallback: daca nu exista history, incearca solvedAt (daca ai)
        if (last == null && t.getSolvedAt() != null && !t.getSolvedAt().isEmpty()) {
            last = LocalDate.parse(t.getSolvedAt());
        }

        return last;
    }

    private double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }



    private double efficiencyScoreNormalized(Ticket t) {
        // daysToResolve = zile intre assignedAt si ultima data cand a devenit RESOLVED sau CLOSED
        int days = daysToResolve(t);
        if (days <= 0) return 0.0;

        double base = 0.0;
        double max = 1.0;

        switch (t.getType()) {
            case "BUG": {
                BugTicket bt = (BugTicket) t;
                double freq = getFrequencyValue(bt.getFrequency());   // 1..4
                double sev  = getSeverityValue(bt.getSeverity());     // 1..3

                base = (freq + sev) * 10.0 / days;
                max = 70.0; // conform enunt
                break;
            }

            case "FEATURE_REQUEST": {
                FeatureRequestTicket ft = (FeatureRequestTicket) t;
                double bv = getBusinessValue(ft.getBusinessValue());          // 1,3,6,10
                double cd = getCustomerDemandValue(ft.getCustomerDemand());   // 1,3,6,10

                base = (bv + cd) / days;
                max = 20.0;
                break;
            }

            case "UI_FEEDBACK": {
                UiFeedbackTicket ut = (UiFeedbackTicket) t;
                double usability = ut.getUsabilityScore();                   // 1..10
                double bv = getBusinessValue(ut.getBusinessValue());          // 1,3,6,10

                base = (usability + bv) / days;
                max = 20.0;
                break;
            }
        }

        return calculateImpactFinal(base, max); // 0..100
    }



    // --- METRICI (CUSTOMER IMPACT) ---

    private void handleGenerateResolutionEfficiencyReport(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".",
                    input.getTimestamp());
            return;
        }

        // eligibile: RESOLVED si CLOSED
        List<Ticket> eligible = system.getTickets().values().stream()
                .filter(t -> t.getStatus() == Status.RESOLVED || t.getStatus() == Status.CLOSED)
                .collect(Collectors.toList());

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generateResolutionEfficiencyReport");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ObjectNode report = mapper.createObjectNode();
        report.put("totalTickets", eligible.size());

        // ticketsByType
        ObjectNode byType = mapper.createObjectNode();
        byType.put("BUG", eligible.stream().filter(t -> "BUG".equals(t.getType())).count());
        byType.put("FEATURE_REQUEST", eligible.stream().filter(t -> "FEATURE_REQUEST".equals(t.getType())).count());
        byType.put("UI_FEEDBACK", eligible.stream().filter(t -> "UI_FEEDBACK".equals(t.getType())).count());
        report.set("ticketsByType", byType);

        // ticketsByPriority
        ObjectNode byPriority = mapper.createObjectNode();
        byPriority.put("LOW", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.LOW).count());
        byPriority.put("MEDIUM", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.MEDIUM).count());
        byPriority.put("HIGH", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.HIGH).count());
        byPriority.put("CRITICAL", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.CRITICAL).count());
        report.set("ticketsByPriority", byPriority);

        // efficiencyByType (average normalized)
        ObjectNode efficiencyByType = mapper.createObjectNode();
        efficiencyByType.put("BUG", round2(avgEfficiencyForType(eligible, "BUG")));
        efficiencyByType.put("FEATURE_REQUEST", round2(avgEfficiencyForType(eligible, "FEATURE_REQUEST")));
        efficiencyByType.put("UI_FEEDBACK", round2(avgEfficiencyForType(eligible, "UI_FEEDBACK")));
        report.set("efficiencyByType", efficiencyByType);

        res.set("report", report);
        outputs.add(res);
    }


    private void handleGenerateCustomerImpactReport(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole(), input.getTimestamp());
            return;
        }

        List<Ticket> eligibleTickets = system.getTickets().values().stream()
                .filter(t -> t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS)
                .collect(Collectors.toList());

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generateCustomerImpactReport");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ObjectNode report = mapper.createObjectNode();
        report.put("totalTickets", eligibleTickets.size());

        // Count by Type
        ObjectNode byType = mapper.createObjectNode();
        byType.put("BUG", eligibleTickets.stream().filter(t -> "BUG".equals(t.getType())).count());
        byType.put("FEATURE_REQUEST", eligibleTickets.stream().filter(t -> "FEATURE_REQUEST".equals(t.getType())).count());
        byType.put("UI_FEEDBACK", eligibleTickets.stream().filter(t -> "UI_FEEDBACK".equals(t.getType())).count());
        report.set("ticketsByType", byType);

        // Count by Priority
        ObjectNode byPriority = mapper.createObjectNode();
        byPriority.put("LOW", eligibleTickets.stream().filter(t -> t.getBusinessPriority() == Priority.LOW).count());
        byPriority.put("MEDIUM", eligibleTickets.stream().filter(t -> t.getBusinessPriority() == Priority.MEDIUM).count());
        byPriority.put("HIGH", eligibleTickets.stream().filter(t -> t.getBusinessPriority() == Priority.HIGH).count());
        byPriority.put("CRITICAL", eligibleTickets.stream().filter(t -> t.getBusinessPriority() == Priority.CRITICAL).count());
        report.set("ticketsByPriority", byPriority);

        // Customer Impact Calculation
        ObjectNode impactByType = mapper.createObjectNode();
        impactByType.put("BUG", calculateAverageImpact(eligibleTickets, "BUG"));
        impactByType.put("FEATURE_REQUEST", calculateAverageImpact(eligibleTickets, "FEATURE_REQUEST"));
        impactByType.put("UI_FEEDBACK", calculateAverageImpact(eligibleTickets, "UI_FEEDBACK"));
        report.set("customerImpactByType", impactByType);

        res.set("report", report);
        outputs.add(res);
    }

    private double calculateAverageImpact(List<Ticket> allTickets, String type) {
        List<Double> scores = new ArrayList<>();
        for (Ticket t : allTickets) {
            if (t.getType().equals(type)) {
                scores.add(calculateImpactScore(t));
            }
        }
        if (scores.isEmpty()) return 0.0;

        // Medie conform cerință
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        // Rotunjire la 2 zecimale pentru output
        return Math.round(avg * 100.0) / 100.0;
    }

    private double calculateImpactScore(Ticket t) {
        double baseScore = 0;
        double maxScore = 1;

        switch (t.getType()) {
            case "BUG": {
                BugTicket bt = (BugTicket) t;
                double severity = getSeverityValue(bt.getSeverity());       // 1..3
                double frequency = getFrequencyValue(bt.getFrequency());    // 1..4
                double priority = getPriorityValue(bt.getBusinessPriority());// 1..4

                baseScore = frequency * priority * severity; // conform enunț
                maxScore = 4 * 4 * 3; // 48
                break;
            }

            case "FEATURE_REQUEST": {
                FeatureRequestTicket ft = (FeatureRequestTicket) t;
                double businessVal = getBusinessValue(ft.getBusinessValue());       // 1,3,6,10
                double demand = getCustomerDemandValue(ft.getCustomerDemand());     // 1,3,6,10

                baseScore = businessVal * demand; // conform enunț
                maxScore = 10 * 10; // 100
                break;
            }

            case "UI_FEEDBACK": {
                UiFeedbackTicket ut = (UiFeedbackTicket) t;
                double businessVal = getBusinessValue(ut.getBusinessValue()); // 1,3,6,10
                double usability = ut.getUsabilityScore();                    // 1..10

                baseScore = businessVal * usability; // conform enunț
                maxScore = 10 * 10; // 100
                break;
            }
        }

        return calculateImpactFinal(baseScore, maxScore);
    }



    private double calculateImpactFinal(double baseScore, double maxValue) {
        if (maxValue == 0) return 0.0;
        return Math.min(100.0, (baseScore * 100.0) / maxValue);
    }

    // --- CONFIGURATION VALUES (CONFORM CERINȚEI "Metrici de Stabilitate") ---

    // Severity: MINOR=1, MODERATE=2, SEVERE=3
    private double getSeverityValue(Severity s) {
        if (s == Severity.MINOR) return 1;
        if (s == Severity.MODERATE) return 2;
        if (s == Severity.SEVERE) return 3;
        return 0;
    }
    private double getMaxSeverityValue() { return 3; }

    // Frequency: RARE=1, OCCASIONAL=2, FREQUENT=3, ALWAYS=4
    private double getFrequencyValue(Frequency f) {
        if (f == Frequency.RARE) return 1;
        if (f == Frequency.OCCASIONAL) return 2;
        if (f == Frequency.FREQUENT) return 3;
        if (f == Frequency.ALWAYS) return 4;
        return 0;
    }
    private double getMaxFrequencyValue() { return 4; }

    // Priority: LOW=1, MEDIUM=2, HIGH=3, CRITICAL=4
    private double getPriorityValue(Priority p) {
        if (p == Priority.LOW) return 1;
        if (p == Priority.MEDIUM) return 2;
        if (p == Priority.HIGH) return 3;
        if (p == Priority.CRITICAL) return 4;
        return 0;
    }
    private double getMaxPriorityValue() { return 4; }

    // BusinessValue: S=1, M=3, L=6, XL=10
    private double getBusinessValue(BusinessValue s) {
        if (s == BusinessValue.S) return 1;
        if (s == BusinessValue.M) return 3;
        if (s == BusinessValue.L) return 6;
        if (s == BusinessValue.XL) return 10;
        return 0;
    }
    private double getMaxBusinessValue() { return 10; }

    // CustomerDemand: LOW=1, MEDIUM=3, HIGH=6, VERY_HIGH=10
    private double getCustomerDemandValue(CustomerDemand s) {
        if (s == CustomerDemand.LOW) return 1;
        if (s == CustomerDemand.MEDIUM) return 3;
        if (s == CustomerDemand.HIGH) return 6;
        if (s == CustomerDemand.VERY_HIGH) return 10;
        return 0;
    }
    private double getMaxCustomerDemandValue() { return 10; }

    // --- (RESTUL METODELOR) ---

    // --- SEARCH ---
    private void handleSearch(CommandInput input, User user, List<ObjectNode> outputs) {
        JsonNode filters = input.getFilters();
        String searchType = filters != null && filters.has("searchType") ? filters.get("searchType").asText() : "TICKET";

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "search");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        res.put("searchType", searchType);

        ArrayNode resultsArr = mapper.createArrayNode();

        if ("DEVELOPER".equals(searchType)) {
            if (user.getRole() == Role.MANAGER) {
                List<Developer> devs = searchDevelopers((Manager) user, filters);
                for (Developer d : devs) {
                    ObjectNode dn = mapper.createObjectNode();
                    dn.put("username", d.getUsername());
                    dn.put("expertiseArea", d.getExpertiseArea().toString());
                    dn.put("seniority", d.getSeniority().toString());
                    dn.put("performanceScore", d.getPerformanceScore());
                    dn.put("hireDate", d.getHireDate());
                    resultsArr.add(dn);
                }
            }
        } else {
            List<Ticket> tickets = searchTickets(user, filters);
            for (Ticket t : tickets) {
                ObjectNode tn = mapper.createObjectNode();
                tn.put("id", t.getId());
                tn.put("type", t.getType());
                tn.put("title", t.getTitle());
                tn.put("businessPriority", t.getBusinessPriority().toString());
                tn.put("status", t.getStatus().toString());
                tn.put("createdAt", t.getCreatedAt());
                tn.put("solvedAt", t.getSolvedAt() == null ? "" : t.getSolvedAt());
                tn.put("reportedBy", t.getReportedBy());

                if (filters != null && filters.has("keywords")) {
                    List<String> keywords = new ArrayList<>();
                    filters.get("keywords").forEach(k -> keywords.add(k.asText()));
                    List<String> matched = getMatchingWords(t, keywords);
                    if (!matched.isEmpty()) {
                        tn.set("matchingWords", mapper.valueToTree(matched));
                    }
                }
                resultsArr.add(tn);
            }
        }
        // DOAR pentru DEVELOPER search
        if ("DEVELOPER".equals(searchType)) {
            if (resultsArr.isEmpty()) {
                return;
            }
        }
        res.set("results", resultsArr);
        outputs.add(res);
    }

    private List<Developer> searchDevelopers(Manager manager, JsonNode filters) {
        if (manager.getSubordinates() == null) return new ArrayList<>();

        List<Developer> result = new ArrayList<>();
        for (String subUsername : manager.getSubordinates()) {
            User u = system.getUsers().get(subUsername);
            if (u instanceof Developer) result.add((Developer) u);
        }

        if (filters == null) {
            result.sort(Comparator.comparing(Developer::getUsername));
            return result;
        }

        result = result.stream().filter(dev -> {
            if (filters.has("expertiseArea") &&
                    !dev.getExpertiseArea().toString().equals(filters.get("expertiseArea").asText()))
                return false;

            if (filters.has("seniority") &&
                    !dev.getSeniority().toString().equals(filters.get("seniority").asText()))
                return false;

            // IMPORTANT: Above = >= ; Below = <=
            if (filters.has("performanceScoreAbove")) {
                double v = filters.get("performanceScoreAbove").asDouble();
                if (dev.getPerformanceScore() < v) return false;
            }

            if (filters.has("performanceScoreBelow")) {
                double v = filters.get("performanceScoreBelow").asDouble();
                if (dev.getPerformanceScore() > v) return false;
            }

            return true;
        }).collect(Collectors.toList());

        result.sort(Comparator.comparing(Developer::getUsername));
        return result;
    }


    private List<Ticket> searchTickets(User user, JsonNode filters) {
        List<Ticket> scope = new ArrayList<>();
        if (user.getRole() == Role.MANAGER) {
            scope = new ArrayList<>(system.getTickets().values());
        } else if (user.getRole() == Role.DEVELOPER) {
            List<Milestone> devMilestones = system.getMilestones().stream().filter(m -> m.getAssignedDevs().contains(user.getUsername())).collect(Collectors.toList());
            Set<Integer> validIds = new HashSet<>();
            for (Milestone m : devMilestones) validIds.addAll(m.getTickets());
            for (Ticket t : system.getTickets().values()) {
                if (validIds.contains(t.getId()) && t.getStatus() == Status.OPEN) scope.add(t);
            }
        } else return new ArrayList<>();

        if (filters == null) {
            scope.sort(Comparator.comparing(Ticket::getCreatedAt).thenComparingInt(Ticket::getId));
            return scope;
        }
        return scope.stream().filter(t -> {
            if (filters.has("businessPriority") && !t.getBusinessPriority().toString().equals(filters.get("businessPriority").asText())) return false;
            if (filters.has("type") && !t.getType().equals(filters.get("type").asText())) return false;
            if (filters.has("createdAfter")) {
                LocalDate tDate = LocalDate.parse(t.getCreatedAt());
                LocalDate filterDate = LocalDate.parse(filters.get("createdAfter").asText());
                if (!tDate.isAfter(filterDate)) return false;
            }
            if (filters.has("createdBefore")) {
                LocalDate tDate = LocalDate.parse(t.getCreatedAt());
                LocalDate filterDate = LocalDate.parse(filters.get("createdBefore").asText());
                if (!tDate.isBefore(filterDate)) return false;
            }
            if (filters.has("keywords")) {
                List<String> keywords = new ArrayList<>();
                filters.get("keywords").forEach(k -> keywords.add(k.asText()));
                if (getMatchingWords(t, keywords).isEmpty()) return false;
            }
            if (filters.has("availableForAssignment") && filters.get("availableForAssignment").asBoolean()) {
                if (user.getRole() != Role.DEVELOPER) return false;
                if (t.getStatus() != Status.OPEN) return false;
                Optional<Milestone> mOpt = system.getMilestones().stream().filter(m -> m.getTickets().contains(t.getId())).findFirst();
                if (mOpt.isEmpty() || system.isMilestoneBlocked(mOpt.get())) return false;
                if (!system.canAccess((Developer) user, t)) return false;
            }
            return true;
        }).sorted(Comparator.comparing(Ticket::getCreatedAt).thenComparingInt(Ticket::getId)).collect(Collectors.toList());
    }

    private List<String> getMatchingWords(Ticket t, List<String> keywords) {
        Set<String> matched = new HashSet<>();
        String text = (t.getTitle() + " " + (t.getDescription() == null ? "" : t.getDescription())).toLowerCase();
        for (String k : keywords) {
            if (text.contains(k.toLowerCase())) matched.add(k);
        }
        List<String> result = new ArrayList<>(matched);
        Collections.sort(result);
        return result;
    }

    // --- NOTIFICATIONS ---
    private void handleViewNotifications(CommandInput input, User user, List<ObjectNode> outputs) {
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewNotifications");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode notifs = mapper.valueToTree(user.getNotifications());
        res.set("notifications", notifs);
        user.clearNotifications();
        outputs.add(res);
    }

    private void handleGenerateTicketRiskReport(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".",
                    input.getTimestamp());
            return;
        }

        List<Ticket> eligible = system.getTickets().values().stream()
                .filter(t -> t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS)
                .collect(Collectors.toList());

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generateTicketRiskReport");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ObjectNode report = mapper.createObjectNode();
        report.put("totalTickets", eligible.size());

        // ticketsByType
        ObjectNode byType = mapper.createObjectNode();
        byType.put("BUG", eligible.stream().filter(t -> "BUG".equals(t.getType())).count());
        byType.put("FEATURE_REQUEST", eligible.stream().filter(t -> "FEATURE_REQUEST".equals(t.getType())).count());
        byType.put("UI_FEEDBACK", eligible.stream().filter(t -> "UI_FEEDBACK".equals(t.getType())).count());
        report.set("ticketsByType", byType);

        // ticketsByPriority
        ObjectNode byPriority = mapper.createObjectNode();
        byPriority.put("LOW", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.LOW).count());
        byPriority.put("MEDIUM", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.MEDIUM).count());
        byPriority.put("HIGH", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.HIGH).count());
        byPriority.put("CRITICAL", eligible.stream().filter(t -> t.getBusinessPriority() == Priority.CRITICAL).count());
        report.set("ticketsByPriority", byPriority);

        // riskByType (average normalized score -> qualifier)
        ObjectNode riskByType = mapper.createObjectNode();
        riskByType.put("BUG", riskQualifier(avgRiskForType(eligible, "BUG")));
        riskByType.put("FEATURE_REQUEST", riskQualifier(avgRiskForType(eligible, "FEATURE_REQUEST")));
        riskByType.put("UI_FEEDBACK", riskQualifier(avgRiskForType(eligible, "UI_FEEDBACK")));
        report.set("riskByType", riskByType);

        res.set("report", report);
        outputs.add(res);
    }

    private double avgRiskForType(List<Ticket> tickets, String type) {
        List<Double> scores = new ArrayList<>();
        for (Ticket t : tickets) {
            if (type.equals(t.getType())) {
                scores.add(riskScoreNormalized(t));
            }
        }
        if (scores.isEmpty()) return 0.0;
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double riskScoreNormalized(Ticket t) {
        double base = 0;
        double max = 1;

        switch (t.getType()) {
            case "BUG": {
                BugTicket bt = (BugTicket) t;
                base = getFrequencyValue(bt.getFrequency()) * getSeverityValue(bt.getSeverity());
                max = 12.0; // 4 * 3
                break;
            }
            case "FEATURE_REQUEST": {
                FeatureRequestTicket ft = (FeatureRequestTicket) t;
                base = getBusinessValue(ft.getBusinessValue()) + getCustomerDemandValue(ft.getCustomerDemand());
                max = 20.0; // 10 + 10
                break;
            }
            case "UI_FEEDBACK": {
                UiFeedbackTicket ut = (UiFeedbackTicket) t;
                base = (11.0 - ut.getUsabilityScore()) * getBusinessValue(ut.getBusinessValue());
                max = 100.0; // (11-1)*10 = 100
                break;
            }
        }

        return calculateImpactFinal(base, max); // aceeași normalizare 0..100
    }

    private String riskQualifier(double riskAvg) {
        if (riskAvg <= 24.0) return "NEGLIGIBLE";
        if (riskAvg <= 49.0) return "MODERATE";
        if (riskAvg <= 74.0) return "SIGNIFICANT";
        return "MAJOR";
    }


    // --- COMMENTS ---
    private void handleAddComment(CommandInput input, User user, List<ObjectNode> outputs) {
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;

        if (ticket.getReportedBy().isEmpty()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Comments are not allowed on anonymous tickets.", input.getTimestamp());
            return;
        }

        if (user.getRole() == Role.REPORTER && ticket.getStatus() == Status.CLOSED) {
            addError(outputs, input.getCommand(), input.getUsername(), "Reporters cannot comment on CLOSED tickets.", input.getTimestamp());
            return;
        }

        if (input.getComment() == null || input.getComment().length() < 10) {
            addError(outputs, input.getCommand(), input.getUsername(), "Comment must be at least 10 characters long.", input.getTimestamp());
            return;
        }

        if (user.getRole() == Role.REPORTER) {
            if (!ticket.getReportedBy().equals(user.getUsername())) {
                addError(outputs, input.getCommand(), input.getUsername(), "Reporter " + user.getUsername() + " cannot comment on ticket " + ticketId + ".", input.getTimestamp());
                return;
            }
        } else if (user.getRole() == Role.DEVELOPER) {
            if (ticket.getAssignedTo() != null && !ticket.getAssignedTo().equals(user.getUsername())) {
                addError(outputs, input.getCommand(), input.getUsername(), "Ticket " + ticketId + " is not assigned to the developer " + user.getUsername() + ".", input.getTimestamp());
                return;
            }
        }

        Comment c = new Comment(user.getUsername(), input.getComment(), input.getTimestamp());
        ticket.getComments().add(c);
    }

    private void handleUndoAddComment(CommandInput input, User user, List<ObjectNode> outputs) {
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;
        if (ticket.getReportedBy().isEmpty()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Comments are not allowed on anonymous tickets.", input.getTimestamp());
            return;
        }
        if (user.getRole() == Role.REPORTER && !ticket.getReportedBy().equals(user.getUsername())) {
            addError(outputs, input.getCommand(), input.getUsername(), "Reporter " + user.getUsername() + " cannot comment on ticket " + ticketId + ".", input.getTimestamp());
            return;
        }
        List<Comment> comments = ticket.getComments();
        for (int i = comments.size() - 1; i >= 0; i--) {
            if (comments.get(i).getAuthor().equals(user.getUsername())) {
                comments.remove(i);
                return;
            }
        }
    }

    // --- OTHER HANDLERS ---

    private void handleViewMilestones(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Milestone> visible = new ArrayList<>();
        if (user.getRole() == Role.MANAGER) {
            visible = system.getMilestones().stream()
                    .filter(m -> m.getCreatedBy().equals(user.getUsername()))
                    .collect(Collectors.toList());
        } else if (user.getRole() == Role.DEVELOPER) {
            visible = system.getMilestones().stream()
                    .filter(m -> m.getAssignedDevs().contains(user.getUsername()))
                    .collect(Collectors.toList());
        }

        visible.sort(Comparator.comparing(Milestone::getDueDate).thenComparing(Milestone::getName));

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewMilestones");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ArrayNode arr = mapper.createArrayNode();
        LocalDate now = LocalDate.parse(input.getTimestamp());

        for (Milestone m : visible) {
            ObjectNode mn = mapper.createObjectNode();
            mn.put("name", m.getName());
            mn.set("blockingFor", mapper.valueToTree(m.getBlockingFor()));
            mn.put("dueDate", m.getDueDate());
            mn.put("createdAt", m.getCreatedAt());
            mn.set("tickets", mapper.valueToTree(m.getTickets()));
            mn.set("assignedDevs", mapper.valueToTree(m.getAssignedDevs()));
            mn.put("createdBy", m.getCreatedBy());

            boolean allClosed = true;
            List<Integer> openTickets = new ArrayList<>();
            List<Integer> closedTickets = new ArrayList<>();

            // IMPORTANT: milestone completion date = data când ultimul ticket a devenit CLOSED
            LocalDate lastClosedDate = null;

            for (Integer id : m.getTickets()) {
                Ticket t = system.getTickets().get(id);
                if (t == null) continue;

                if (t.getStatus() != Status.CLOSED) {
                    allClosed = false;
                    openTickets.add(id);
                } else {
                    closedTickets.add(id);

                    LocalDate closedAt = getClosedAtFromHistory(t); // ia data din history pe STATUS_CHANGED -> CLOSED
                    if (closedAt == null && t.getSolvedAt() != null && !t.getSolvedAt().isEmpty()) {
                        // fallback defensiv (dar în teste, CLOSED ar trebui să aibă history)
                        closedAt = LocalDate.parse(t.getSolvedAt());
                    }
                    if (closedAt != null && (lastClosedDate == null || closedAt.isAfter(lastClosedDate))) {
                        lastClosedDate = closedAt;
                    }
                }
            }

            mn.put("status", (allClosed && !m.getTickets().isEmpty()) ? "COMPLETED" : "ACTIVE");

            boolean isBlocked = system.isMilestoneBlocked(m);
            mn.put("isBlocked", isBlocked);

            LocalDate due = LocalDate.parse(m.getDueDate());

            if (allClosed && lastClosedDate != null) {
                // COMPLETED: overdueBy = max(0, (lastClosedDate - due) + 1)
                long overdue = ChronoUnit.DAYS.between(due, lastClosedDate) + 1;
                mn.put("daysUntilDue", 0);
                mn.put("overdueBy", Math.max(0, overdue));
            } else {
                // ACTIVE: daysUntilDue / overdueBy față de "now"
                long diff = ChronoUnit.DAYS.between(now, due);
                if (diff < 0) {
                    mn.put("daysUntilDue", 0);
                    mn.put("overdueBy", Math.abs(diff) + 1);
                } else {
                    mn.put("daysUntilDue", diff + 1);
                    mn.put("overdueBy", 0);
                }
            }

            mn.set("openTickets", mapper.valueToTree(openTickets));
            mn.set("closedTickets", mapper.valueToTree(closedTickets));

            double ratio = m.getTickets().isEmpty() ? 0.0 : ((double) closedTickets.size() / m.getTickets().size());
            mn.put("completionPercentage", Math.round(ratio * 100.0) / 100.0);

            ArrayNode rep = mapper.createArrayNode();
            if (m.getAssignedDevs() != null) {
                for (String dev : m.getAssignedDevs()) {
                    ObjectNode dn = mapper.createObjectNode();
                    dn.put("developer", dev);

                    List<Integer> tids = new ArrayList<>();
                    for (Integer id : m.getTickets()) {
                        Ticket t = system.getTickets().get(id);
                        if (t != null && dev.equals(t.getAssignedTo())) {
                            tids.add(id);
                        }
                    }
                    dn.set("assignedTickets", mapper.valueToTree(tids));
                    rep.add(dn);
                }
            }
            mn.set("repartition", rep);

            arr.add(mn);
        }

        res.set("milestones", arr);
        outputs.add(res);
    }


    private void handleChangeStatus(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.DEVELOPER) return;

        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;

        if (ticket.getAssignedTo() == null || !user.getUsername().equals(ticket.getAssignedTo())) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "Ticket " + ticketId + " is not assigned to developer " + user.getUsername() + ".",
                    input.getTimestamp());
            return;
        }

        Status oldStatus = ticket.getStatus();
        Status newStatus;

        if (oldStatus == Status.IN_PROGRESS) {
            newStatus = Status.RESOLVED;
            ticket.setSolvedAt(input.getTimestamp()); // se seteaza doar aici
        } else if (oldStatus == Status.RESOLVED) {
            newStatus = Status.CLOSED;
            // IMPORTANT: nu modifici solvedAt la inchidere
        } else {
            return; // OPEN / CLOSED -> ignorat
        }

        ticket.setStatus(newStatus);

        HistoryEntry entry = new HistoryEntry();
        entry.setAction("STATUS_CHANGED");
        entry.setFrom(oldStatus.toString());
        entry.setTo(newStatus.toString());
        entry.setBy(user.getUsername());
        entry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(entry);
    }

    private void handleUndoChangeStatus(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.DEVELOPER) return;

        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;

        if (ticket.getAssignedTo() == null || !user.getUsername().equals(ticket.getAssignedTo())) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "Ticket " + ticketId + " is not assigned to developer " + user.getUsername() + ".",
                    input.getTimestamp());
            return;
        }

        Status oldStatus = ticket.getStatus();

        // conform cerintei: daca e IN_PROGRESS, undo e ignorat
        if (oldStatus == Status.IN_PROGRESS) return;

        Status newStatus;
        if (oldStatus == Status.CLOSED) {
            newStatus = Status.RESOLVED;
            // IMPORTANT: NU atingi solvedAt
        } else if (oldStatus == Status.RESOLVED) {
            newStatus = Status.IN_PROGRESS;
            // nu mai e rezolvat -> solvedAt trebuie golit
            ticket.setSolvedAt(null);
        } else {
            return; // OPEN sau orice altceva -> ignorat
        }

        ticket.setStatus(newStatus);

        HistoryEntry entry = new HistoryEntry();
        entry.setAction("STATUS_CHANGED");
        entry.setFrom(oldStatus.toString());
        entry.setTo(newStatus.toString());
        entry.setBy(user.getUsername());
        entry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(entry);
    }


    private void handleViewTicketHistory(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> userTickets = system.getTickets().values().stream()
                .filter(t -> user.getUsername().equals(t.getAssignedTo()))
                .sorted(Comparator.comparingInt(Ticket::getId))
                .collect(Collectors.toList());

        if (user.getRole() == Role.DEVELOPER) {
            List<Ticket> historical = system.getTickets().values().stream()
                    .filter(t -> !userTickets.contains(t) && t.getHistory().stream()
                            .anyMatch(h -> "ASSIGNED".equals(h.getAction()) && user.getUsername().equals(h.getBy())))
                    .sorted(Comparator.comparingInt(Ticket::getId))
                    .collect(Collectors.toList());
            userTickets.addAll(historical);
            userTickets.sort(Comparator.comparingInt(Ticket::getId));
        }

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewTicketHistory");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ArrayNode historyArr = mapper.createArrayNode();
        Set<String> allowedActions = new HashSet<>(Arrays.asList(
                "ASSIGNED", "DE-ASSIGNED", "STATUS_CHANGED", "ADDED_TO_MILESTONE", "REMOVED_FROM_DEV"
        ));

        for (Ticket t : userTickets) {
            ObjectNode tNode = mapper.createObjectNode();
            tNode.put("id", t.getId());
            tNode.put("title", t.getTitle());
            tNode.put("status", t.getStatus().toString());
            List<HistoryEntry> filteredHistory = t.getHistory().stream()
                    .filter(h -> allowedActions.contains(h.getAction()))
                    .collect(Collectors.toList());
            tNode.set("actions", mapper.valueToTree(filteredHistory));
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            historyArr.add(tNode);
        }
        res.set("ticketHistory", historyArr);
        outputs.add(res);
    }

    private void handleCreateMilestone(CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Milestones cannot be created during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole(), input.getTimestamp());
            return;
        }
        if (input.getTickets() != null) {
            for (Integer id : input.getTickets()) {
                Ticket t = system.getTickets().get(id);
                if (t == null) {
                    addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + id + " does not exist.", input.getTimestamp());
                    return;
                }
                Optional<Milestone> assignedMilestone = system.getMilestones().stream().filter(m -> m.getTickets().contains(id)).findFirst();
                if (assignedMilestone.isPresent()) {
                    addError(outputs, input.getCommand(), input.getUsername(), "Tickets " + id + " already assigned to milestone " + assignedMilestone.get().getName() + ".", input.getTimestamp());
                    return;
                }
            }
        }
        Milestone m = new Milestone();
        m.setName(input.getName());
        m.setDueDate(input.getDueDate());
        m.setCreatedBy(user.getUsername());
        m.setCreatedAt(input.getTimestamp());
        if (input.getTickets() != null) m.setTickets(input.getTickets());
        if (input.getAssignedDevs() != null) m.setAssignedDevs(input.getAssignedDevs());
        if (input.getBlockingFor() != null) m.setBlockingFor(input.getBlockingFor());
        if (m.getBlockingFor() != null) {
            for (String blockedName : m.getBlockingFor()) {
                Milestone blockedM = system.findMilestoneByName(blockedName);
                if (blockedM != null) blockedM.getDependsOn().add(m.getName());
            }
        }
        if (m.getTickets() != null) {
            for (Integer tid : m.getTickets()) {
                Ticket t = system.getTickets().get(tid);
                HistoryEntry h = new HistoryEntry();
                h.setAction("ADDED_TO_MILESTONE");
                h.setMilestone(m.getName());
                h.setBy(user.getUsername());
                h.setTimestamp(input.getTimestamp());
                t.addHistoryEntry(h);
            }
        }
        system.addMilestone(m);
        system.notifyDevs(m, "New milestone " + m.getName() + " has been created with due date " + m.getDueDate() + ".");
    }

    private void handleAssignTicket(CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Tickets cannot be assigned during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.DEVELOPER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role DEVELOPER; user role " + user.getRole(), input.getTimestamp());
            return;
        }
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + ticketId + " does not exist.", input.getTimestamp());
            return;
        }
        if (ticket.getStatus() != Status.OPEN) {
            addError(outputs, input.getCommand(), input.getUsername(), "Only OPEN tickets can be assigned.", input.getTimestamp());
            return;
        }
        Optional<Milestone> mOpt = system.getMilestones().stream().filter(m -> m.getTickets().contains(ticketId)).findFirst();
        if (mOpt.isEmpty()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + ticketId + " is not assigned to any milestone.", input.getTimestamp());
            return;
        }
        Milestone m = mOpt.get();
        if (!m.getAssignedDevs().contains(user.getUsername())) {
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + user.getUsername() + " is not assigned to milestone " + m.getName() + ".", input.getTimestamp());
            return;
        }
        if (system.isMilestoneBlocked(m)) {
            addError(outputs, input.getCommand(), input.getUsername(), "Cannot assign ticket " + ticketId + " from blocked milestone " + m.getName() + ".", input.getTimestamp());
            return;
        }
        Developer dev = (Developer) user;
        if (!isExpertiseMatch(dev, ticket)) {
            String required = getRequiredExpertiseString(ticket.getExpertiseArea());
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + dev.getUsername() + " cannot assign ticket " + ticketId + " due to expertise area. Required: " + required + "; Current: " + dev.getExpertiseArea() + ".", input.getTimestamp());
            return;
        }
        if (!isSeniorityMatch(dev, ticket)) {
            String required = getRequiredSeniorityString(ticket);
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + dev.getUsername() + " cannot assign ticket " + ticketId + " due to seniority level. Required: " + required + "; Current: " + dev.getSeniority() + ".", input.getTimestamp());
            return;
        }
        ticket.setAssignedTo(user.getUsername());
        ticket.setAssignedAt(input.getTimestamp());
        HistoryEntry assignEntry = new HistoryEntry();
        assignEntry.setAction("ASSIGNED");
        assignEntry.setBy(user.getUsername());
        assignEntry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(assignEntry);
        ticket.setStatus(Status.IN_PROGRESS);
        HistoryEntry statusEntry = new HistoryEntry();
        statusEntry.setAction("STATUS_CHANGED");
        statusEntry.setFrom("OPEN");
        statusEntry.setTo("IN_PROGRESS");
        statusEntry.setBy(user.getUsername());
        statusEntry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(statusEntry);
    }

    private void handleReportTicket(CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.getTestingPhaseStartDate() == null) {
            system.setTestingPhaseStartDate(input.getTimestamp());
            system.setTestingPhase(true);
        }
        if (!system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Tickets can only be reported during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.REPORTER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role REPORTER; user role " + user.getRole(), input.getTimestamp());
            return;
        }
        JsonNode params = input.getParams();
        String reportedBy = params.has("reportedBy") ? params.get("reportedBy").asText() : "";
        if (reportedBy.isEmpty()) {
            String type = params.has("type") ? params.get("type").asText() : "";
            if (!"BUG".equals(type)) {
                addError(outputs, input.getCommand(), input.getUsername(), "Anonymous reports are only allowed for tickets of type BUG.", input.getTimestamp());
                return;
            }
        }
        int id = system.getNextTicketId();
        Ticket ticket = TicketFactory.createTicket(params, id, input.getTimestamp());
        if (ticket.getReportedBy().isEmpty()) ticket.setBusinessPriority(Priority.LOW);
        system.getTickets().put(id, ticket);
    }

    private void handleViewTickets(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> visibleTickets = new ArrayList<>();
        List<Ticket> allTickets = system.getTickets().values().stream().collect(Collectors.toList());
        if (user.getRole() == Role.MANAGER) {
            visibleTickets = allTickets;
        } else if (user.getRole() == Role.REPORTER) {
            visibleTickets = allTickets.stream().filter(t -> t.getReportedBy().equals(user.getUsername())).collect(Collectors.toList());
        } else if (user.getRole() == Role.DEVELOPER) {
            List<Milestone> devMilestones = system.getMilestones().stream().filter(m -> m.getAssignedDevs().contains(user.getUsername())).collect(Collectors.toList());
            Set<Integer> visibleTicketIds = new HashSet<>();
            for (Milestone m : devMilestones) visibleTicketIds.addAll(m.getTickets());
            visibleTickets = allTickets.stream().filter(t -> visibleTicketIds.contains(t.getId()) && t.getStatus() == Status.OPEN).collect(Collectors.toList());
        }
        visibleTickets.sort(Comparator.comparing(Ticket::getCreatedAt).thenComparingInt(Ticket::getId));
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewTickets");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode ticketsArray = mapper.createArrayNode();
        for (Ticket t : visibleTickets) {
            ObjectNode tNode = mapper.createObjectNode();
            tNode.put("id", t.getId());
            tNode.put("type", t.getType());
            tNode.put("title", t.getTitle());
            tNode.put("businessPriority", t.getBusinessPriority() != null ? t.getBusinessPriority().toString() : null);
            tNode.put("status", t.getStatus() != null ? t.getStatus().toString() : null);
            tNode.put("createdAt", t.getCreatedAt());
            tNode.put("assignedAt", t.getAssignedAt() == null ? "" : t.getAssignedAt());
            tNode.put("solvedAt", t.getSolvedAt() == null ? "" : t.getSolvedAt());
            tNode.put("assignedTo", t.getAssignedTo() == null ? "" : t.getAssignedTo());
            tNode.put("reportedBy", t.getReportedBy() == null ? "" : t.getReportedBy());
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            ticketsArray.add(tNode);
        }
        res.set("tickets", ticketsArray);
        outputs.add(res);
    }

    private void handleViewAssignedTickets(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> assignedTickets = system.getTickets().values().stream()
                .filter(t -> user.getUsername().equals(t.getAssignedTo()))
                .collect(Collectors.toList());
        assignedTickets.sort((t1, t2) -> {
            int p1 = t1.getBusinessPriority().ordinal();
            int p2 = t2.getBusinessPriority().ordinal();
            if (p1 != p2) return Integer.compare(p2, p1);
            return Integer.compare(t1.getId(), t2.getId());
        });
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewAssignedTickets");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode arr = mapper.createArrayNode();
        for (Ticket t : assignedTickets) {
            ObjectNode tNode = mapper.createObjectNode();
            tNode.put("id", t.getId());
            tNode.put("type", t.getType());
            tNode.put("title", t.getTitle());
            tNode.put("businessPriority", t.getBusinessPriority().toString());
            tNode.put("status", t.getStatus().toString());
            tNode.put("createdAt", t.getCreatedAt());
            tNode.put("assignedAt", t.getAssignedAt());
            tNode.put("reportedBy", t.getReportedBy());
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            arr.add(tNode);
        }
        res.set("assignedTickets", arr);
        outputs.add(res);
    }



    private void handleUndoAssignTicket(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.DEVELOPER) return;
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null || !user.getUsername().equals(ticket.getAssignedTo())) return;
        ticket.setAssignedTo(null);
        ticket.setAssignedAt(null);
        ticket.setStatus(Status.OPEN);
        HistoryEntry entry = new HistoryEntry();
        entry.setAction("DE-ASSIGNED");
        entry.setBy(user.getUsername());
        entry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(entry);
    }

    private boolean isExpertiseMatch(Developer dev, Ticket ticket) {
        ExpertiseArea devExp = dev.getExpertiseArea();
        ExpertiseArea ticketExp = ticket.getExpertiseArea();
        if (devExp == ExpertiseArea.FULLSTACK) return true;
        if (devExp == ticketExp) return true;
        if (devExp == ExpertiseArea.BACKEND && (ticketExp == ExpertiseArea.DB || ticketExp == ExpertiseArea.BACKEND)) return true;
        if (devExp == ExpertiseArea.FRONTEND && (ticketExp == ExpertiseArea.DESIGN || ticketExp == ExpertiseArea.FRONTEND)) return true;
        return false;
    }

    private String getRequiredExpertiseString(ExpertiseArea ticketExp) {
        if (ticketExp == ExpertiseArea.DB || ticketExp == ExpertiseArea.BACKEND) return "BACKEND, DB, FULLSTACK";
        if (ticketExp == ExpertiseArea.DESIGN || ticketExp == ExpertiseArea.FRONTEND) return "DESIGN, FRONTEND, FULLSTACK";
        if (ticketExp == ExpertiseArea.DEVOPS) return "DEVOPS, FULLSTACK";
        return ticketExp + ", FULLSTACK";
    }

    private boolean isSeniorityMatch(Developer dev, Ticket ticket) {
        Priority p = ticket.getBusinessPriority();
        Seniority s = dev.getSeniority();
        if (p == Priority.CRITICAL) return s == Seniority.SENIOR;
        if (p == Priority.HIGH) return s == Seniority.MID || s == Seniority.SENIOR;
        return true;
    }

    private String getRequiredSeniorityString(Ticket ticket) {
        Priority p = ticket.getBusinessPriority();
        if (p == Priority.CRITICAL) return "SENIOR";
        if (p == Priority.HIGH) return "MID, SENIOR";
        return "JUNIOR, MID, SENIOR";
    }

    private void addError(List<ObjectNode> outputs, String command, String username, String message, String timestamp) {
        ObjectNode res = mapper.createObjectNode();
        res.put("command", command);
        res.put("username", username);
        res.put("timestamp", timestamp);
        res.put("error", message);
        outputs.add(res);
    }
}