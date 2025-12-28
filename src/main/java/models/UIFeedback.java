package models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UIFeedback extends Ticket {
    private String uiElementId;
    private BusinessValue businessValue;
    private Integer usabilityScore;
    private String screenshotUrl;
    private String suggestedFix;
}