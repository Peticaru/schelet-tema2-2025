package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Milestone;
import models.Role;
import models.Status;
import models.Ticket;
import models.User;
import services.TicketSystem;

import java.util.List;

public class StartTestingPhase extends BaseCommand {
    @Override
    public void execute(TicketSystem system, CommandInput input, User user, List<ObjectNode> outputs) {
        // 1. Verificare permisiuni: Doar MANAGER poate executa comanda
        if (user.getRole() != Role.MANAGER) {
            addError(outputs, input.getCommand(), input.getUsername(),
                    "The user does not have permission to execute this command: required role MANAGER; user role " + user.getRole(),
                    input.getTimestamp());
            return;
        }

        // 2. Verificare dacă există Milestone-uri active
        // O nouă fază de testare poate începe doar dacă NU există tichete nerezolvate (Status != CLOSED) în niciun milestone.
        boolean hasActiveMilestones = false;

        if (system.getMilestones() != null) {
            for (Milestone m : system.getMilestones()) {
                // Verificăm tichetele din acest milestone
                if (m.getTickets() != null) {
                    for (Integer ticketId : m.getTickets()) {
                        Ticket t = system.getTickets().get(ticketId);
                        // Dacă găsim un tichet care nu e CLOSED, milestone-ul e încă activ
                        if (t != null && t.getStatus() != Status.CLOSED) {
                            hasActiveMilestones = true;
                            break;
                        }
                    }
                }
                if (hasActiveMilestones) {
                    break;
                }
            }
        }

        // Dacă există milestone-uri active, afișăm eroarea specifică
        if (hasActiveMilestones) {
            addError(outputs, input.getCommand(), input.getUsername(), "Cannot start a new testing phase.", input.getTimestamp());
            return;
        }

        // 3. Dacă totul e ok, pornim faza de testare
        // Aceasta nu generează output în lista 'outputs' dacă are succes (void command)
        system.setTestingPhase(true);
        system.setTestingPhaseStartDate(input.getTimestamp());
    }
}