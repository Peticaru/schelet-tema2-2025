package models;

import lombok.Getter;

@Getter
public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int value;

    Priority(int value) {
        this.value = value;
    }

    public Priority next() {
        Priority[] values = Priority.values();
        int nextIndex = Math.min(this.ordinal() + 1, values.length - 1);
        return values[nextIndex];
    }
}