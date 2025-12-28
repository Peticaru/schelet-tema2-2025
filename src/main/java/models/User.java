package models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "role",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Manager.class, name = "MANAGER"),
        @JsonSubTypes.Type(value = Developer.class, name = "DEVELOPER"),
        @JsonSubTypes.Type(value = Reporter.class, name = "REPORTER")
})
public abstract class User {
    private String username;
    private String email;
    private Role role;

    // List to store notifications
    private List<String> notifications = new ArrayList<>();

    /**
     * Adds a new notification to the user's list.
     * @param notification The message string to add.
     */
    public void addNotification(String notification) {
        this.notifications.add(notification);
    }
}