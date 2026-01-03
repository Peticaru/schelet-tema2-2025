package commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;

import java.util.List;

public class ViewNotifications extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewNotifications");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode notifs = mapper.valueToTree(user.getNotifications());
        res.set("notifications", notifs);
        user.clearNotifications();
        outputs.add(res);
    }
}
