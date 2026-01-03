package commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;
import utils.Utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeneratePerformanceReport extends BaseCommand {

    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {

        Manager manager = (Manager) user;

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "generatePerformanceReport");
        res.put("username", manager.getUsername());
        res.put("timestamp", input.getTimestamp());

        ArrayNode reportArr = mapper.createArrayNode();
        LocalDate now = LocalDate.parse(input.getTimestamp());
        List<String> subs = new ArrayList<>(manager.getSubordinates());
        Collections.sort(subs);

        for (String devUsername : subs) {
            User u = system.getUsers().get(devUsername);
            Developer dev = (Developer) u;

            List<Ticket> closedLastMonth = system.getTickets().values().stream()
                    .filter(t -> devUsername.equals(t.getAssignedTo()))
                    .filter(t -> t.getStatus() == Status.CLOSED)
                    .filter(t -> {
                        LocalDate closedAt = Utils.whereClosed(t);
                        if (closedAt == null) return false;
                        int prevMonth = now.minusMonths(1).getMonthValue();
                        int prevYear  = now.minusMonths(1).getYear();
                        return closedAt.getYear() == prevYear && closedAt.getMonthValue() == prevMonth;
                    })
                    .toList();

            int closedTickets = closedLastMonth.size();

            double avgResolutionTime = 0.0;
            double sum = 0.0;
            for (Ticket t : closedLastMonth) {
                LocalDate assigned = LocalDate.parse(t.getAssignedAt());
                LocalDate solved = LocalDate.parse(t.getSolvedAt());
                sum += ChronoUnit.DAYS.between(assigned, solved) + 1;
            }
            avgResolutionTime = sum / closedTickets;

            int highPriorityTickets = 0;
            for (Ticket t : closedLastMonth) {
                if (t.getBusinessPriority() == Priority.HIGH || t.getBusinessPriority() == Priority.CRITICAL) {
                    highPriorityTickets++;
                }
            }

            int bug = 0, feature = 0, ui = 0;
            for (Ticket t : closedLastMonth) {
                if ("BUG".equals(t.getType())) bug++;
                else if ("FEATURE_REQUEST".equals(t.getType())) feature++;
                else if ("UI_FEEDBACK".equals(t.getType())) ui++;
            }
            double performanceScore = Utils.computePerformanceScore(dev.getSeniority(), closedTickets, highPriorityTickets, avgResolutionTime, bug, feature, ui);

            dev.setPerformanceScore(round2(performanceScore));

            ObjectNode dn = mapper.createObjectNode();
            dn.put("username", dev.getUsername());
            dn.put("closedTickets", closedTickets);
            dn.put("averageResolutionTime", round2(avgResolutionTime));
            dn.put("performanceScore", round2(performanceScore));
            dn.put("seniority", dev.getSeniority().toString());

            reportArr.add(dn);
        }

        res.set("report", reportArr);
        outputs.add(res);
    }

}
