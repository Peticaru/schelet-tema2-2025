package models;

public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public Priority next() {
        Priority[] values = Priority.values();
        int nextIndex = Math.min(this.ordinal() + 1, values.length - 1);
        return values[nextIndex];
    }
}
