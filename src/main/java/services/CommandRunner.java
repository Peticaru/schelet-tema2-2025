package services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import commands.*;
import models.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandRunner {
    private final TicketSystem system;
    private final ObjectMapper mapper;
    private final Map<String, BaseCommand> commandRegistry;

    public CommandRunner() {
        this.system = TicketSystem.getInstance();
        this.mapper = new ObjectMapper();
        this.commandRegistry = new HashMap<>();
        initializeCommands();
    }

    /**
     * Registers all available commands in the registry.
     * Each key corresponds to the command string from the JSON input.
     */
    private void initializeCommands() {
        commandRegistry.put("reportTicket", new ReportTicket());
        commandRegistry.put("viewTickets", new ViewTickets());
        commandRegistry.put("viewTicketHistory", new ViewTicketHistory());
        commandRegistry.put("search", new Search());

        commandRegistry.put("createMilestone", new CreateMilestone());
        commandRegistry.put("viewMilestones", new ViewMilestones());

        commandRegistry.put("assignTicket", new AssignTicket());
        commandRegistry.put("undoAssignTicket", new UndoAssignTicket());
        commandRegistry.put("viewAssignedTickets", new ViewAssignedTickets());

        commandRegistry.put("changeStatus", new ChangeStatus());
        commandRegistry.put("undoChangeStatus", new UndoChangeStatus());
        commandRegistry.put("addComment", new AddComments());
        commandRegistry.put("undoAddComment", new UndoAddComment());

        commandRegistry.put("viewNotifications", new ViewNotifications());

        commandRegistry.put("generateCustomerImpactReport", new GenerateCustomImpactReport());
        commandRegistry.put("generateTicketRiskReport", new GenerateTicketReport());
        commandRegistry.put("generateResolutionEfficiencyReport", new GenerateResolutionEfficiencyReport());
        commandRegistry.put("appStabilityReport", new AppStabilityReport());
        commandRegistry.put("generatePerformanceReport", new GeneratePerformanceReport());

        // System Commands
        commandRegistry.put("lostInvestors", new LostInvestors());
    }

    /**
     * Main entry point for executing commands.
     */
    public void execute(CommandInput input, List<ObjectNode> outputs) {
        system.updateTime(input.getTimestamp());

        if (system.isInvestorsLost()) return;

        User user = system.getUsers().get(input.getUsername());
        if (user == null) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "The user " + input.getUsername() + " does not exist.",
                    input.getTimestamp());
            return;
        }

        BaseCommand command = commandRegistry.get(input.getCommand());
        command.execute(system, input, user, outputs);
    }

    private void addError(List<ObjectNode> outputs, String command, String username, String message, String timestamp) {
        ObjectNode res = mapper.createObjectNode();
        res.put("command", command);
        res.put("username", username);
        res.put("timestamp", timestamp);
        res.put("error", message);
        outputs.add(res);
    }
}