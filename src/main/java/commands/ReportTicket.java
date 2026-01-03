package commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Priority;
import models.Role;
import models.Ticket;
import models.User;
import services.TicketFactory;
import services.TicketSystem;

import java.util.List;

public class ReportTicket extends BaseCommand {
    public void execute(TicketSystem system,  CommandInput input, User user, List<ObjectNode> outputs) {
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
        Ticket ticket =  TicketFactory.createTicket(params, id, input.getTimestamp());
        if (ticket.getReportedBy().isEmpty()) ticket.setBusinessPriority(Priority.LOW);
        system.getTickets().put(id, ticket);
    }
}
