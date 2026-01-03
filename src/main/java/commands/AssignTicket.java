package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;
import utils.Utils;

import java.util.List;
import java.util.Optional;

public class AssignTicket extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Tickets cannot be assigned during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.DEVELOPER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role DEVELOPER; user role " + user.getRole(), input.getTimestamp());
            return;
        }
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + ticketId + " does not exist.", input.getTimestamp());
            return;
        }
        if (ticket.getStatus() != Status.OPEN) {
            addError(outputs, input.getCommand(), input.getUsername(), "Only OPEN tickets can be assigned.", input.getTimestamp());
            return;
        }
        Optional<Milestone> mOpt = system.getMilestones().stream().filter(m -> m.getTickets().contains(ticketId)).findFirst();
        if (mOpt.isEmpty()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + ticketId + " is not assigned to any milestone.", input.getTimestamp());
            return;
        }
        Milestone m = mOpt.get();
        if (!m.getAssignedDevs().contains(user.getUsername())) {
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + user.getUsername() + " is not assigned to milestone " + m.getName() + ".", input.getTimestamp());
            return;
        }
        if (system.isMilestoneBlocked(m)) {
            addError(outputs, input.getCommand(), input.getUsername(), "Cannot assign ticket " + ticketId + " from blocked milestone " + m.getName() + ".", input.getTimestamp());
            return;
        }
        Developer dev = (Developer) user;
        if (!Utils.isExpertiseMatch(dev, ticket)) {
            String required = Utils.getRequiredExpertiseString(ticket.getExpertiseArea());
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + dev.getUsername() + " cannot assign ticket " + ticketId + " due to expertise area. Required: " + required + "; Current: " + dev.getExpertiseArea() + ".", input.getTimestamp());
            return;
        }
        if (!Utils.isSeniorityMatch(dev, ticket)) {
            String required = Utils.getRequiredSeniorityString(ticket);
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + dev.getUsername() + " cannot assign ticket " + ticketId + " due to seniority level. Required: " + required + "; Current: " + dev.getSeniority() + ".", input.getTimestamp());
            return;
        }
        ticket.setAssignedTo(user.getUsername());
        ticket.setAssignedAt(input.getTimestamp());
        HistoryEntry assignEntry = new HistoryEntry();
        assignEntry.setAction("ASSIGNED");
        assignEntry.setBy(user.getUsername());
        assignEntry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(assignEntry);
        ticket.setStatus(Status.IN_PROGRESS);
        HistoryEntry statusEntry = new HistoryEntry();
        statusEntry.setAction("STATUS_CHANGED");
        statusEntry.setFrom("OPEN");
        statusEntry.setTo("IN_PROGRESS");
        statusEntry.setBy(user.getUsername());
        statusEntry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(statusEntry);
    }
}
