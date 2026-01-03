package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;

import java.util.List;

public class AddComments extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
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
}
