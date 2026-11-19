
package waypoint.model;

import java.time.Instant;
import java.util.Objects;

public final class Ticket {

    private final TicketId id;
    private final SpotId spotId;
    private final Instant entry;

    private Instant exit;
    private Money fare;

    private Ticket(TicketId id, SpotId spotId, Instant entry) {
        this.id = Objects.requireNonNull(id, "Ticket ID cannot be null");
        this.spotId = Objects.requireNonNull(spotId, "Spot ID cannot be null");
        this.entry = Objects.requireNonNull(entry, "Entry time cannot be null");
    }

    public static Ticket open(TicketId id, SpotId spotId, Instant entry) {
        return new Ticket(id, spotId, entry);
    }

    public boolean isOpen() {
        return exit == null;
    }

    public Money fare() {
        return fare;
    }

    public Instant exit() {
        return exit;
    }

    public Money close(Instant at, FarePolicy policy) {
        if (!isOpen()) {
            throw new TicketAlreadyClosed();
        }

        Objects.requireNonNull(at, "Exit time cannot be null");
        Objects.requireNonNull(policy, "Fare policy cannot be null");

        if (at.isBefore(entry)) {
            throw new IllegalArgumentException("Exit cannot be before entry");
        }

        Money calculatedFare = Objects.requireNonNull(
            policy.fareFor(entry, at, spotId),
            "Fare cannot be null"
        );

        if (calculatedFare.isNegative()) {
            throw new IllegalArgumentException("Fare cannot be negative");
        }

        exit = at;
        fare = calculatedFare;

        return fare;
    }
}
