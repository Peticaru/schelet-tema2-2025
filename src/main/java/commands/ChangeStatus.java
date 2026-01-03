package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.HistoryEntry;
import models.Status;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.List;

public class ChangeStatus extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {

        Ticket ticket = getTicketWithId(system, user, input, outputs);
        if (ticket == null) return;

        Status oldStatus = ticket.getStatus();

        Status newStatus;

        if (oldStatus == Status.IN_PROGRESS) {
            newStatus = Status.RESOLVED;
            ticket.setSolvedAt(input.getTimestamp());
        } else if (oldStatus == Status.RESOLVED) {
            newStatus = Status.CLOSED;
        } else {
            return;
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

}
