// java
package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.User;
import services.TicketSystem;

import java.util.List;

public interface Command {
    void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs);
}