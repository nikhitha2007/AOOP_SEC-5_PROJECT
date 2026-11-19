package waypoint.model;

public class CurrencyMismatch extends IllegalArgumentException {
    public CurrencyMismatch() {
        super("Cannot perform operations on different currencies");
    }
}