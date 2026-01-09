package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;

import java.util.List;

public class ChangeStatus extends BaseCommand {

    @Override
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        int ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);

        if (ticket == null) return;

        if (ticket.getAssignedTo() == null || !ticket.getAssignedTo().equals(user.getUsername())) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "Ticket " + ticketId + " is not assigned to developer " + user.getUsername() + ".",
                    input.getTimestamp());
            return;
        }

        Status oldStatus = ticket.getStatus();
        Status newStatus = null;

        if (oldStatus == Status.IN_PROGRESS) {
            newStatus = Status.RESOLVED;
            ticket.setSolvedAt(input.getTimestamp());
        } else if (oldStatus == Status.RESOLVED) {
            newStatus = Status.CLOSED;
        } else if (oldStatus == Status.OPEN) {
            newStatus = Status.IN_PROGRESS;
        } else {
            return;
        }

        ticket.setStatus(newStatus);

        HistoryEntry entry = new HistoryEntry();
        entry.setTimestamp(input.getTimestamp());
        entry.setBy(user.getUsername());
        entry.setAction("STATUS_CHANGED");
        entry.setFrom(oldStatus.toString());
        entry.setTo(newStatus.toString());
        ticket.addHistoryEntry(entry);

        if (newStatus == Status.CLOSED) {
            checkAndNotifyUnblocking(system, ticket);
        }
    }

    private void checkAndNotifyUnblocking(TicketSystem system, Ticket closedTicket) {
        Milestone blockingMilestone = null;
        if (system.getMilestones() != null) {
            for (Milestone m : system.getMilestones()) {
                if (m.getTickets() != null && m.getTickets().contains(closedTicket.getId())) {
                    blockingMilestone = m;
                    break;
                }
            }
        }

        if (blockingMilestone == null) return;

        if (system.getMilestones() != null) {
            for (Milestone blockedM : system.getMilestones()) {
                if (isDependent(blockedM, blockingMilestone)) {
                    if (!system.isMilestoneBlocked(blockedM)) {
                        system.handleUnblocking(blockedM, closedTicket.getId());
                    }
                }
            }
        }
    }

    private boolean isDependent(Milestone target, Milestone blocker) {
        if (blocker.getBlockingFor() != null && blocker.getBlockingFor().contains(target.getName())) {
            return true;
        }
        return false;
    }
}