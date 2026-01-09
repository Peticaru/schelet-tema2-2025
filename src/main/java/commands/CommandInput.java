package commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommandInput {
    private String command;
    private String username;
    private String timestamp;
    private JsonNode params;
    private String name;
    private String dueDate;
    private List<Integer> tickets;
    private List<String> blockingFor;
    private List<String> assignedDevs;
    private String comment;
    private Integer ticketID;
    private String status; // Asigura-te ca acest camp exista
    private JsonNode filters;
}