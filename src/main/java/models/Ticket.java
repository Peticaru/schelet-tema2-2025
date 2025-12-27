package models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Bug.class, name = "BUG"),
    @JsonSubTypes.Type(value = FeatureRequest.class, name = "FEATURE_REQUEST"),
    @JsonSubTypes.Type(value = UIFeedback.class, name = "UI_FEEDBACK")
})
public abstract class Ticket {
    private int id;
    private String type;
    private String title;
    private Priority businessPriority;
    private Status status;
    private ExpertiseArea expertiseArea;
    private String description;
    private String reportedBy;
    private List<Comment> comments = new ArrayList<>();
    private String createdAt;
    private String assignedTo;
    private String solvedAt;
    private String assignedAt;
    private List<HistoryEntry> history = new ArrayList<>();

    public void addHistoryEntry(String date, String user, String action, String description) {
        history.add(new HistoryEntry(date, user, action + ": " + description));
    }
}
