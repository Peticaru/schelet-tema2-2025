package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Status;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.List;
import java.util.stream.Collectors;

import static utils.Utils.avgRiskForType;
import static utils.Utils.riskQualifier;

public class GenerateTicketReport extends BaseCommand{
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {

        List<Ticket> eligibleTickets = system.getTickets().values().stream()
                .filter(t -> t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS)
                .collect(Collectors.toList());

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generateTicketRiskReport");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ObjectNode report = generateReport(eligibleTickets);

        // riskByType (average normalized score -> qualifier)
        ObjectNode riskByType = mapper.createObjectNode();
        riskByType.put("BUG", riskQualifier(avgRiskForType(eligibleTickets, "BUG")));
        riskByType.put("FEATURE_REQUEST", riskQualifier(avgRiskForType(eligibleTickets, "FEATURE_REQUEST")));
        riskByType.put("UI_FEEDBACK", riskQualifier(avgRiskForType(eligibleTickets, "UI_FEEDBACK")));
        report.set("riskByType", riskByType);

        res.set("report", report);
        outputs.add(res);
    }

}
