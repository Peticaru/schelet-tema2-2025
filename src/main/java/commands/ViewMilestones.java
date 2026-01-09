package commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;
import utils.Utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ViewMilestones extends BaseCommand {

    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        List<Milestone> visible = new ArrayList<>();
        if (user.getRole() == Role.MANAGER) {
            visible = system.getMilestones().stream()
                    .filter(m -> m.getCreatedBy().equals(user.getUsername()))
                    .collect(Collectors.toList());
        } else if (user.getRole() == Role.DEVELOPER) {
            visible = system.getMilestones().stream()
                    .filter(m -> m.getAssignedDevs().contains(user.getUsername()))
                    .collect(Collectors.toList());
        }

        visible.sort(Comparator.comparing(Milestone::getDueDate).thenComparing(Milestone::getName));

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewMilestones");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ArrayNode arr = mapper.createArrayNode();
        LocalDate now = LocalDate.parse(input.getTimestamp());

        for (Milestone m : visible) {
            ObjectNode mn = mapper.createObjectNode();
            mn.put("name", m.getName());
            mn.set("blockingFor", mapper.valueToTree(m.getBlockingFor()));
            mn.put("dueDate", m.getDueDate());
            mn.put("createdAt", m.getCreatedAt());
            mn.set("tickets", mapper.valueToTree(m.getTickets()));
            mn.set("assignedDevs", mapper.valueToTree(m.getAssignedDevs()));
            mn.put("createdBy", m.getCreatedBy());

            boolean allClosed = true;
            List<Integer> openTickets = new ArrayList<>();
            List<Integer> closedTickets = new ArrayList<>();

            LocalDate lastClosedDate = null;

            for (Integer id : m.getTickets()) {
                Ticket t = system.getTickets().get(id);

                if (t.getStatus() != Status.CLOSED) {
                    allClosed = false;
                    openTickets.add(id);
                } else {
                    closedTickets.add(id);
                    // Presupunem că Utils.whereClosed returnează data corectă din istoric
                    LocalDate closedAt = Utils.whereClosed(t);
                    if (lastClosedDate == null || (closedAt != null && closedAt.isAfter(lastClosedDate))) {
                        lastClosedDate = closedAt;
                    }
                }
            }

            mn.put("status", (allClosed && !m.getTickets().isEmpty()) ? "COMPLETED" : "ACTIVE");

            boolean isBlocked = system.isMilestoneBlocked(m);
            mn.put("isBlocked", isBlocked);

            LocalDate due = LocalDate.parse(m.getDueDate());

            // FIX: Calcul metrici înghețate pentru COMPLETED
            if (allClosed && lastClosedDate != null) {
                // Dacă e gata, calculăm diferența față de data închiderii
                long diff = ChronoUnit.DAYS.between(lastClosedDate, due);

                if (diff < 0) {
                    // Overdue la finalizare
                    mn.put("daysUntilDue", 0);
                    mn.put("overdueBy", Math.abs(diff) + 1);
                } else {
                    // Finalizat la timp
                    mn.put("daysUntilDue", diff + 1);
                    mn.put("overdueBy", 0);
                }
            } else {
                // Activ: calculăm față de now
                long diff = ChronoUnit.DAYS.between(now, due);
                if (diff < 0) {
                    mn.put("daysUntilDue", 0);
                    mn.put("overdueBy", Math.abs(diff) + 1);
                } else {
                    mn.put("daysUntilDue", diff + 1);
                    mn.put("overdueBy", 0);
                }
            }

            mn.set("openTickets", mapper.valueToTree(openTickets));
            mn.set("closedTickets", mapper.valueToTree(closedTickets));

            double ratio = m.getTickets().isEmpty() ? 0.0 : ((double) closedTickets.size() / m.getTickets().size());
            mn.put("completionPercentage", Math.round(ratio * 100.0) / 100.0);

            // FIX: Sortare Repartiție
            List<ObjectNode> repartitionList = new ArrayList<>();
            if (m.getAssignedDevs() != null) {
                for (String dev : m.getAssignedDevs()) {
                    ObjectNode dn = mapper.createObjectNode();
                    dn.put("developer", dev);

                    List<Integer> tids = new ArrayList<>();
                    for (Integer id : m.getTickets()) {
                        Ticket t = system.getTickets().get(id);
                        if (t != null && dev.equals(t.getAssignedTo())) {
                            tids.add(id);
                        }
                    }
                    dn.set("assignedTickets", mapper.valueToTree(tids));
                    repartitionList.add(dn);
                }
            }

            // Sortare: 1. Nr tichete (crescător), 2. Username (alfabetic)
            repartitionList.sort((a, b) -> {
                int sizeA = a.get("assignedTickets").size();
                int sizeB = b.get("assignedTickets").size();
                if (sizeA != sizeB) {
                    return Integer.compare(sizeA, sizeB);
                }
                return a.get("developer").asText().compareTo(b.get("developer").asText());
            });

            ArrayNode rep = mapper.createArrayNode();
            repartitionList.forEach(rep::add);
            mn.set("repartition", rep);

            arr.add(mn);
        }

        res.set("milestones", arr);
        outputs.add(res);
    }
}