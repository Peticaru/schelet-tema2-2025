package services;

import models.Milestone;
import modelsarchive.Ticket;
import models.User;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketSystem {
    // Instanța unică (Singleton)
    private static TicketSystem instance;

    // --- Baza de date (Listele de date) ---
    private List<User> users;
    private List<Ticket> tickets;
    private List<Milestone> milestones;

    // --- Starea Aplicației (Flags) ---

    // true = Perioada de Testare (Reporterii pot adăuga bug-uri)
    // false = Perioada de Dezvoltare (Devii rezolvă bug-uri)
    private boolean activeTestingPhase;

    // true = Aplicația rulează normal
    // false = "lostInvestors" a fost apelat, nu se mai execută nimic
    private boolean active;

    // Folosit pentru raportul de stabilitate final
    private boolean appStable;

    // Constructor privat (nimeni nu poate face new TicketSystem() în afară de noi)
    private TicketSystem() {
        this.users = new ArrayList<>();
        this.tickets = new ArrayList<>();
        this.milestones = new ArrayList<>();

        this.activeTestingPhase = true;
        this.active = true;
        this.appStable = false;
    }

    public static synchronized TicketSystem getInstance() {
        if (instance == null) {
            instance = new TicketSystem();
        }
        return instance;
    }

    public void reset() {
        this.users.clear();
        this.tickets.clear();
        this.milestones.clear();

        this.activeTestingPhase = true;
        this.active = true;
        this.appStable = false;
    }


    public void loadUsers(List<User> inputUsers) {
        if (inputUsers != null) {
            this.users.addAll(inputUsers);
        }
    }

    public User getUser(String username) {
        if (username == null) return null;
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }


    public void addTicket(Ticket ticket) {
        this.tickets.add(ticket);
    }

    public Ticket getTicket(int id) {
        return tickets.stream()
                .filter(t -> t.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public int getNextTicketId() {
        return tickets.size();
    }

    public void addMilestone(Milestone milestone) {
        this.milestones.add(milestone);
    }

    public Milestone getMilestone(String name) {
        return milestones.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElse(null);
    }


    public boolean isTestingPhase() {
        return activeTestingPhase;
    }

    public void setTestingPhase(boolean activeTestingPhase) {
        this.activeTestingPhase = activeTestingPhase;
    }

}