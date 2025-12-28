package models;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Milestone {
    private String name;
    private String dueDate;
    private String createdAt;
    private String createdBy; // Manager username

    private List<Integer> tickets = new ArrayList<>();
    private List<String> assignedDevs = new ArrayList<>();

    // Logic fields
    private List<String> blockingFor = new ArrayList<>(); // Milestones blocked by this one
    private List<String> dependsOn = new ArrayList<>();   // Milestones this one depends on (calculated from blockingFor of others)
}