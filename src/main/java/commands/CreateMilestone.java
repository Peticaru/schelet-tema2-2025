package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;

import java.util.List;
import java.util.Optional;

public class CreateMilestone extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Milestones cannot be created during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole(), input.getTimestamp());
            return;
        }
        if (input.getTickets() != null) {
            for (Integer id : input.getTickets()) {
                Ticket t = system.getTickets().get(id);
                if (t == null) {
                    addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + id + " does not exist.", input.getTimestamp());
                    return;
                }
                Optional<Milestone> assignedMilestone = system.getMilestones().stream().filter(m -> m.getTickets().contains(id)).findFirst();
                if (assignedMilestone.isPresent()) {
                    addError(outputs, input.getCommand(), input.getUsername(), "Tickets " + id + " already assigned to milestone " + assignedMilestone.get().getName() + ".", input.getTimestamp());
                    return;
                }
            }
        }
        Milestone m = new Milestone();
        m.setName(input.getName());
        m.setDueDate(input.getDueDate());
        m.setCreatedBy(user.getUsername());
        m.setCreatedAt(input.getTimestamp());
        if (input.getTickets() != null) m.setTickets(input.getTickets());
        if (input.getAssignedDevs() != null) m.setAssignedDevs(input.getAssignedDevs());
        if (input.getBlockingFor() != null) m.setBlockingFor(input.getBlockingFor());
        if (m.getBlockingFor() != null) {
            for (String blockedName : m.getBlockingFor()) {
                Milestone blockedM = system.findMilestoneByName(blockedName);
                if (blockedM != null) blockedM.getDependsOn().add(m.getName());
            }
        }
        if (m.getTickets() != null) {
            for (Integer tid : m.getTickets()) {
                Ticket t = system.getTickets().get(tid);
                HistoryEntry h = new HistoryEntry();
                h.setAction("ADDED_TO_MILESTONE");
                h.setMilestone(m.getName());
                h.setBy(user.getUsername());
                h.setTimestamp(input.getTimestamp());
                t.addHistoryEntry(h);
            }
        }
        system.addMilestone(m);
        system.notifyDevs(m, "New milestone " + m.getName() + " has been created with due date " + m.getDueDate() + ".");
    }
}
