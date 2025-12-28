package models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryEntry {
    private String milestone; // Pentru ADDED_TO_MILESTONE
    private String from;      // Pentru STATUS_CHANGED
    private String to;        // Pentru STATUS_CHANGED
    private String by;
    private String timestamp;
    private String action;    // ADDED_TO_MILESTONE, ASSIGNED, STATUS_CHANGED, PRIORITY_ESCALATION etc.
    private String description; // Adăugat pentru mesajele de sistem
}