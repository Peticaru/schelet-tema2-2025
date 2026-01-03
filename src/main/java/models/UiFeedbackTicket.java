package models;

import lombok.Getter;
import lombok.Setter;
import visitor.Visitor;

@Getter
@Setter
public class UiFeedbackTicket extends Ticket {
    private String uiElementId;
    private BusinessValue businessValue;
    private Integer usabilityScore;
    private String screenshotUrl;
    private String suggestedFix;

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}