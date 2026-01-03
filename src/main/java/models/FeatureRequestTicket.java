package models;

import lombok.Getter;
import lombok.Setter;
import visitor.Visitor;

@Getter
@Setter
public class FeatureRequestTicket extends Ticket {
    private BusinessValue businessValue;
    private CustomerDemand customerDemand;

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}