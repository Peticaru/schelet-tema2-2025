package models;

import lombok.Getter;
import lombok.Setter;
import visitor.Visitor;


@Getter
@Setter
public class BugTicket extends Ticket {
    private String expectedBehavior;
    private String actualBehavior;
    private Frequency frequency;
    private Severity severity;
    private String environment;
    private Integer errorCode;

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}