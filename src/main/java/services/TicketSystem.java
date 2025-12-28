package services;

import lombok.Getter;
import lombok.Setter;
import models.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Getter
@Setter
public class TicketSystem {
    private static TicketSystem instance;

    private TicketSystem() {}

    public static synchronized TicketSystem getInstance() {
        if (instance == null) {
            instance = new TicketSystem();
        }
        return instance;
    }

    private Map<String, User> users = new HashMap<>();
    private Map<Integer, Ticket> tickets = new HashMap<>();
    private List<Milestone> milestones = new ArrayList<>();

    private int ticketIdCounter = 0;
    private String currentDate;
    private boolean testingPhase = true;
    private boolean investorsLost = false;
    private String testingPhaseStartDate;

    private Set<String> blockedMilestones = new HashSet<>();

    public void reset() {
        this.users.clear();
        this.tickets.clear();
        this.milestones.clear();
        this.blockedMilestones.clear();
        this.ticketIdCounter = 0;
        this.currentDate = null;
        this.testingPhase = true;
        this.investorsLost = false;
        this.testingPhaseStartDate = null;
    }

    public void loadUsers(List<User> inputUsers) {
        if (inputUsers != null) {
            for (User user : inputUsers) {
                this.users.put(user.getUsername(), user);
            }
        }
    }

    public void updateTime(String timestamp) {
        this.currentDate = timestamp;

        if (this.testingPhase && this.testingPhaseStartDate != null) {
            try {
                LocalDate start = LocalDate.parse(this.testingPhaseStartDate);
                LocalDate now = LocalDate.parse(timestamp);
                long daysDiff = ChronoUnit.DAYS.between(start, now) + 1;

                if (daysDiff > 12) {
                    this.testingPhase = false;
                }
            } catch (DateTimeParseException e) { }
        }

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

            if (currentlyBlocked && !previouslyBlocked) {
                blockedMilestones.add(mName);
                // notifyDevs(m, "MILESTONE_BLOCKED: " + mName); // Optional, depinde de teste
                continue;
            }

            if (!currentlyBlocked && previouslyBlocked) {
                blockedMilestones.remove(mName);
                handleUnblocking(m);
            }

            if (currentlyBlocked) continue;

            // 1. Escaladare Prioritate (la 3 zile)
            if (m.getCreatedAt() != null && m.getTickets() != null) {
                LocalDate mCreated = LocalDate.parse(m.getCreatedAt());
                long pureDaysDiff = ChronoUnit.DAYS.between(mCreated, now);

                // Folosim IF, nu WHILE, pentru a evita spam-ul în istoric la tichetele redeschise
                if (pureDaysDiff > 0 && pureDaysDiff % 3 == 0) {
                    for (Integer tid : m.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t == null || t.getStatus() == Status.CLOSED || t.getStatus() == Status.RESOLVED) continue;

                        if (t.getBusinessPriority() != Priority.CRITICAL) {
                            Priority next = t.getBusinessPriority().next();
                            t.setBusinessPriority(next);

                            HistoryEntry entry = new HistoryEntry();
                            entry.setTimestamp(date);
                            entry.setBy("SYSTEM");
                            entry.setAction("PRIORITY_ESCALATION");
                            entry.setDescription("Priority increased due to time in milestone '" + mName + "' to " + next);
                            t.addHistoryEntry(entry);

                            checkDevAccess(t, date);
                        }
                    }
                }
            }

