package models;

import lombok.Getter;

@Getter
public enum BusinessValue {
    S(1),
    M(2),
    L(3),
    XL(4);

    private final int value;

    BusinessValue(int value) {
        this.value = value;
    }
    public int toScore() {
        return this.value;
    }
}