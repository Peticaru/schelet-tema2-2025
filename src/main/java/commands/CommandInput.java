package commands;

import lombok.Data;
import lombok.NoArgsConstructor;
import models.BusinessValue;
import models.CustomerDemand;
import models.Frequency;
import models.Severity;

@Data
@NoArgsConstructor
public class CommandInput {
    // --- Câmpuri Generale ---
    private String command;
    private String username;
    private String timestamp; // Jackson îl va citi ca String, noi îl parsam când avem nevoie

    // --- Câmpuri pentru Tichete (reportTicket) ---
    private String type;
    private String title;
    private String description;
    private BusinessValue businessPriority; // sau Priority, verifică enum-ul
    private String reportedBy;

    // Specifice BUG
    private String expectedBehavior;
    private String actualBehavior;
    private Frequency frequency;
    private Severity severity;
    private String environment;
    private Integer errorCode; // Integer ca să poată fi null

    // Specifice FEATURE_REQUEST
    private BusinessValue businessValue;
    private CustomerDemand customerDemand;

    // Specifice UI_FEEDBACK
    private String uiElementId;
    private Integer usabilityScore;
    private String screenshotUrl;
    private String suggestedFix;

    // --- Câmpuri pentru Alte Comenzi ---
    private Integer ticketId; // Pentru viewTicket, addComment, etc.
    private String status;    // Pentru changeStatus

    // --- Câmpuri pentru Comentarii ---
    private String comment;   // Sau "content" în funcție de input JSON

    // --- Câmpuri pentru Milestone ---
    private String milestoneName;
    private String dueDate;
    // Jackson poate mapa un array JSON direct într-o Listă
    // private List<Integer> ticketIds;
}