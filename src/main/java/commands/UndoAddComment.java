package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Comment;
import models.Role;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.List;

public class UndoAddComment extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
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
}
