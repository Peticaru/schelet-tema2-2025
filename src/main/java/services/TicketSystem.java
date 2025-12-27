package services;

import lombok.Getter;
import lombok.Setter;
import models.Milestone;
import models.Ticket;
import models.User;
import models.Developer;
import models.Priority;
import models.Seniority;
import models.Status;
import models.ExpertiseArea;
import models.Bug;
import models.Severity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Getter
@Setter
public class TicketSystem {
    private Map<String, User> users = new HashMap<>();
    private Map<Integer, Ticket> tickets = new HashMap<>();
    private List<Milestone> milestones = new ArrayList<>();
    private int ticketIdCounter = 1;
    private String currentDate;
    private boolean testingPhase = false;
    private boolean investorsLost = false;
    private String testingPhaseStartDate;

    // track blocked milestones to detect transitions
    private Set<String> blockedMilestones = new HashSet<>();

    public void updateTime(String timestamp) {
        this.currentDate = timestamp;
        processDayUpdate(timestamp);
    }

    private void processDayUpdate(String date) {
        if (date == null) return;
        LocalDate now;
        try {
            now = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return;
        }

        for (Milestone m : milestones) {
            if (m == null) continue;
            String mName = m.getName();
            boolean currentlyBlocked = isMilestoneBlocked(m);
            boolean previouslyBlocked = blockedMilestones.contains(mName);

            // milestone became blocked
            if (currentlyBlocked && !previouslyBlocked) {
                blockedMilestones.add(mName);
                if (m.getTickets() != null) {
                    for (Integer tid : m.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t == null) continue;
                        if (t.getStatus() != Status.CLOSED && t.getStatus() != Status.BLOCKED) {
                            t.setStatus(Status.BLOCKED);
                            t.addHistoryEntry(date, "SYSTEM", "MILESTONE_BLOCKED",
                                    "Milestone '" + mName + "' became blocked");
                        }
                    }
                }
                notifyDevs(m, "MILESTONE_BLOCKED: " + mName);
                continue; // do not apply escalations while blocked
            }

            // milestone became unblocked
            if (!currentlyBlocked && previouslyBlocked) {
                blockedMilestones.remove(mName);
                handleUnblocking(m);
            }

            // skip escalations for blocked milestones
            if (currentlyBlocked) continue;

            // 3-day priority escalation
            if (m.getTickets() != null) {
                for (Integer tid : m.getTickets()) {
                    Ticket t = tickets.get(tid);
                    if (t == null) continue;
                    Status s = t.getStatus();
                    if (s == Status.OPEN || s == Status.IN_PROGRESS) {
                        if (t.getCreatedAt() == null) continue;
                        LocalDate created;
                        try {
                            created = LocalDate.parse(t.getCreatedAt());
                        } catch (DateTimeParseException ex) {
                            continue;
                        }
                        int daysBetween = (int) ChronoUnit.DAYS.between(created, now) + 1;
                        if (daysBetween >= 3) {
                            int escalationsDue = daysBetween / 3;
                            long previousEsc = t.getHistory().stream()
                                    .map(h -> h.getAction())
                                    .filter(a -> a != null && a.startsWith("PRIORITY_ESCALATION"))
                                    .count();
                            int toApply = escalationsDue - (int) previousEsc;
                            for (int i = 0; i < toApply; i++) {
                                if (t.getBusinessPriority() == Priority.CRITICAL) break;
                                Priority next = t.getBusinessPriority().next();
                                t.setBusinessPriority(next);
                                t.addHistoryEntry(date, "SYSTEM", "PRIORITY_ESCALATION",
                                        "Priority increased due to time in milestone '" + mName + "' to " + next);
                                // if assigned developer can no longer handle priority -> auto-unassign
                                if (t.getAssignedTo() != null) {
                                    User u = users.get(t.getAssignedTo());
                                    if (u instanceof Developer) {
                                        Developer dev = (Developer) u;
                                        if (!canAccess(dev, t)) {
                                            t.setAssignedTo(null);
                                            t.setStatus(Status.OPEN);
                                            t.addHistoryEntry(date, "SYSTEM", "AUTO_UNASSIGN",
                                                    "Ticket unassigned due to priority exceeding developer seniority");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1 day before dueDate escalate remaining tickets to CRITICAL
            if (m.getDueDate() != null) {
                try {
                    LocalDate due = LocalDate.parse(m.getDueDate());
                    LocalDate dayBefore = due.minusDays(1);
                    if (now.equals(dayBefore)) {
                        boolean anyEsc = false;
                        if (m.getTickets() != null) {
                            for (Integer tid : m.getTickets()) {
                                Ticket t = tickets.get(tid);
                                if (t == null) continue;
                                if (t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS) {
                                    if (t.getBusinessPriority() != Priority.CRITICAL) {
                                        t.setBusinessPriority(Priority.CRITICAL);
                                        t.addHistoryEntry(date, "SYSTEM", "DEADLINE_IMMINENT_ESCALATION",
                                                "Escalated to CRITICAL due to imminent deadline for milestone '" + mName + "'");
                                        anyEsc = true;
                                    }
                                }
                            }
                        }
                        if (anyEsc) notifyDevs(m, "DEADLINE_IMMINENT_ESCALATION: " + mName);
                    }
                } catch (DateTimeParseException ex) {
                    // ignore malformed dates
                }
            }
        }
    }

    private boolean isMilestoneBlocked(Milestone milestone) {
        if (milestone == null) return false;
        // blocked if any dependency has non-CLOSED ticket
        if (milestone.getDependsOn() != null) {
            for (String depName : milestone.getDependsOn()) {
                Milestone dep = findMilestoneByName(depName);
                if (dep == null) continue;
                if (dep.getTickets() != null) {
                    for (Integer tid : dep.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t == null) continue;
                        if (t.getStatus() != Status.CLOSED) return true;
                    }
                }
            }
        }
        // also blocked if any ticket inside is BLOCKED
        if (milestone.getTickets() != null) {
            for (Integer tid : milestone.getTickets()) {
                Ticket t = tickets.get(tid);
                if (t == null) continue;
                if (t.getStatus() == Status.BLOCKED) return true;
            }
        }
        return false;
    }

    private void checkSeniorityOverflow(Ticket ticket) {
        if (ticket == null || ticket.getBusinessPriority() == Priority.CRITICAL) {
            return;
        }

        long openTicketsCount = tickets.values().stream()
                .filter(t -> t.getBusinessPriority() == ticket.getBusinessPriority())
                .filter(t -> t.getStatus() == Status.OPEN || t.getStatus() == Status.IN_PROGRESS)
                .count();

        if (openTicketsCount > 5) {
            ticket.setBusinessPriority(ticket.getBusinessPriority().next());
            ticket.addHistoryEntry(currentDate, "SYSTEM", "PRIORITY_ESCALATION", 
                "Priority increased due to high volume of " + ticket.getBusinessPriority() + " tickets");
        }
    }

    public boolean canAccess(Developer developer, Ticket ticket) {
        if (developer == null || ticket == null) {
            return false;
        }

        Priority priority = ticket.getBusinessPriority();
        Seniority seniority = developer.getSeniority();
        boolean expertiseMatch = isExpertiseMatch(developer, ticket);

        if (!expertiseMatch) {
            return false;
        }

        // Ticket type specific restrictions
        if (ticket.getType().equals("FEATURE_REQUEST") && seniority == Seniority.JUNIOR) {
            return false;
        }

        if (ticket instanceof Bug) {
            Bug bug = (Bug) ticket;
            if (bug.getSeverity() == Severity.SEVERE && seniority == Seniority.JUNIOR) {
                return false;
            }
        }

        // Check Seniority vs Priority
        if (priority == Priority.CRITICAL) {
            return seniority == Seniority.SENIOR;
        }
        if (priority == Priority.HIGH) {
            return seniority == Seniority.SENIOR || seniority == Seniority.MID;
        }
        return true;
    }

    private static boolean isExpertiseMatch(Developer developer, Ticket ticket) {
        ExpertiseArea devExpertise = developer.getExpertiseArea();
        ExpertiseArea ticketExpertise = ticket.getExpertiseArea();

        // Check ExpertiseArea
        boolean expertiseMatch = switch (devExpertise) {
            case FRONTEND -> (ticketExpertise == ExpertiseArea.FRONTEND || ticketExpertise == ExpertiseArea.DESIGN);
            case BACKEND -> (ticketExpertise == ExpertiseArea.BACKEND || ticketExpertise == ExpertiseArea.DB);
            case FULLSTACK -> (ticketExpertise == ExpertiseArea.FRONTEND || ticketExpertise == ExpertiseArea.BACKEND
                    || ticketExpertise == ExpertiseArea.DEVOPS || ticketExpertise == ExpertiseArea.DESIGN
                    || ticketExpertise == ExpertiseArea.DB || ticketExpertise == ExpertiseArea.FULLSTACK);
            case DEVOPS -> (ticketExpertise == ExpertiseArea.DEVOPS);
            case DESIGN -> (ticketExpertise == ExpertiseArea.DESIGN || ticketExpertise == ExpertiseArea.FRONTEND);
            case DB -> (ticketExpertise == ExpertiseArea.DB);
            default -> (devExpertise == ticketExpertise);
        };
        return expertiseMatch;
    }

    private void notifyDevs(Milestone milestone, String message) {
        if (milestone == null || milestone.getTickets() == null) return;
        for (Integer tid : milestone.getTickets()) {
            Ticket t = tickets.get(tid);
            if (t == null) continue;
            if (t.getAssignedTo() != null) {
                t.addHistoryEntry(currentDate != null ? currentDate : "", "SYSTEM", "NOTIFICATION", message);
            }
        }
    }

    public void handleTicketClosed(int ticketId) {
        Ticket t = tickets.get(ticketId);
        if (t == null) return;
        if (t.getStatus() == Status.CLOSED) return;
        t.setStatus(Status.CLOSED);
        if (t.getSolvedAt() == null) t.setSolvedAt(currentDate);
        t.addHistoryEntry(currentDate != null ? currentDate : "", "SYSTEM", "STATUS_CHANGED", "Ticket closed");

        for (Milestone m : milestones) {
            if (m.getTickets() != null && m.getTickets().contains(ticketId)) {
                boolean allClosed = true;
                for (Integer tid : m.getTickets()) {
                    Ticket other = tickets.get(tid);
                    if (other != null && other.getStatus() != Status.CLOSED) {
                        allClosed = false;
                        break;
                    }
                }
                if (allClosed) {
                    notifyDevs(m, "MILESTONE_COMPLETED: " + m.getName());
                    for (Milestone dep : milestones) {
                        if (dep.getDependsOn() != null && dep.getDependsOn().contains(m.getName())) {
                            if (blockedMilestones.contains(dep.getName())) {
                                if (!isMilestoneBlocked(dep)) {
                                    blockedMilestones.remove(dep.getName());
                                    handleUnblocking(dep);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleUnblocking(Milestone milestone) {
        if (milestone == null) return;
        if (isMilestoneBlocked(milestone)) return;

        LocalDate now;
        try {
            if (currentDate == null) return;
            now = LocalDate.parse(currentDate);
        } catch (DateTimeParseException e) {
            return;
        }

        // If unblocked after dueDate -> escalate remaining to CRITICAL and notify
        if (milestone.getDueDate() != null) {
            try {
                LocalDate due = LocalDate.parse(milestone.getDueDate());
                if (now.isAfter(due)) {
                    boolean any = false;
                    if (milestone.getTickets() != null) {
                        for (Integer tid : milestone.getTickets()) {
                            Ticket t = tickets.get(tid);
                            if (t == null) continue;
                            if (t.getStatus() != Status.CLOSED && t.getBusinessPriority() != Priority.CRITICAL) {
                                t.setBusinessPriority(Priority.CRITICAL);
                                t.addHistoryEntry(currentDate, "SYSTEM", "LATE_UNBLOCK_ESCALATION",
                                        "Escalated to CRITICAL due to late unblocking of milestone '" + milestone.getName() + "'");
                                any = true;
                            }
                        }
                    }
                    if (any) notifyDevs(milestone, "LATE_UNBLOCK_ESCALATION: " + milestone.getName());
                    return;
                }
            } catch (DateTimeParseException e) {
                // ignore
            }
        }

        if (milestone.getTickets() != null) {
            for (Integer tid : milestone.getTickets()) {
                Ticket t = tickets.get(tid);
                if (t == null) continue;
                if (t.getStatus() != Status.CLOSED) {
                    if (t.getAssignedTo() != null) {
                        t.setStatus(Status.IN_PROGRESS);
                    } else {
                        t.setStatus(Status.OPEN);
                    }
                    t.addHistoryEntry(currentDate, "SYSTEM", "MILESTONE_UNBLOCKED",
                            "Milestone '" + milestone.getName() + "' was unblocked");
                }
            }
        }
        notifyDevs(milestone, "MILESTONE_UNBLOCKED: " + milestone.getName());
    }

    private Milestone findMilestoneByName(String name) {
        return milestones.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public boolean isTicketBlocked(int ticketId) {
        Ticket t = tickets.get(ticketId);
        if (t == null) return false;
        if (t.getStatus() == Status.BLOCKED) return true;
        for (Milestone m : milestones) {
            if (m.getTickets() != null && m.getTickets().contains(ticketId)) {
                return isMilestoneBlocked(m);
            }
        }
        return false;
    }

    public int getNextTicketId() {
        return ticketIdCounter++;
    }
}
