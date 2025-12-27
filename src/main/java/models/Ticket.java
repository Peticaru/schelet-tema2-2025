package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class Ticket {
    private int id;
    private String type; // BUG, FEATURE_REQUEST, etc.
    private String title;
    private String description;

    // Folosim String pentru simplitate la output,
    // sau Enum dacă vrei logică strictă. Recomand Enum convertit la String.
    private Status status;
    private Priority businessPriority; // LOW, MEDIUM, HIGH, CRITICAL

    private String reportedBy;
    private String createdAt; // Timestamp-ul creării

    // Câmpuri care se schimbă pe parcurs
    private String assignedTo; // Username-ul developer-ului
    private String milestone;  // Numele milestone-ului

    // Câmpuri ajutătoare (nu neapărat în JSON output, dar utile logic)
    @JsonIgnore
    private boolean active = true;
}