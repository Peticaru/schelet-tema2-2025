package models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatureRequest extends Ticket {
    private BusinessValue businessValue;
    private CustomerDemand customerDemand;
}
