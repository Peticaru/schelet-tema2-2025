package commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.HistoryEntry;
import models.Role;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.*;
import java.util.stream.Collectors;

public class ViewTicketHistory extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> userTickets = system.getTickets().values().stream()
                .filter(t -> user.getUsername().equals(t.getAssignedTo()))
                .sorted(Comparator.comparingInt(Ticket::getId))
                .collect(Collectors.toList());

        if (user.getRole() == Role.DEVELOPER) {
            List<Ticket> historical = system.getTickets().values().stream()
                    .filter(t -> !userTickets.contains(t) && t.getHistory().stream()
                            .anyMatch(h -> "ASSIGNED".equals(h.getAction()) && user.getUsername().equals(h.getBy())))
                    .sorted(Comparator.comparingInt(Ticket::getId))
                    .collect(Collectors.toList());
            userTickets.addAll(historical);
            userTickets.sort(Comparator.comparingInt(Ticket::getId));
        }

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewTicketHistory");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ArrayNode historyArr = mapper.createArrayNode();
        Set<String> allowedActions = new HashSet<>(Arrays.asList(
                "ASSIGNED", "DE-ASSIGNED", "STATUS_CHANGED", "ADDED_TO_MILESTONE", "REMOVED_FROM_DEV"
        ));

        for (Ticket t : userTickets) {
            ObjectNode tNode = mapper.createObjectNode();
            tNode.put("id", t.getId());
            tNode.put("title", t.getTitle());
            tNode.put("status", t.getStatus().toString());
            List<HistoryEntry> filteredHistory = t.getHistory().stream()
                    .filter(h -> allowedActions.contains(h.getAction()))
                    .collect(Collectors.toList());
            tNode.set("actions", mapper.valueToTree(filteredHistory));
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            historyArr.add(tNode);
        }
        res.set("ticketHistory", historyArr);
        outputs.add(res);
    }
}
