package models;

import lombok.Getter;

@Getter
public enum CustomerDemand {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    VERY_HIGH(4);

    private final int value;

    CustomerDemand(int value) {
        this.value = value;
    }
}