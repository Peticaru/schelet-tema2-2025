package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface Command {
    ObjectNode execute(TicketSystem system);
}
