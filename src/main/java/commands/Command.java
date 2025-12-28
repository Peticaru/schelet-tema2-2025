package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import services.TicketSystem;

public interface Command {
    ObjectNode execute(TicketSystem system);
}
