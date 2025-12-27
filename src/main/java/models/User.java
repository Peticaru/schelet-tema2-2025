package models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "role",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Reporter.class, name = "REPORTER"),
    @JsonSubTypes.Type(value = Developer.class, name = "DEVELOPER"),
    @JsonSubTypes.Type(value = Manager.class, name = "MANAGER")
})
public abstract class User {
    private String username;
    private Role role;
    private String email;
}
