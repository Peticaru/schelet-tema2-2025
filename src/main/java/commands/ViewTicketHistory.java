package commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.HistoryEntry;
import models.Milestone;
import models.Role;
import models.Ticket;
import models.User;
import models.Comment;
import services.TicketSystem;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ViewTicketHistory extends BaseCommand {
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        // 1. Colectăm tichetele relevante
        List<Ticket> userTickets = system.getTickets().values().stream()
                .filter(t -> user.getUsername().equals(t.getAssignedTo()))
                .collect(Collectors.toList());

        if (user.getRole() == Role.DEVELOPER) {
            List<Ticket> historical = system.getTickets().values().stream()
                    .filter(t -> !userTickets.contains(t) && t.getHistory().stream()
                            .anyMatch(h -> "ASSIGNED".equals(h.getAction()) && user.getUsername().equals(h.getBy())))
                    .collect(Collectors.toList());
            userTickets.addAll(historical);
        }

        if (user.getRole() == Role.MANAGER) {
            Set<Integer> managerTicketIds = new HashSet<>();
            if (system.getMilestones() != null) {
                for (Milestone m : system.getMilestones()) {
                    if (user.getUsername().equals(m.getCreatedBy()) && m.getTickets() != null) {
                        managerTicketIds.addAll(m.getTickets());
                    }
                }
            }
            for (Integer id : managerTicketIds) {
                Ticket t = system.getTickets().get(id);
                if (t != null && !userTickets.contains(t)) {
                    userTickets.add(t);
                }
            }
        }

        userTickets.sort(Comparator.comparing(Ticket::getCreatedAt)
                .thenComparingInt(Ticket::getId));

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

            // --- LOGICA DE FILTRARE PENTRU DE-ASSIGNED ---
            List<HistoryEntry> rawHistory = t.getHistory();
            List<HistoryEntry> visibleHistory = new ArrayList<>(rawHistory);
            List<Comment> visibleComments = new ArrayList<>(t.getComments());

            // Dacă e developer și NU e asignat curent, verificăm dacă a dat DE-ASSIGNED
            if (user.getRole() == Role.DEVELOPER && !user.getUsername().equals(t.getAssignedTo())) {
                int deAssignIndex = -1;
                // Căutăm ultima acțiune de DE-ASSIGNED a acestui user
                for (int i = rawHistory.size() - 1; i >= 0; i--) {
                    HistoryEntry h = rawHistory.get(i);
                    if ("DE-ASSIGNED".equals(h.getAction()) && user.getUsername().equals(h.getBy())) {
                        deAssignIndex = i;
                        break;
                    }
                }

                // Dacă am găsit, tăiem tot ce e după ea
                if (deAssignIndex != -1) {
                    visibleHistory = new ArrayList<>(rawHistory.subList(0, deAssignIndex + 1));

                    // Opțional: tăiem și comentariile care sunt strict după data de-asignării
                    // (Deși testul se plânge de 'actions', e bine să fim consistenți)
                    String cutoffStr = rawHistory.get(deAssignIndex).getTimestamp();
                    try {
                        LocalDate cutoffDate = LocalDate.parse(cutoffStr);
                        visibleComments = visibleComments.stream()
                                .filter(c -> !LocalDate.parse(c.getCreatedAt()).isAfter(cutoffDate))
                                .collect(Collectors.toList());
                    } catch (Exception e) {
                        // Ignorăm erorile de parse, păstrăm comentariile
                    }
                }
            }

            // Aplicăm filtrul standard de tipuri de acțiuni (ASSIGNED, STATUS_CHANGED etc.)
            List<HistoryEntry> filteredHistory = visibleHistory.stream()
                    .filter(h -> allowedActions.contains(h.getAction()))
                    .collect(Collectors.toList());

            tNode.set("actions", mapper.valueToTree(filteredHistory));
            tNode.set("comments", mapper.valueToTree(visibleComments));
            historyArr.add(tNode);
        }
        res.set("ticketHistory", historyArr);
        outputs.add(res);
    }
}