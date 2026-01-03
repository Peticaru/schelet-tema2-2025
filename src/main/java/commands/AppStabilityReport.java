package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jshell.execution.Util;
import models.Priority;
import models.Status;
import models.Ticket;
import models.User;
import services.TicketSystem;
import utils.Utils;

import java.util.List;
import java.util.stream.Collectors;

public class AppStabilityReport extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
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

        ObjectNode riskByType = mapper.createObjectNode();
        String bugRisk = Utils.riskQualifier(Utils.avgRiskForType(open, "BUG"));
        String featureRisk = Utils.riskQualifier(Utils.avgRiskForType(open, "FEATURE_REQUEST"));
        String uiRisk = Utils.riskQualifier(Utils.avgRiskForType(open, "UI_FEEDBACK"));
        riskByType.put("BUG", bugRisk);
        riskByType.put("FEATURE_REQUEST", featureRisk);
        riskByType.put("UI_FEEDBACK", uiRisk);
        report.set("riskByType", riskByType);

        ObjectNode impactByType = mapper.createObjectNode();
        impactByType.put("BUG", Utils.calculateAverageImpact(open, "BUG"));
        impactByType.put("FEATURE_REQUEST", Utils.calculateAverageImpact(open, "FEATURE_REQUEST"));
        impactByType.put("UI_FEEDBACK", Utils.calculateAverageImpact(open, "UI_FEEDBACK"));
        report.set("impactByType", impactByType);

        // appStability rules (conform enunt)
        String stability = Utils.computeStability(open, bugRisk, featureRisk, uiRisk, impactByType);
        report.put("appStability", stability);

        res.set("report", report);
        outputs.add(res);

        if ("STABLE".equals(stability)) {
            system.setInvestorsLost(true);
        }
    }
}
