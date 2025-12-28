package models;

import lombok.Getter;

@Getter
public enum Frequency {
    RARE(1),
    OCCASIONAL(2),
    FREQUENT(3),
    ALWAYS(4);

    private final int value;

    Frequency(int value) {
        this.value = value;
    }
    public int toScore() {
        return this.value;
    }
}