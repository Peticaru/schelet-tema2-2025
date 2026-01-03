package commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import services.TicketSystem;
import utils.Utils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class Search extends BaseCommand {

    private List<Developer> searchDevelopers(TicketSystem system, Manager manager, JsonNode filters) {
        if (manager.getSubordinates() == null) return new ArrayList<>();

        List<Developer> result = new ArrayList<>();
        for (String subUsername : manager.getSubordinates()) {
            User u = system.getUsers().get(subUsername);
            if (u instanceof Developer) result.add((Developer) u);
        }

        if (filters == null) {
            result.sort(Comparator.comparing(Developer::getUsername));
            return result;
        }

        result = result.stream().filter(dev -> {
            if (filters.has("expertiseArea") &&
                    !dev.getExpertiseArea().toString().equals(filters.get("expertiseArea").asText()))
                return false;

            if (filters.has("seniority") &&
                    !dev.getSeniority().toString().equals(filters.get("seniority").asText()))
                return false;

            if (filters.has("performanceScoreAbove")) {
                double v = filters.get("performanceScoreAbove").asDouble();
                return (dev.getPerformanceScore() <= v);
            }

            if (filters.has("performanceScoreBelow")) {
                double v = filters.get("performanceScoreBelow").asDouble();
                return (dev.getPerformanceScore() <= v);
            }

            return true;
        }).collect(Collectors.toList());

        result.sort(Comparator.comparing(Developer::getUsername));
        return result;
    }

    // --- NOTIFICATIONS ---
    private List<String> getMatchingWords(Ticket t, List<String> keywords) {
        Set<String> matched = new HashSet<>();
        String text = (t.getTitle() + " " + (t.getDescription() == null ? "" : t.getDescription())).toLowerCase();
        for (String k : keywords) {
            if (text.contains(k.toLowerCase())) matched.add(k);
        }
        List<String> result = new ArrayList<>(matched);
        Collections.sort(result);
        return result;
    }


    private List<Ticket> searchTickets(TicketSystem system, User user, JsonNode filters) {
        List<Ticket> scope = new ArrayList<>();
        if (user.getRole() == Role.MANAGER) {
            scope = new ArrayList<>(system.getTickets().values());
        } else if (user.getRole() == Role.DEVELOPER) {
            List<Milestone> devMilestones = system.getMilestones().stream().filter(m -> m.getAssignedDevs().contains(user.getUsername())).collect(Collectors.toList());
            Set<Integer> validIds = new HashSet<>();
            for (Milestone m : devMilestones) validIds.addAll(m.getTickets());
            for (Ticket t : system.getTickets().values()) {
                if (validIds.contains(t.getId()) && t.getStatus() == Status.OPEN) scope.add(t);
            }
        } else return new ArrayList<>();

        if (filters == null) {
            scope.sort(Comparator.comparing(Ticket::getCreatedAt).thenComparingInt(Ticket::getId));
            return scope;
        }
        return scope.stream().filter(t -> {
            if (filters.has("businessPriority") && !t.getBusinessPriority().toString().equals(filters.get("businessPriority").asText())) return false;
            if (filters.has("type") && !t.getType().equals(filters.get("type").asText())) return false;
            if (filters.has("createdAfter")) {
                LocalDate tDate = LocalDate.parse(t.getCreatedAt());
                LocalDate filterDate = LocalDate.parse(filters.get("createdAfter").asText());
                if (!tDate.isAfter(filterDate)) return false;
            }
            if (filters.has("createdBefore")) {
                LocalDate tDate = LocalDate.parse(t.getCreatedAt());
                LocalDate filterDate = LocalDate.parse(filters.get("createdBefore").asText());
                if (!tDate.isBefore(filterDate)) return false;
            }
            if (filters.has("keywords")) {
                List<String> keywords = new ArrayList<>();
                filters.get("keywords").forEach(k -> keywords.add(k.asText()));
                if (getMatchingWords(t, keywords).isEmpty()) return false;
            }
            if (filters.has("availableForAssignment") && filters.get("availableForAssignment").asBoolean()) {
                if (user.getRole() != Role.DEVELOPER) return false;
                if (t.getStatus() != Status.OPEN) return false;
                Optional<Milestone> mOpt = system.getMilestones().stream().filter(m -> m.getTickets().contains(t.getId())).findFirst();
                if (mOpt.isEmpty() || system.isMilestoneBlocked(mOpt.get())) return false;
                if (!system.canAccess((Developer) user, t)) return false;
            }
            return true;
        }).sorted(Comparator.comparing(Ticket::getCreatedAt).thenComparingInt(Ticket::getId)).collect(Collectors.toList());
    }
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        JsonNode filters = input.getFilters();
        String searchType = filters != null && filters.has("searchType") ? filters.get("searchType").asText() : "TICKET";

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "search");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        res.put("searchType", searchType);

        ArrayNode resultsArr = mapper.createArrayNode();

        if ("DEVELOPER".equals(searchType)) {
            if (user.getRole() == Role.MANAGER) {
                List<Developer> devs = searchDevelopers(system, (Manager) user, filters);
                for (Developer d : devs) {
                    ObjectNode dn = mapper.createObjectNode();
                    dn.put("username", d.getUsername());
                    dn.put("expertiseArea", d.getExpertiseArea().toString());
                    dn.put("seniority", d.getSeniority().toString());
                    dn.put("performanceScore", d.getPerformanceScore());
                    dn.put("hireDate", d.getHireDate());
                    resultsArr.add(dn);
                }
            }
        } else {
            List<Ticket> tickets = searchTickets(system, user, filters);
            for (Ticket t : tickets) {
                ObjectNode tn = ticketObject(t);
                tn.put("solvedAt", t.getSolvedAt() == null ? "" : t.getSolvedAt());
                tn.put("reportedBy", t.getReportedBy());

                if (filters != null && filters.has("keywords")) {
                    List<String> keywords = new ArrayList<>();
                    filters.get("keywords").forEach(k -> keywords.add(k.asText()));
                    List<String> matched = getMatchingWords(t, keywords);
                    if (!matched.isEmpty()) {
                        tn.set("matchingWords", mapper.valueToTree(matched));
                    }
                }
                resultsArr.add(tn);
            }
        }
        res.set("results", resultsArr);
        outputs.add(res);
    }
}
