package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;

import java.util.List;

public class UndoAssignTicket extends BaseCommand{
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.DEVELOPER) return;
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null || !user.getUsername().equals(ticket.getAssignedTo())) return;
        ticket.setAssignedTo(null);
        ticket.setAssignedAt(null);
        ticket.setStatus(Status.OPEN);
        HistoryEntry entry = new HistoryEntry();
        entry.setAction("DE-ASSIGNED");
        entry.setBy(user.getUsername());
        entry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(entry);
    }
}
