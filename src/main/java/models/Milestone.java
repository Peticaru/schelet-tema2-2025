package models;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class Milestone {
    private String name;
    private String description;
    // use the exact names expected by command classes
    private String dueDate; // previously called deadline in some implementations
    private String createdAt;
    private List<Integer> tickets;
    private List<String> blockingFor; // milestones blocked by this one
    private List<String> assignedDevs;
    private String creator; // who created the milestone (manager username)
    private List<String> dependsOn; // keep for backward compatibility
}
