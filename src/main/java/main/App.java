package main;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import commands.*;
import models.User;
import services.TicketSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * main.App represents the main application logic that processes input commands,
 * generates outputs, and writes them to a file
 */
public class App {
    private App() {
    }

    private static final String INPUT_USERS_FIELD = "input/database/users.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter WRITER = MAPPER.writer().withDefaultPrettyPrinter();

    /**
     * Runs the application: reads commands from an input file,
     * processes them, generates results, and writes them to an output file
     *
     * @param inputPath path to the input file containing commands
     * @param outputPath path to the file where results should be written
     */
    public static void run(final String inputPath, final String outputPath) {
        List<ObjectNode> outputs = new ArrayList<>();
        TicketSystem system = new TicketSystem();



        try {
            // Load initial user data
            List<User> userList = MAPPER.readValue(new File(INPUT_USERS_FIELD), new TypeReference<>() {});
            for (User user : userList) {
                system.getUsers().put(user.getUsername(), user);
            }

            // Load commands
            List<CommandInput> commandInputs = MAPPER.readValue(new File(inputPath), new TypeReference<>() {});

            for (CommandInput ci : commandInputs) {
                if (system.isInvestorsLost()) break;

                system.updateTime(ci.getTimestamp());
                
                Command command = createCommand(ci);
                if (command != null) {
                    ObjectNode result = command.execute(system);
                    if (result != null) {
                        outputs.add(result);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // DO NOT CHANGE THIS SECTION IN ANY WAY
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            WRITER.withDefaultPrettyPrinter().writeValue(outputFile, outputs);
        } catch (IOException e) {
            System.out.println("error writing to output file: " + e.getMessage());
        }
    }

    private static Command createCommand(CommandInput ci) {
        switch (ci.getCommand()) {
            case "lostInvestors": return new LostInvestorsCommand(ci);
            case "startTestingPhase": return new StartTestingPhaseCommand(ci);
            case "reportTicket": return new ReportTicketCommand(ci);
            case "viewTickets": return new ViewTicketsCommand(ci);
            case "addComment": return new AddCommentCommand(ci);
            case "undoAddComment": return new UndoAddCommentCommand(ci);
            case "createMilestone": return new CreateMilestoneCommand(ci);
            case "viewMilestones": return new ViewMilestonesCommand(ci);
            case "assignTicket": return new AssignTicketCommand(ci);
            case "undoAssignTicket": return new UndoAssignTicketCommand(ci);
            case "viewAssignedTickets": return new ViewAssignedTicketsCommand(ci);
            case "changeStatus": return new ChangeStatusCommand(ci);
            case "undoChangeStatus": return new UndoChangeStatusCommand(ci);
            case "viewNotifications": return new ViewNotificationsCommand(ci);
            case "viewTicketHistory": return new ViewTicketHistoryCommand(ci);
            case "generateTicketRiskReport": return new GenerateTicketRiskReportCommand(ci);
            case "generateCustomerImpactReport": return new GenerateCustomerImpactReportCommand(ci);
            case "generateResolutionEfficiencyReport": return new GenerateResolutionEfficiencyReportCommand(ci);
            case "appStabilityReport": return new AppStabilityReportCommand(ci);
            case "generatePerformanceReport": return new GeneratePerformanceReportCommand(ci);
            case "search": return new SearchCommand(ci);
            default: return null;
        }
    }
}
