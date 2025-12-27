package services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import commands.CommandInput;
import models.*;

import java.util.List;

public class CommandRunner {
    private final TicketSystem system;
    private final ObjectMapper mapper;

    public CommandRunner() {
        this.system = TicketSystem.getInstance();
        this.mapper = new ObjectMapper(); // Poți folosi unul global dacă vrei
    }

    public void execute(CommandInput input, List<ObjectNode> outputs) {
        // 0. Verifică dacă sistemul e activ (Lost Investors)
        if (!system.isActive()) return;

        // 1. Validare User (Globală)
        User user = system.getUser(input.getUsername());
        if (user == null) {
            addError(outputs, input.getCommand(), "The user " + input.getUsername() + " was not found.", input.getTimestamp());
            return;
        }

        // 2. Routing Comenzi
        try {
            switch (input.getCommand()) {
                case "reportTicket":
                    handleReportTicket(input, user, outputs);
                    break;
                case "lostInvestors":
                    system.setActive(false);
                    // Această comandă nu are output
                    break;
                // ... aici adaugi restul cazurilor pe măsură ce le implementezi
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace(); // Pentru debug
        }
    }

    private void handleReportTicket(CommandInput input, User user, List<ObjectNode> outputs) {
        // Validare Rol: Doar Reporterii pot raporta
        if (user.getRole() != Role.REPORTER) {
            addError(outputs, input.getCommand(),
                    "The user does not have permission to execute this command: required role REPORTER; user role " + user.getRole(),
                    input.getTimestamp());
            return;
        }

        // Validare Fază: Doar în Testing Phase
        if (!system.isTestingPhase()) {
            addError(outputs, input.getCommand(), "Command not allowed: active phase is DEVELOPMENT", input.getTimestamp());
            return;
        }

        // Creare Tichet
        int id = system.getNextTicketId();

        // Logică specială: Tichete anonime
        // Dacă reportedBy e null în JSON, îl punem pe userul curent.
        // Dacă e empty string "", e anonim.
        if (input.getReportedBy() == null) {
            input.setReportedBy(user.getUsername());
        }

        // Factory Call
        Ticket ticket = TicketFactory.createTicket(input, id);

        // Logică tichete anonime (Forțare prioritate LOW)
        if (ticket.getReportedBy().isEmpty()) {
            // Asigură-te că ai Priority.LOW
            // ticket.setBusinessPriority(Priority.LOW);
        }

        // Adăugare în sistem
        system.addTicket(ticket);

        // Output Success
        ObjectNode result = mapper.createObjectNode();
        result.put("command", "reportTicket");
        ObjectNode outputDetail = mapper.createObjectNode();
        outputDetail.put("status", "success");
        outputDetail.put("ticketId", id);
        result.set("output", outputDetail);
        result.put("timestamp", input.getTimestamp());
        outputs.add(result);
    }

    // Helper pentru erori standard
    private void addError(List<ObjectNode> outputs, String command, String errorMsg, String timestamp) {
        ObjectNode errorNode = mapper.createObjectNode();
        errorNode.put("command", command);
        ObjectNode outputDetail = mapper.createObjectNode();
        outputDetail.put("error", errorMsg);
        outputDetail.put("timestamp", timestamp);
        errorNode.set("output", outputDetail);
        outputs.add(errorNode);
    }
}