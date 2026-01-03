package commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Priority;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.List;

public abstract class BaseCommand implements Command {
    protected final ObjectMapper mapper = new ObjectMapper();

    protected void addError(List<ObjectNode> outputs, String command, String username, String message, String timestamp) {
        ObjectNode res = mapper.createObjectNode();
        res.put("command", command);
        res.put("username", username);
        res.put("timestamp", timestamp);
        res.put("error", message);
        outputs.add(res);
    }

    protected ObjectNode generateReport( List<Ticket> eligibleTickets) {
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

        return report;
    }
    protected ObjectNode ticketObject(Ticket t) {
        ObjectNode tn = mapper.createObjectNode();
        tn.put("id", t.getId());
        tn.put("type", t.getType());
        tn.put("title", t.getTitle());
        tn.put("businessPriority", t.getBusinessPriority().toString());
        tn.put("status", t.getStatus().toString());
        tn.put("createdAt", t.getCreatedAt());
        return tn;
    }


    protected Ticket getTicketWithId(TicketSystem system, User user, CommandInput input, List<ObjectNode> outputs) {
        int ticketID = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketID);
        if (ticket == null)
            return null;

        if (ticket.getAssignedTo() == null || !user.getUsername().equals(ticket.getAssignedTo())) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "Ticket " + ticketID + " is not assigned to developer " + user.getUsername() + ".",
                    input.getTimestamp());
            return null;
        }
        return ticket;
    }

    protected double round2 (double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}