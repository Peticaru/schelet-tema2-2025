package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Status;
import models.Ticket;
import models.User;
import services.TicketSystem;
import utils.Utils;

import java.util.List;
import java.util.stream.Collectors;

public class GenerateResolutionEfficiencyReport extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> eligibleTickets = system.getTickets().values().stream()
                .filter(t -> t.getStatus() == Status.RESOLVED || t.getStatus() == Status.CLOSED)
                .collect(Collectors.toList());

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generateResolutionEfficiencyReport");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ObjectNode report = generateReport(eligibleTickets);

        // efficiencyByType (average normalized)
        ObjectNode efficiencyByType = mapper.createObjectNode();
        efficiencyByType.put("BUG", round2(Utils.avgEfficiencyForType(eligibleTickets, "BUG")));
        efficiencyByType.put("FEATURE_REQUEST", round2(Utils.avgEfficiencyForType(eligibleTickets, "FEATURE_REQUEST")));
        efficiencyByType.put("UI_FEEDBACK", round2(Utils.avgEfficiencyForType(eligibleTickets, "UI_FEEDBACK")));
        report.set("efficiencyByType", efficiencyByType);

        res.set("report", report);
        outputs.add(res);
    }
}
