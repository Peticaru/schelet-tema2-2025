package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.User;
import services.TicketSystem;

import java.util.List;

public class LostInvestors extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
       system.setInvestorsLost(true);
    }
}
