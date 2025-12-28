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
                notifyDevs(m, "MILESTONE_BLOCKED: " + mName);
                continue;
            }

            if (!currentlyBlocked && previouslyBlocked) {
                blockedMilestones.remove(mName);
                handleUnblocking(m);
            }

            if (currentlyBlocked) continue;

            // 1. Escaladare Prioritate
            if (m.getCreatedAt() != null && m.getTickets() != null) {
                LocalDate mCreated = LocalDate.parse(m.getCreatedAt());
                long pureDaysDiff = ChronoUnit.DAYS.between(mCreated, now);

                // FIX: Strict check logic to prevent catch-up escalations for reopened tickets
                if (pureDaysDiff > 0 && pureDaysDiff % 3 == 0) {
                    for (Integer tid : m.getTickets()) {
                        Ticket t = tickets.get(tid);
                        if (t == null || t.getStatus() == Status.CLOSED || t.getStatus() == Status.RESOLVED) continue;

                        // Apply SINGLE escalation if not critical
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

            // 2. Deadline Imminent
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
                    if (notified) notifyDevs(m, "DEADLINE_IMMINENT_ESCALATION: " + mName);
                }
            }
        }
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

    private void updateTicketsStatusInMilestone(Milestone m, Status status, String date, String msg) {
        if (m.getTickets() == null) return;
        for (Integer tid : m.getTickets()) {
            Ticket t = tickets.get(tid);
            if (t != null && t.getStatus() != Status.CLOSED) {
                t.setStatus(status);
                t.addHistoryEntry(new HistoryEntry(null, null, null, "SYSTEM", date, "STATUS_CHANGED", msg));
            }
        }
    }

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

    private void notifyDevs(Milestone milestone, String message) {
        if (milestone == null || milestone.getAssignedDevs() == null) return;
        for (String devUsername : milestone.getAssignedDevs()) {
            User user = users.get(devUsername);
            if (user != null) user.addNotification(message);
        }
    }

    private void handleUnblocking(Milestone milestone) {
        if (milestone == null) return;
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
            }
        }
        notifyDevs(milestone, "MILESTONE_UNBLOCKED: " + milestone.getName());
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