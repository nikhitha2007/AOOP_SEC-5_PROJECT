
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

// 1. ROOM IDENTIFIER
final class RoomId {
    private final String value;

    RoomId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Invalid Room ID");
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RoomId)) return false;
        return value.equals(((RoomId) obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

// 2. RESERVATION IDENTIFIER
final class ReservationId {
    private final String value;

    ReservationId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Invalid Reservation ID");
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

// 3. GUEST
final class Guest {
    private final String guestId;
    private final String name;

    Guest(String guestId, String name) {
        if (guestId == null || guestId.trim().isEmpty())
            throw new IllegalArgumentException("Invalid Guest ID");

        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Guest name required");

        this.guestId = guestId;
        this.name = name;
    }

    String getName() {
        return name;
    }
}

// 4. MONEY
final class Money {
    private final long minor;
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0)
            throw new IllegalArgumentException("Negative amount");

        this.minor = minor;
        this.currency = currency;
    }

    static Money of(String amount, Currency currency) {
        BigDecimal value = new BigDecimal(amount);

        if (value.signum() < 0)
            throw new IllegalArgumentException("Negative amount");

        value = value.setScale(
            currency.minorUnits,
            RoundingMode.UNNECESSARY
        );

        return new Money(
            value.movePointRight(currency.minorUnits).longValueExact(),
            currency
        );
    }

    Money times(int quantity) {
        if (quantity < 0)
            throw new IllegalArgumentException("Invalid quantity");

        return new Money(
            Math.multiplyExact(minor, quantity),
            currency
        );
    }

    Money percentage(int basisPoints) {
        if (basisPoints < 0 || basisPoints > 10000)
            throw new IllegalArgumentException("Invalid percentage");

        long result = BigDecimal.valueOf(minor)
            .multiply(BigDecimal.valueOf(basisPoints))
            .divide(
                BigDecimal.valueOf(10000),
                0,
                RoundingMode.HALF_EVEN
            )
            .longValueExact();

        return new Money(result, currency);
    }

    @Override
    public String toString() {
        return currency + " " +
            BigDecimal.valueOf(minor, currency.minorUnits);
    }
}

enum Currency {
    INR(2), USD(2), EUR(2);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

// 5. DATE RANGE
final class DateRange {
    private final LocalDate checkIn;
    private final LocalDate checkOut;

    DateRange(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null)
            throw new IllegalArgumentException("Dates required");

        if (!checkIn.isBefore(checkOut))
            throw new IllegalArgumentException(
                "Check-in must be before check-out"
            );

        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    boolean overlaps(DateRange other) {
        return checkIn.isBefore(other.checkOut) &&
               other.checkIn.isBefore(checkOut);
    }

    long nights() {
        return java.time.temporal.ChronoUnit.DAYS.between(
            checkIn, checkOut
        );
    }

    LocalDate getCheckIn() {
        return checkIn;
    }

    LocalDate getCheckOut() {
        return checkOut;
    }

    @Override
    public String toString() {
        return checkIn + " to " + checkOut;
    }
}

// 6. RESERVATION STATUS
enum ReservationStatus {
    REQUESTED,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED
}

// 7. CANCELLATION POLICY
final class CancellationPolicy {
    private final int refundBasisPoints;

    CancellationPolicy(int refundBasisPoints) {
        if (refundBasisPoints < 0 || refundBasisPoints > 10000)
            throw new IllegalArgumentException("Invalid refund percentage");

        this.refundBasisPoints = refundBasisPoints;
    }

    Money calculateRefund(Money amount) {
        return amount.percentage(refundBasisPoints);
    }
}

// 8. RESERVATION
final class Reservation {
    private final ReservationId id;
    private final Guest guest;
    private final RoomId room;
    private final DateRange stay;
    private final Money price;

    private ReservationStatus status =
        ReservationStatus.REQUESTED;

    private boolean paymentGuaranteed;
    private Money refund;

    Reservation(
        ReservationId id,
        Guest guest,
        RoomId room,
        DateRange stay,
        Money price
    ) {
        this.id = Objects.requireNonNull(id);
        this.guest = Objects.requireNonNull(guest);
        this.room = Objects.requireNonNull(room);
        this.stay = Objects.requireNonNull(stay);
        this.price = Objects.requireNonNull(price);
    }

    void confirm(boolean paymentGuaranteed) {
        requireStatus(ReservationStatus.REQUESTED);

        if (!paymentGuaranteed)
            throw new IllegalStateException(
                "Payment guarantee required"
            );

        this.paymentGuaranteed = true;
        status = ReservationStatus.CONFIRMED;
    }

    void checkIn() {
        requireStatus(ReservationStatus.CONFIRMED);

        status = ReservationStatus.CHECKED_IN;
    }

    void checkOut() {
        requireStatus(ReservationStatus.CHECKED_IN);

        status = ReservationStatus.COMPLETED;
    }

    void cancel(CancellationPolicy policy) {
        if (status != ReservationStatus.REQUESTED &&
            status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                "Reservation cannot be cancelled in " + status
            );
        }

        refund = policy.calculateRefund(price);
        status = ReservationStatus.CANCELLED;
    }

    private void requireStatus(ReservationStatus expected) {
        if (status != expected)
            throw new IllegalStateException(
                "Invalid operation. Current status: " + status
            );
    }

    ReservationStatus getStatus() {
        return status;
    }

    RoomId getRoom() {
        return room;
    }

    DateRange getStay() {
        return stay;
    }

    ReservationId getId() {
        return id;
    }

    Money getRefund() {
        return refund;
    }

    Guest getGuest() {
        return guest;
    }

    Money getPrice() {
        return price;
    }

    boolean isPaymentGuaranteed() {
        return paymentGuaranteed;
    }

    void display() {
        System.out.println("\nReservation ID: " + id);
        System.out.println("Guest: " + guest.getName());
        System.out.println("Room: " + room);
        System.out.println("Stay: " + stay);
        System.out.println("Nights: " + stay.nights());
        System.out.println("Price: " + price);
        System.out.println("Status: " + status);
    }
}

// 9. HOTEL - RESERVATION MANAGEMENT
class Hotel {
    private final Map<RoomId, List<Reservation>> reservations =
        new HashMap<>();

    void addRoom(RoomId room) {
        reservations.putIfAbsent(room, new ArrayList<>());
    }

    void makeReservation(Reservation reservation) {
        RoomId room = reservation.getRoom();

        if (!reservations.containsKey(room))
            throw new IllegalArgumentException("Room does not exist");

        for (Reservation existing : reservations.get(room)) {
            if (existing.getStatus() != ReservationStatus.CANCELLED &&
                existing.getStay().overlaps(reservation.getStay())) {

                throw new IllegalStateException(
                    "Room is already booked for these dates"
                );
            }
        }

        reservations.get(room).add(reservation);
    }

    void displayReservations() {
        for (List<Reservation> list : reservations.values()) {
            for (Reservation reservation : list) {
                reservation.display();
            }
        }
    }
}

// 10. MAIN PROGRAM
public class Main {

    public static void main(String[] args) {

        System.out.println("===== HOTEL RESERVATION SYSTEM =====");

        Hotel hotel = new Hotel();

        RoomId room1 = new RoomId("ROOM101");
        RoomId room2 = new RoomId("ROOM102");

        hotel.addRoom(room1);
        hotel.addRoom(room2);

        Guest guest1 = new Guest("G101", "Nikhitha");
        Guest guest2 = new Guest("G102", "Poojitha");

        DateRange stay1 = new DateRange(
            LocalDate.of(2026, 12, 10),
            LocalDate.of(2026, 12, 13)
        );

        Reservation reservation1 = new Reservation(
            new ReservationId("RES101"),
            guest1,
            room1,
            stay1,
            Money.of("6000.00", Currency.INR)
        );

        // Make reservation
        hotel.makeReservation(reservation1);

        System.out.println("\nReservation created successfully");

        // Confirm reservation
        reservation1.confirm(true);

        System.out.println("After confirmation: " +
            reservation1.getStatus());

        // Check in
        reservation1.checkIn();

        System.out.println("After check-in: " +
            reservation1.getStatus());

        // Check out
        reservation1.checkOut();

        System.out.println("After check-out: " +
            reservation1.getStatus());

        reservation1.display();

        // Test overlapping reservation
        try {
            Reservation reservation2 = new Reservation(
                new ReservationId("RES102"),
                guest2,
                room1,
                new DateRange(
                    LocalDate.of(2026, 12, 12),
                    LocalDate.of(2026, 12, 15)
                ),
                Money.of("4000.00", Currency.INR)
            );

            hotel.makeReservation(reservation2);

        } catch (IllegalStateException e) {
            System.out.println(
                "\nBooking Error: " + e.getMessage()
            );
        }

        // Test consecutive reservations
        try {
            Reservation reservation3 = new Reservation(
                new ReservationId("RES103"),
                guest2,
                room1,
                new DateRange(
                    LocalDate.of(2026, 12, 13),
                    LocalDate.of(2026, 12, 16)
                ),
                Money.of("6000.00", Currency.INR)
            );

            hotel.makeReservation(reservation3);

            System.out.println(
                "Consecutive reservation accepted successfully"
            );

        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }

        // Test missing payment guarantee
        try {
            Reservation reservation4 = new Reservation(
                new ReservationId("RES104"),
                guest2,
                room2,
                new DateRange(
                    LocalDate.of(2026, 12, 20),
                    LocalDate.of(2026, 12, 22)
                ),
                Money.of("3000.00", Currency.INR)
            );

            hotel.makeReservation(reservation4);
            reservation4.confirm(false);

        } catch (IllegalStateException e) {
            System.out.println(
                "\nPayment Error: " + e.getMessage()
            );
        }

        // Test invalid date range
        try {
            new DateRange(
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 12, 10)
            );

        } catch (IllegalArgumentException e) {
            System.out.println(
                "Date Error: " + e.getMessage()
            );
        }

        // Test cancellation and refund
        try {
            Reservation reservation5 = new Reservation(
                new ReservationId("RES105"),
                guest2,
                room2,
                new DateRange(
                    LocalDate.of(2026, 12, 25),
                    LocalDate.of(2026, 12, 28)
                ),
                Money.of("5000.00", Currency.INR)
            );

            hotel.makeReservation(reservation5);
            reservation5.confirm(true);

            CancellationPolicy policy =
                new CancellationPolicy(8000);

            reservation5.cancel(policy);

            System.out.println(
                "\nCancellation Status: " +
                reservation5.getStatus()
            );

            System.out.println(
                "Refund Amount: " +
                reservation5.getRefund()
            );

        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }

        // Test cancellation after check-in
        try {
            reservation1.cancel(
                new CancellationPolicy(10000)
            );

        } catch (IllegalStateException e) {
            System.out.println(
                "\nCancellation Error: " + e.getMessage()
            );
        }

        System.out.println("\n===== PROGRAM COMPLETED =====");
    }
}
