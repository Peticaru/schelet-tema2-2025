package commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommandInput {
    private String command;
    private String username;
    private String timestamp;
    private JsonNode params;

    // --- Fields for Milestone ---
    private String name;        // for createMilestone
    private String dueDate;

    // JSON input uses "tickets", NOT "ticketIds"
    private List<Integer> tickets;

    private List<String> blockingFor;
    private List<String> assignedDevs;

    private String comment;

    private Integer ticketID;

    private JsonNode filters;
}