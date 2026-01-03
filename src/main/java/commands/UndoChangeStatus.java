package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.HistoryEntry;
import models.Status;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.List;

public class UndoChangeStatus extends BaseCommand {

    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {

        Ticket ticket = getTicketWithId(system, user, input, outputs);
        if (ticket == null) return;

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


}
