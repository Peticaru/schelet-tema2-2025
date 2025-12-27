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

            // Gestionare tranzitie Blocat
            if (currentlyBlocked && !previouslyBlocked) {
                blockedMilestones.add(mName);
                updateTicketsStatusInMilestone(m, Status.BLOCKED, date, "Milestone '" + mName + "' became blocked");
                notifyDevs(m, "MILESTONE_BLOCKED: " + mName);
                continue;
            }

            // Gestionare tranzitie Deblocat
            if (!currentlyBlocked && previouslyBlocked) {
                blockedMilestones.remove(mName);
                handleUnblocking(m);
            }

            // Conform cerintei, regulile de escaladare nu se aplica daca milestone-ul este blocat
            if (currentlyBlocked) continue;

            // 1. Escaladarea priorității la fiecare 3 zile (Special Mention #1)
            // Calculul se face de la data CREĂRII MILESTONE-ULUI
            if (m.getCreatedAt() != null && m.getTickets() != null) {
                LocalDate mCreated = LocalDate.parse(m.getCreatedAt());
                // Formula daysBetween = date2 - date1 + 1
                long daysSinceMilestoneCreated = ChronoUnit.DAYS.between(mCreated, now) + 1;

                if (daysSinceMilestoneCreated >= 3) {
                    int expectedEscalations = (int) (daysSinceMilestoneCreated / 3);

                    for (Integer tid : m.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t == null || t.getStatus() == Status.CLOSED || t.getStatus() == Status.RESOLVED) continue;

                        // Numărăm câte escaladări de tip SYSTEM au fost deja aplicate acestui tichet
                        long appliedEscalations = t.getHistory().stream()
                                .filter(h -> "PRIORITY_ESCALATION".equals(h.getAction()))
                                .count();

                        while (appliedEscalations < expectedEscalations && t.getBusinessPriority() != Priority.CRITICAL) {
                            Priority next = t.getBusinessPriority().next();
                            t.setBusinessPriority(next);
                            t.addHistoryEntry(date, "SYSTEM", "PRIORITY_ESCALATION",
                                    "Priority increased due to time in milestone '" + mName + "' to " + next);
                            appliedEscalations++;

                            // Verificare: dacă noul nivel de prioritate depășește senioritatea dev-ului (Edge Case)
                            if (t.getAssignedTo() != null) {
                                User u = users.get(t.getAssignedTo());
                                if (u instanceof Developer && !canAccess((Developer) u, t)) {
                                    t.setAssignedTo(null);
                                    t.setStatus(Status.OPEN);
                                    t.addHistoryEntry(date, "SYSTEM", "AUTO_UNASSIGN",
                                            "Ticket unassigned: priority " + t.getBusinessPriority() + " exceeds dev seniority");
                                }
                            }
                        }
                    }
                }
            }

            // 2. Regula "O zi înainte de dueDate" (Special Mention #2)
            if (m.getDueDate() != null) {
                LocalDate due = LocalDate.parse(m.getDueDate());
                if (now.equals(due.minusDays(1))) {
                    boolean notified = false;
                    for (Integer tid : m.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t == null || t.getStatus() == Status.CLOSED || t.getStatus() == Status.RESOLVED) continue;

                        if (t.getBusinessPriority() != Priority.CRITICAL) {
                            t.setBusinessPriority(Priority.CRITICAL);
                            t.addHistoryEntry(date, "SYSTEM", "DEADLINE_IMMINENT_ESCALATION", "Escalated to CRITICAL - 1 day before due date");
                            notified = true;
                        }
                    }
                    if (notified) notifyDevs(m, "DEADLINE_IMMINENT_ESCALATION: " + mName);
                }
            }
        }
    }

    // Helper pentru a curăța codul principal
    private void updateTicketsStatusInMilestone(Milestone m, Status status, String date, String msg) {
        if (m.getTickets() == null) return;
        for (Integer tid : m.getTickets()) {
            Ticket t = tickets.get(tid);
            if (t != null && t.getStatus() != Status.CLOSED) {
                t.setStatus(status);
                t.addHistoryEntry(date, "SYSTEM", "STATUS_CHANGED", msg);
            }
        }
    }

    public double normalizeScore(double baseScore, double maxValue) {
        if (maxValue == 0) return 0.0;
        double score = Math.min(100.0, (baseScore * 100.0) / maxValue);
        return Math.round(score * 100.0) / 100.0; 
    }

    public double calculateAverage(List<Double> scores) {
        double average = scores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        return Math.round(average * 100.0) / 100.0;
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
        if (milestone == null || milestone.getAssignedDevs() == null) return;
        for (String devUsername : milestone.getAssignedDevs()) {
            notifyUser(devUsername, message);
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

    private void notifyUser(String username, String message) {
        User user = users.get(username);
        if (user != null) {
            user.addNotification(message);
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
