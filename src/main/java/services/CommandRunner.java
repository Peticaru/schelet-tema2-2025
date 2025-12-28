package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import commands.CommandInput;
import models.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class CommandRunner {
    private final TicketSystem system;
    private final ObjectMapper mapper;

    public CommandRunner() {
        this.system = TicketSystem.getInstance();
        this.mapper = new ObjectMapper();
    }

    public void execute(CommandInput input, List<ObjectNode> outputs) {
        system.updateTime(input.getTimestamp());

        if (system.isInvestorsLost()) return;

        User user = system.getUsers().get(input.getUsername());
        if (user == null) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user " + input.getUsername() + " does not exist.", input.getTimestamp());
            return;
        }

        switch (input.getCommand()) {
            case "reportTicket": handleReportTicket(input, user, outputs); break;
            case "viewTickets": handleViewTickets(input, user, outputs); break;
            case "createMilestone": handleCreateMilestone(input, user, outputs); break;
            case "viewMilestones": handleViewMilestones(input, user, outputs); break;
            case "assignTicket": handleAssignTicket(input, user, outputs); break;
            case "undoAssignTicket": handleUndoAssignTicket(input, user, outputs); break;
            case "viewAssignedTickets": handleViewAssignedTickets(input, user, outputs); break;
            case "addComment": handleAddComment(input, user, outputs); break;
            case "undoAddComment": handleUndoAddComment(input, user, outputs); break;
            case "changeStatus": handleChangeStatus(input, user, outputs); break;
            case "undoChangeStatus": handleUndoChangeStatus(input, user, outputs); break;
            case "viewTicketHistory": handleViewTicketHistory(input, user, outputs); break;
            case "lostInvestors": system.setInvestorsLost(true); break;
        }
    }

    // --- MILESTONES ---

    private void handleViewMilestones(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Milestone> visible = new ArrayList<>();
        if (user.getRole() == Role.MANAGER) {
            visible = system.getMilestones().stream().filter(m -> m.getCreatedBy().equals(user.getUsername())).collect(Collectors.toList());
        } else if (user.getRole() == Role.DEVELOPER) {
            visible = system.getMilestones().stream().filter(m -> m.getAssignedDevs().contains(user.getUsername())).collect(Collectors.toList());
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
            List<Integer> open = new ArrayList<>();
            List<Integer> closed = new ArrayList<>();
            LocalDate lastSolvedDate = null;

            for(Integer id : m.getTickets()) {
                Ticket t = system.getTickets().get(id);
                if(t.getStatus() != Status.CLOSED) {
                    allClosed = false;
                    open.add(id);
                } else {
                    closed.add(id);
                    if (t.getSolvedAt() != null) {
                        LocalDate solved = LocalDate.parse(t.getSolvedAt());
                        if (lastSolvedDate == null || solved.isAfter(lastSolvedDate)) {
                            lastSolvedDate = solved;
                        }
                    }
                }
            }
            mn.put("status", allClosed && !m.getTickets().isEmpty() ? "COMPLETED" : "ACTIVE");

            boolean isBlocked = system.isMilestoneBlocked(m);
            mn.put("isBlocked", isBlocked);

            LocalDate due = LocalDate.parse(m.getDueDate());
            LocalDate calculationDate = (allClosed && lastSolvedDate != null) ? lastSolvedDate : now;
            long diff = ChronoUnit.DAYS.between(calculationDate, due);

            if(calculationDate.isAfter(due)) {
                mn.put("daysUntilDue", 0);
                // FIX: OverdueBy is exclusive (Math.abs(diff))
                mn.put("overdueBy", Math.abs(diff) + 1);
            } else {
                mn.put("daysUntilDue", diff + 1);
                mn.put("overdueBy", 0);
            }

            mn.set("openTickets", mapper.valueToTree(open));
            mn.set("closedTickets", mapper.valueToTree(closed));

            double ratio = m.getTickets().isEmpty() ? 0.0 : ((double)closed.size() / m.getTickets().size());
            mn.put("completionPercentage", Math.round(ratio * 100.0) / 100.0);

            ArrayNode rep = mapper.createArrayNode();
            if (m.getAssignedDevs() != null) {
                for (String dev : m.getAssignedDevs()) {
                    ObjectNode dn = mapper.createObjectNode();
                    dn.put("developer", dev);
                    List<Integer> tids = new ArrayList<>();
                    for(Integer id : m.getTickets()) {
                        if(dev.equals(system.getTickets().get(id).getAssignedTo())) tids.add(id);
                    }
                    dn.set("assignedTickets", mapper.valueToTree(tids));
                    rep.add(dn);
                }
            }
            mn.set("repartition", rep);
            arr.add(mn);
        }
        res.set("milestones", arr);
        outputs.add(res);
    }

    // --- HISTORY ---

    private void handleViewTicketHistory(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> userTickets = system.getTickets().values().stream()
                .filter(t -> user.getUsername().equals(t.getAssignedTo()))
                .sorted(Comparator.comparingInt(Ticket::getId))
                .collect(Collectors.toList());

        // For Developers: Also include tickets they revoked assignment on
        if (user.getRole() == Role.DEVELOPER) {
            List<Ticket> historical = system.getTickets().values().stream()
                    .filter(t -> !userTickets.contains(t) && t.getHistory().stream()
                            .anyMatch(h -> "ASSIGNED".equals(h.getAction()) && user.getUsername().equals(h.getBy())))
                    .sorted(Comparator.comparingInt(Ticket::getId))
                    .collect(Collectors.toList());
            userTickets.addAll(historical);
            userTickets.sort(Comparator.comparingInt(Ticket::getId));
        }

        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewTicketHistory");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());

        ArrayNode historyArr = mapper.createArrayNode();
        // Allowed actions for output
        Set<String> allowedActions = new HashSet<>(Arrays.asList(
                "ASSIGNED", "DE-ASSIGNED", "STATUS_CHANGED", "ADDED_TO_MILESTONE", "REMOVED_FROM_DEV"
        ));

        for (Ticket t : userTickets) {
            ObjectNode tNode = mapper.createObjectNode();
            tNode.put("id", t.getId());
            tNode.put("title", t.getTitle());
            tNode.put("status", t.getStatus().toString());

            // FILTERING SYSTEM ACTIONS (Fixes Test 10 Array Length Error)
            List<HistoryEntry> filteredHistory = t.getHistory().stream()
                    .filter(h -> allowedActions.contains(h.getAction()))
                    .collect(Collectors.toList());

            tNode.set("actions", mapper.valueToTree(filteredHistory));
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            historyArr.add(tNode);
        }
        res.set("ticketHistory", historyArr);
        outputs.add(res);
    }

    // --- COMMENTS ---

    private void handleAddComment(CommandInput input, User user, List<ObjectNode> outputs) {
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;

        // 1. Anonim
        if (ticket.getReportedBy().isEmpty()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Comments are not allowed on anonymous tickets.", input.getTimestamp());
            return;
        }

        // 2. Reporter CLOSED Check (Moved BEFORE Length Check - Fixes Test 10)
        if (user.getRole() == Role.REPORTER && ticket.getStatus() == Status.CLOSED) {
            addError(outputs, input.getCommand(), input.getUsername(), "Reporters cannot comment on CLOSED tickets.", input.getTimestamp());
            return;
        }

        // 3. Length Check
        if (input.getComment() == null || input.getComment().length() < 10) {
            addError(outputs, input.getCommand(), input.getUsername(), "Comment must be at least 10 characters long.", input.getTimestamp());
            return;
        }

        // 4. Permissions Check
        if (user.getRole() == Role.REPORTER) {
            if (!ticket.getReportedBy().equals(user.getUsername())) {
                addError(outputs, input.getCommand(), input.getUsername(), "Reporter " + user.getUsername() + " cannot comment on ticket " + ticketId + ".", input.getTimestamp());
                return;
            }
        } else if (user.getRole() == Role.DEVELOPER) {
            if (ticket.getAssignedTo() != null && !ticket.getAssignedTo().equals(user.getUsername())) {
                addError(outputs, input.getCommand(), input.getUsername(), "Ticket " + ticketId + " is not assigned to the developer " + user.getUsername() + ".", input.getTimestamp());
                return;
            }
        }

        Comment c = new Comment(user.getUsername(), input.getComment(), input.getTimestamp());
        ticket.getComments().add(c);
    }

    // --- STATUS CHANGES ---

    private void handleChangeStatus(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.DEVELOPER) return;
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;
        if (!user.getUsername().equals(ticket.getAssignedTo())) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket " + ticketId + " is not assigned to developer " + user.getUsername() + ".", input.getTimestamp());
            return;
        }
        Status oldStatus = ticket.getStatus();
        Status newStatus = null;
        if (oldStatus == Status.IN_PROGRESS) {
            newStatus = Status.RESOLVED;
        } else if (oldStatus == Status.RESOLVED) {
            newStatus = Status.CLOSED;
            ticket.setSolvedAt(input.getTimestamp());
        } else {
            return;
        }
        ticket.setStatus(newStatus);
        HistoryEntry entry = new HistoryEntry();
        entry.setAction("STATUS_CHANGED");
        entry.setFrom(oldStatus.toString());
        entry.setTo(newStatus.toString());
        entry.setBy(user.getUsername());
        entry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(entry);
    }

    private void handleUndoChangeStatus(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.DEVELOPER) return;
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;
        if (!user.getUsername().equals(ticket.getAssignedTo())) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket " + ticketId + " is not assigned to developer " + user.getUsername() + ".", input.getTimestamp());
            return;
        }
        Status oldStatus = ticket.getStatus();
        Status newStatus = null;
        if (oldStatus == Status.CLOSED) {
            newStatus = Status.RESOLVED;
            ticket.setSolvedAt(null);
        } else if (oldStatus == Status.RESOLVED) {
            newStatus = Status.IN_PROGRESS;
        } else {
            return;
        }
        ticket.setStatus(newStatus);
        HistoryEntry entry = new HistoryEntry();
        entry.setAction("STATUS_CHANGED");
        entry.setFrom(oldStatus.toString());
        entry.setTo(newStatus.toString());
        entry.setBy(user.getUsername());
        entry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(entry);
    }

    // --- OTHER METHODS ---

    private void handleCreateMilestone(CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Milestones cannot be created during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole() + ".", input.getTimestamp());
            return;
        }
        if (input.getTickets() != null) {
            for (Integer id : input.getTickets()) {
                Ticket t = system.getTickets().get(id);
                if (t == null) {
                    addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + id + " does not exist.", input.getTimestamp());
                    return;
                }
                Optional<Milestone> assignedMilestone = system.getMilestones().stream().filter(m -> m.getTickets().contains(id)).findFirst();
                if (assignedMilestone.isPresent()) {
                    addError(outputs, input.getCommand(), input.getUsername(), "Tickets " + id + " already assigned to milestone " + assignedMilestone.get().getName() + ".", input.getTimestamp());
                    return;
                }
            }
        }
        Milestone m = new Milestone();
        m.setName(input.getName());
        m.setDueDate(input.getDueDate());
        m.setCreatedBy(user.getUsername());
        m.setCreatedAt(input.getTimestamp());
        if (input.getTickets() != null) m.setTickets(input.getTickets());
        if (input.getAssignedDevs() != null) m.setAssignedDevs(input.getAssignedDevs());
        if (input.getBlockingFor() != null) m.setBlockingFor(input.getBlockingFor());
        if (m.getBlockingFor() != null) {
            for (String blockedName : m.getBlockingFor()) {
                Milestone blockedM = system.findMilestoneByName(blockedName);
                if (blockedM != null) blockedM.getDependsOn().add(m.getName());
            }
        }
        if (m.getTickets() != null) {
            for (Integer tid : m.getTickets()) {
                Ticket t = system.getTickets().get(tid);
                HistoryEntry h = new HistoryEntry();
                h.setAction("ADDED_TO_MILESTONE");
                h.setMilestone(m.getName());
                h.setBy(user.getUsername());
                h.setTimestamp(input.getTimestamp());
                t.addHistoryEntry(h);
            }
        }
        system.addMilestone(m);
    }

    private void handleAssignTicket(CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Tickets cannot be assigned during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.DEVELOPER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role DEVELOPER; user role " + user.getRole() + ".", input.getTimestamp());
            return;
        }
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + ticketId + " does not exist.", input.getTimestamp());
            return;
        }
        if (ticket.getStatus() != Status.OPEN) {
            addError(outputs, input.getCommand(), input.getUsername(), "Only OPEN tickets can be assigned.", input.getTimestamp());
            return;
        }
        Optional<Milestone> mOpt = system.getMilestones().stream().filter(m -> m.getTickets().contains(ticketId)).findFirst();
        if (mOpt.isEmpty()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Ticket ID " + ticketId + " is not assigned to any milestone.", input.getTimestamp());
            return;
        }
        Milestone m = mOpt.get();
        if (!m.getAssignedDevs().contains(user.getUsername())) {
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + user.getUsername() + " is not assigned to milestone " + m.getName() + ".", input.getTimestamp());
            return;
        }
        if (system.isMilestoneBlocked(m)) {
            addError(outputs, input.getCommand(), input.getUsername(), "Cannot assign ticket " + ticketId + " from blocked milestone " + m.getName() + ".", input.getTimestamp());
            return;
        }
        Developer dev = (Developer) user;
        if (!isExpertiseMatch(dev, ticket)) {
            String required = getRequiredExpertiseString(ticket.getExpertiseArea());
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + dev.getUsername() + " cannot assign ticket " + ticketId + " due to expertise area. Required: " + required + "; Current: " + dev.getExpertiseArea() + ".", input.getTimestamp());
            return;
        }
        if (!isSeniorityMatch(dev, ticket)) {
            String required = getRequiredSeniorityString(ticket);
            addError(outputs, input.getCommand(), input.getUsername(), "Developer " + dev.getUsername() + " cannot assign ticket " + ticketId + " due to seniority level. Required: " + required + "; Current: " + dev.getSeniority() + ".", input.getTimestamp());
            return;
        }
        ticket.setAssignedTo(user.getUsername());
        ticket.setAssignedAt(input.getTimestamp());
        HistoryEntry assignEntry = new HistoryEntry();
        assignEntry.setAction("ASSIGNED");
        assignEntry.setBy(user.getUsername());
        assignEntry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(assignEntry);
        ticket.setStatus(Status.IN_PROGRESS);
        HistoryEntry statusEntry = new HistoryEntry();
        statusEntry.setAction("STATUS_CHANGED");
        statusEntry.setFrom("OPEN");
        statusEntry.setTo("IN_PROGRESS");
        statusEntry.setBy(user.getUsername());
        statusEntry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(statusEntry);
    }

    private void handleReportTicket(CommandInput input, User user, List<ObjectNode> outputs) {
        if (system.getTestingPhaseStartDate() == null) {
            system.setTestingPhaseStartDate(input.getTimestamp());
            system.setTestingPhase(true);
        }
        if (!system.isTestingPhase()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Tickets can only be reported during testing phases.", input.getTimestamp());
            return;
        }
        if (user.getRole() != Role.REPORTER) {
            addError(outputs, input.getCommand(), input.getUsername(), "The user does not have permission to execute this command: required role REPORTER; user role " + user.getRole() + ".", input.getTimestamp());
            return;
        }
        JsonNode params = input.getParams();
        String reportedBy = params.has("reportedBy") ? params.get("reportedBy").asText() : "";
        if (reportedBy.isEmpty()) {
            String type = params.has("type") ? params.get("type").asText() : "";
            if (!"BUG".equals(type)) {
                addError(outputs, input.getCommand(), input.getUsername(), "Anonymous reports are only allowed for tickets of type BUG.", input.getTimestamp());
                return;
            }
        }
        int id = system.getNextTicketId();
        Ticket ticket = TicketFactory.createTicket(params, id, input.getTimestamp());
        if (ticket.getReportedBy().isEmpty()) ticket.setBusinessPriority(Priority.LOW);
        system.getTickets().put(id, ticket);
    }

    private void handleViewTickets(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> visibleTickets = new ArrayList<>();
        List<Ticket> allTickets = system.getTickets().values().stream().collect(Collectors.toList());
        if (user.getRole() == Role.MANAGER) {
            visibleTickets = allTickets;
        } else if (user.getRole() == Role.REPORTER) {
            visibleTickets = allTickets.stream().filter(t -> t.getReportedBy().equals(user.getUsername())).collect(Collectors.toList());
        } else if (user.getRole() == Role.DEVELOPER) {
            List<Milestone> devMilestones = system.getMilestones().stream().filter(m -> m.getAssignedDevs().contains(user.getUsername())).collect(Collectors.toList());
            Set<Integer> visibleTicketIds = new HashSet<>();
            for (Milestone m : devMilestones) visibleTicketIds.addAll(m.getTickets());
            visibleTickets = allTickets.stream().filter(t -> visibleTicketIds.contains(t.getId()) && t.getStatus() == Status.OPEN).collect(Collectors.toList());
        }
        visibleTickets.sort(Comparator.comparing(Ticket::getCreatedAt).thenComparingInt(Ticket::getId));
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewTickets");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode ticketsArray = mapper.createArrayNode();
        for (Ticket t : visibleTickets) {
            ObjectNode tNode = mapper.createObjectNode();
            tNode.put("id", t.getId());
            tNode.put("type", t.getType());
            tNode.put("title", t.getTitle());
            tNode.put("businessPriority", t.getBusinessPriority() != null ? t.getBusinessPriority().toString() : null);
            tNode.put("status", t.getStatus() != null ? t.getStatus().toString() : null);
            tNode.put("createdAt", t.getCreatedAt());
            tNode.put("assignedAt", t.getAssignedAt() == null ? "" : t.getAssignedAt());
            tNode.put("solvedAt", t.getSolvedAt() == null ? "" : t.getSolvedAt());
            tNode.put("assignedTo", t.getAssignedTo() == null ? "" : t.getAssignedTo());
            tNode.put("reportedBy", t.getReportedBy() == null ? "" : t.getReportedBy());
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            ticketsArray.add(tNode);
        }
        res.set("tickets", ticketsArray);
        outputs.add(res);
    }

    private void handleViewAssignedTickets(CommandInput input, User user, List<ObjectNode> outputs) {
        List<Ticket> assignedTickets = system.getTickets().values().stream()
                .filter(t -> user.getUsername().equals(t.getAssignedTo()))
                .collect(Collectors.toList());
        assignedTickets.sort((t1, t2) -> {
            int p1 = t1.getBusinessPriority().ordinal();
            int p2 = t2.getBusinessPriority().ordinal();
            if (p1 != p2) return Integer.compare(p2, p1);
            return Integer.compare(t1.getId(), t2.getId());
        });
        ObjectNode res = mapper.createObjectNode();
        res.put("command", "viewAssignedTickets");
        res.put("username", user.getUsername());
        res.put("timestamp", input.getTimestamp());
        ArrayNode arr = mapper.createArrayNode();
        for (Ticket t : assignedTickets) {
            ObjectNode tNode = mapper.createObjectNode();
            tNode.put("id", t.getId());
            tNode.put("type", t.getType());
            tNode.put("title", t.getTitle());
            tNode.put("businessPriority", t.getBusinessPriority().toString());
            tNode.put("status", t.getStatus().toString());
            tNode.put("createdAt", t.getCreatedAt());
            tNode.put("assignedAt", t.getAssignedAt());
            tNode.put("reportedBy", t.getReportedBy());
            tNode.set("comments", mapper.valueToTree(t.getComments()));
            arr.add(tNode);
        }
        res.set("assignedTickets", arr);
        outputs.add(res);
    }

    private void handleUndoAddComment(CommandInput input, User user, List<ObjectNode> outputs) {
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null) return;
        if (ticket.getReportedBy().isEmpty()) {
            addError(outputs, input.getCommand(), input.getUsername(), "Comments are not allowed on anonymous tickets.", input.getTimestamp());
            return;
        }
        if (user.getRole() == Role.REPORTER && !ticket.getReportedBy().equals(user.getUsername())) {
            addError(outputs, input.getCommand(), input.getUsername(), "Reporter " + user.getUsername() + " cannot comment on ticket " + ticketId + ".", input.getTimestamp());
            return;
        }
        List<Comment> comments = ticket.getComments();
        for (int i = comments.size() - 1; i >= 0; i--) {
            if (comments.get(i).getAuthor().equals(user.getUsername())) {
                comments.remove(i);
                return;
            }
        }
    }

    private void handleUndoAssignTicket(CommandInput input, User user, List<ObjectNode> outputs) {
        if (user.getRole() != Role.DEVELOPER) return;
        Integer ticketId = input.getTicketID();
        Ticket ticket = system.getTickets().get(ticketId);
        if (ticket == null || !user.getUsername().equals(ticket.getAssignedTo())) return;
        ticket.setAssignedTo(null);
        ticket.setAssignedAt(null);
        ticket.setStatus(Status.OPEN);
        HistoryEntry entry = new HistoryEntry();
        entry.setAction("DE-ASSIGNED");
        entry.setBy(user.getUsername());
        entry.setTimestamp(input.getTimestamp());
        ticket.addHistoryEntry(entry);
    }

    private boolean isExpertiseMatch(Developer dev, Ticket ticket) {
        ExpertiseArea devExp = dev.getExpertiseArea();
        ExpertiseArea ticketExp = ticket.getExpertiseArea();
        if (devExp == ExpertiseArea.FULLSTACK) return true;
        if (devExp == ticketExp) return true;
        if (devExp == ExpertiseArea.BACKEND && (ticketExp == ExpertiseArea.DB || ticketExp == ExpertiseArea.BACKEND)) return true;
        if (devExp == ExpertiseArea.FRONTEND && (ticketExp == ExpertiseArea.DESIGN || ticketExp == ExpertiseArea.FRONTEND)) return true;
        return false;
    }

    private String getRequiredExpertiseString(ExpertiseArea ticketExp) {
        if (ticketExp == ExpertiseArea.DB || ticketExp == ExpertiseArea.BACKEND) return "BACKEND, DB, FULLSTACK";
        if (ticketExp == ExpertiseArea.DESIGN || ticketExp == ExpertiseArea.FRONTEND) return "DESIGN, FRONTEND, FULLSTACK";
        if (ticketExp == ExpertiseArea.DEVOPS) return "DEVOPS, FULLSTACK";
        return ticketExp + ", FULLSTACK";
    }

    private boolean isSeniorityMatch(Developer dev, Ticket ticket) {
        Priority p = ticket.getBusinessPriority();
        Seniority s = dev.getSeniority();
        if (p == Priority.CRITICAL) return s == Seniority.SENIOR;
        if (p == Priority.HIGH) return s == Seniority.MID || s == Seniority.SENIOR;
        return true;
    }

    private String getRequiredSeniorityString(Ticket ticket) {
        Priority p = ticket.getBusinessPriority();
        if (p == Priority.CRITICAL) return "SENIOR";
        if (p == Priority.HIGH) return "MID, SENIOR";
        return "JUNIOR, MID, SENIOR";
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