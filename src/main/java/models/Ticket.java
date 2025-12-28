package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public abstract class Ticket {
    private int id;
    private String type;
    private String title;
    private String description;
    private Priority businessPriority;
    private Status status;
    private ExpertiseArea expertiseArea;
    private String reportedBy;
    private String createdAt;
    private String assignedTo;
    private String assignedAt;
    private String solvedAt;

    private List<Comment> comments = new ArrayList<>();

    // Scoatem @JsonIgnore pentru că avem nevoie de el la viewTicketHistory,
    // dar îl vom gestiona manual în CommandRunner pentru viewTickets
    private List<HistoryEntry> history = new ArrayList<>();

    public void addHistoryEntry(HistoryEntry entry) {
        history.add(entry);
    }
}