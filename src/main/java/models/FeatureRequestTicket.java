package models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatureRequestTicket extends Ticket {
    private BusinessValue businessValue;
    private CustomerDemand customerDemand;
}