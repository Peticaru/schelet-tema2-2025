package commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.List;
import java.util.stream.Collectors;

public class ViewAssignedTickets extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> assignedTickets = system.getTickets().values().stream()
                .filter(t -> user.getUsername().equals(t.getAssignedTo()))
                .collect(Collectors.toList());
        assignedTickets.sort((t1, t2) -> {
            int p1 = t1.getBusinessPriority().ordinal();
            int p2 = t2.getBusinessPriority().ordinal();
            if (p1 != p2) return Integer.compare(p2, p1);
            return Integer.compare(t1.getId(), t2.getId());
        });
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewAssignedTickets");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode arr = mapper.createArrayNode();
        for (Ticket t : assignedTickets) {
            ObjectNode tNode = ticketObject(t);
            tNode.put("assignedAt", t.getAssignedAt());
            tNode.put("reportedBy", t.getReportedBy());
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            arr.add(tNode);
        }
        res.set("assignedTickets", arr);
        outputs.add(res);
    }
}
