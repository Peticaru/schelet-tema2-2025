package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Status;
import models.Ticket;
import models.User;
import services.TicketSystem;
import utils.Utils;

import java.util.List;
import java.util.stream.Collectors;

public class GenerateCustomImpactReport extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> eligibleTickets = system.getTickets().values().stream()
                .filter(t -> t.getStatus() == Status.OPEN )
                .collect(Collectors.toList());

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generateCustomerImpactReport");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ObjectNode report = generateReport(eligibleTickets);

        // Customer Impact Calculation
        ObjectNode impactByType = mapper.createObjectNode();
        impactByType.put("BUG", Utils.calculateAverageImpact(eligibleTickets, "BUG"));
        impactByType.put("FEATURE_REQUEST", Utils.calculateAverageImpact(eligibleTickets, "FEATURE_REQUEST"));
        impactByType.put("UI_FEEDBACK", Utils.calculateAverageImpact(eligibleTickets, "UI_FEEDBACK"));
        report.set("customerImpactByType", impactByType);

        res.set("report", report);
        outputs.add(res);
    }
}