            // 2. Deadline Imminent (1 zi înainte)
            if (m.getDueDate() != null) {
                LocalDate due = LocalDate.parse(m.getDueDate());
                if (ChronoUnit.DAYS.between(now, due) == 1) {
                    boolean notified = false;
                    for (Integer tid : m.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t != null && t.getStatus() != Status.CLOSED && t.getStatus() != Status.RESOLVED) {
                            if (t.getBusinessPriority() != Priority.CRITICAL) {
                                t.setBusinessPriority(Priority.CRITICAL);

                                HistoryEntry entry = new HistoryEntry();
                                entry.setTimestamp(date);
                                entry.setBy("SYSTEM");
                                entry.setAction("DEADLINE_IMMINENT_ESCALATION");
                                entry.setDescription("Escalated to CRITICAL - 1 day before due date");
                                t.addHistoryEntry(entry);

                                checkDevAccess(t, date);
                                notified = true;
                            }
                        }
                    }
                    if (notified || hasUnresolvedTickets(m)) {
                        notifyDevs(m, "Milestone " + mName + " is due tomorrow. All unresolved tickets are now CRITICAL.");
                    }
                }
            }
        }
    }

    private boolean hasUnresolvedTickets(Milestone m) {
        if (m.getTickets() == null) return false;
        for (Integer id : m.getTickets()) {
            Ticket t = tickets.get(id);
            if (t != null && t.getStatus() != Status.CLOSED && t.getStatus() != Status.RESOLVED) return true;
        }
        return false;
    }

    public void addMilestone(Milestone m) {
        this.milestones.add(m);
    }

    public Milestone findMilestoneByName(String name) {
        return milestones.stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }

    public int getNextTicketId() {
        return ticketIdCounter++;
    }

    private void checkDevAccess(Ticket t, String date) {
        if (t.getAssignedTo() != null) {
            User u = users.get(t.getAssignedTo());
            if (u instanceof Developer && !canAccess((Developer) u, t)) {
                t.setAssignedTo(null);
                t.setStatus(Status.OPEN);

                HistoryEntry entry = new HistoryEntry();
                entry.setTimestamp(date);
                entry.setBy("SYSTEM");
                entry.setAction("AUTO_UNASSIGN");
                entry.setDescription("Ticket unassigned: priority " + t.getBusinessPriority() + " exceeds dev seniority");
                t.addHistoryEntry(entry);
            }
        }
    }

    // Facem metoda publică pentru acces din CommandRunner (validare asignare)
    public boolean isMilestoneBlocked(Milestone milestone) {
        if (milestone == null) return false;
        if (milestone.getDependsOn() != null) {
            for (String depName : milestone.getDependsOn()) {
                Milestone dep = findMilestoneByName(depName);
                if (dep == null) continue;
                boolean allClosed = true;
                if (dep.getTickets() != null) {
                    for (Integer tid : dep.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t != null && t.getStatus() != Status.CLOSED) {
                            allClosed = false;
                            break;
                        }
                    }
                }
                if (!allClosed) return true;
            }
        }
        return false;
    }

    // Facem metoda publică pentru acces din CommandRunner (notificare la creare)
    public void notifyDevs(Milestone milestone, String message) {
        if (milestone == null || milestone.getAssignedDevs() == null) return;
        for (String devUsername : milestone.getAssignedDevs()) {
            User user = users.get(devUsername);
            if (user != null) user.addNotification(message);
        }
    }

    private void handleUnblocking(Milestone milestone) {
        if (milestone == null) return;

        boolean overdue = false;
        if (milestone.getDueDate() != null && currentDate != null) {
            LocalDate due = LocalDate.parse(milestone.getDueDate());
            LocalDate now = LocalDate.parse(currentDate);
            if (now.isAfter(due)) overdue = true;
        }

        if (milestone.getTickets() != null) {
            for (Integer tid : milestone.getTickets()) {
                Ticket t = tickets.get(tid);
                if (t != null && t.getStatus() == Status.BLOCKED) {
                    t.setStatus(t.getAssignedTo() != null ? Status.IN_PROGRESS : Status.OPEN);

                    HistoryEntry entry = new HistoryEntry();
                    entry.setTimestamp(currentDate);
                    entry.setBy("SYSTEM");
                    entry.setAction("MILESTONE_UNBLOCKED");
                    entry.setDescription("Milestone unblocked");
                    t.addHistoryEntry(entry);
                }

                if (overdue && t != null && t.getStatus() != Status.CLOSED && t.getStatus() != Status.RESOLVED) {
                    if (t.getBusinessPriority() != Priority.CRITICAL) {
                        t.setBusinessPriority(Priority.CRITICAL);
                        // Opțional istoric aici
                    }
                }
            }
        }

        if (overdue) {
            notifyDevs(milestone, "Milestone " + milestone.getName() + " was unblocked after due date. All active tickets are now CRITICAL.");
        }
    }

    public boolean canAccess(Developer developer, Ticket ticket) {
        if (developer == null || ticket == null) return false;

        boolean expertiseMatch = false;
        ExpertiseArea devExp = developer.getExpertiseArea();
        ExpertiseArea ticketExp = ticket.getExpertiseArea();

        if (devExp == ExpertiseArea.FULLSTACK) expertiseMatch = true;
        else if (devExp == ticketExp) expertiseMatch = true;

        if (!expertiseMatch && devExp != ExpertiseArea.FULLSTACK) {
            if (devExp == ExpertiseArea.BACKEND && (ticketExp == ExpertiseArea.DB || ticketExp == ExpertiseArea.BACKEND)) expertiseMatch = true;
            if (devExp == ExpertiseArea.FRONTEND && (ticketExp == ExpertiseArea.DESIGN || ticketExp == ExpertiseArea.FRONTEND)) expertiseMatch = true;
        }

        Priority p = ticket.getBusinessPriority();
        Seniority s = developer.getSeniority();
        if (p == Priority.CRITICAL && s != Seniority.SENIOR) return false;
        if (p == Priority.HIGH && s == Seniority.JUNIOR) return false;

        return expertiseMatch;
    }
}