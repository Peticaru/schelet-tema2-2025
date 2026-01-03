package commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;
import utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class ViewTickets extends BaseCommand {



    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> visibleTickets = new ArrayList<>();
        List<Ticket> allTickets = system.getTickets().values().stream().collect(Collectors.toList());
        if (user.getRole() == Role.MANAGER) {
            visibleTickets = allTickets;
        } else if (user.getRole() == Role.REPORTER) {
            visibleTickets = allTickets.stream().filter(t -> t.getReportedBy().equals(user.getUsername())).collect(Collectors.toList());
        } else if (user.getRole() == Role.DEVELOPER) {
            List<Milestone> devMilestones = system.getMilestones().stream().filter(m -> m.getAssignedDevs().contains(user.getUsername())).collect(Collectors.toList());
            Set<Integer> visibleTicketIds = new HashSet<>();
            for (Milestone m : devMilestones) visibleTicketIds.addAll(m.getTickets());
            visibleTickets = allTickets.stream().filter(t -> visibleTicketIds.contains(t.getId()) && t.getStatus() == Status.OPEN).collect(Collectors.toList());
        }
        visibleTickets.sort(Comparator.comparing(Ticket::getCreatedAt).thenComparingInt(Ticket::getId));
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewTickets");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode ticketsArray = mapper.createArrayNode();
        for (Ticket t : visibleTickets) {
            ObjectNode tNode = ticketObject(t);
            tNode.put("assignedAt", t.getAssignedAt() == null ? "" : t.getAssignedAt());
            tNode.put("solvedAt", t.getSolvedAt() == null ? "" : t.getSolvedAt());
            tNode.put("assignedTo", t.getAssignedTo() == null ? "" : t.getAssignedTo());
            tNode.put("reportedBy", t.getReportedBy() == null ? "" : t.getReportedBy());
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            ticketsArray.add(tNode);
        }
        res.set("tickets", ticketsArray);
        outputs.add(res);
    }
}
