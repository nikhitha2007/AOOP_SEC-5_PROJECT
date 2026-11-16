
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

// 1. CURRENCY
enum Currency {
    EUR(2), USD(2), JPY(0), KWD(3), INR(2);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

// 2. MONEY
final class Money {
    private final long minor;
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0)
            throw new IllegalArgumentException("Negative money not allowed");

        this.minor = minor;
        this.currency = Objects.requireNonNull(currency);
    }

    static Money of(String amount, Currency currency) {
        BigDecimal value = new BigDecimal(amount);

        if (value.signum() < 0)
            throw new IllegalArgumentException("Negative price");

        value = value.setScale(
            currency.minorUnits,
            RoundingMode.UNNECESSARY
        );

        long minor = value
            .movePointRight(currency.minorUnits)
            .longValueExact();

        return new Money(minor, currency);
    }

    static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    Money plus(Money other) {
        requireSame(other);

        return new Money(
            Math.addExact(minor, other.minor),
            currency
        );
    }

    Money times(Quantity quantity) {
        return new Money(
            Math.multiplyExact(minor, quantity.getValue()),
            currency
        );
    }

    private void requireSame(Money other) {
        if (currency != other.currency)
            throw new IllegalArgumentException("Currency mismatch");
    }

    Currency getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Money)) return false;

        Money other = (Money) obj;

        return minor == other.minor &&
               currency == other.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minor, currency);
    }

    @Override
    public String toString() {
        return currency + " " +
            BigDecimal.valueOf(minor)
                .movePointLeft(currency.minorUnits)
                .toPlainString();
    }
}

// 3. QUANTITY
final class Quantity {
    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    static Quantity of(int value) {
        if (value < 1)
            throw new IllegalArgumentException(
                "Quantity must be positive"
            );

        return new Quantity(value);
    }

    int getValue() {
        return value;
    }

    Quantity plus(Quantity other) {
        return Quantity.of(
            Math.addExact(value, other.value)
        );
    }
}

// 4. ORDER ID
final class OrderId {
    private final String value;

    OrderId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Invalid Order ID");

        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OrderId)) return false;

        return value.equals(((OrderId) obj).value);
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

// 5. PRODUCT ID
final class ProductId {
    private final String value;

    ProductId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Invalid Product ID");

        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProductId)) return false;

        return value.equals(((ProductId) obj).value);
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

// 6. TRACKING NUMBER
final class TrackingNumber {
    private final String value;

    TrackingNumber(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(
                "Tracking number required"
            );

        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

// 7. PAYMENT REFERENCE
final class PaymentRef {
    private final String value;

    PaymentRef(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(
                "Payment reference required"
            );

        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}

// 8. ORDER STATUS
enum Status {
    DRAFT,
    PLACED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}

// 9. DOMAIN EXCEPTION
class DomainException extends RuntimeException {
    DomainException(String message) {
        super(message);
    }
}

// 10. ORDER LINE
final class OrderLine {
    private final ProductId productId;
    private final Money unitPrice;
    private final Quantity quantity;

    OrderLine(
        ProductId productId,
        Money unitPrice,
        Quantity quantity
    ) {
        this.productId = Objects.requireNonNull(productId);
        this.unitPrice = Objects.requireNonNull(unitPrice);
        this.quantity = Objects.requireNonNull(quantity);
    }

    OrderLine combine(OrderLine other) {
        if (!unitPrice.equals(other.unitPrice))
            throw new DomainException(
                "Product price mismatch"
            );

        return new OrderLine(
            productId,
            unitPrice,
            quantity.plus(other.quantity)
        );
    }

    Money total() {
        return unitPrice.times(quantity);
    }

    ProductId getProductId() {
        return productId;
    }

    Quantity getQuantity() {
        return quantity;
    }
}

// 11. SHIPMENT
final class Shipment {
    private final TrackingNumber trackingNumber;
    private final Instant shippedAt;

    Shipment(TrackingNumber trackingNumber, Instant shippedAt) {
        this.trackingNumber =
            Objects.requireNonNull(trackingNumber);

        this.shippedAt =
            Objects.requireNonNull(shippedAt);
    }

    TrackingNumber getTrackingNumber() {
        return trackingNumber;
    }

    Instant getShippedAt() {
        return shippedAt;
    }
}

// 12. ORDER - AGGREGATE ROOT
final class Order {
    private final OrderId id;

    private Status status = Status.DRAFT;

    private final Map<ProductId, OrderLine> lines =
        new LinkedHashMap<>();

    private Shipment shipment;

    private PaymentRef paymentRef;

    private String cancellationReason;

    private final Currency currency;

    Order(OrderId id, Currency currency) {
        this.id = Objects.requireNonNull(id);
        this.currency = Objects.requireNonNull(currency);
    }

    private void requireStatus(Status expected) {
        if (status != expected)
            throw new DomainException(
                "Invalid operation. Current status: " + status
            );
    }

    // Add product only in DRAFT state
    void addLine(
        ProductId productId,
        Money unitPrice,
        Quantity quantity
    ) {
        requireStatus(Status.DRAFT);

        if (unitPrice.getCurrency() != currency)
            throw new DomainException("Currency mismatch");

        OrderLine newLine =
            new OrderLine(productId, unitPrice, quantity);

        if (lines.containsKey(productId)) {
            lines.put(
                productId,
                lines.get(productId).combine(newLine)
            );
        } else {
            lines.put(productId, newLine);
        }
    }

    // DRAFT -> PLACED
    void place() {
        requireStatus(Status.DRAFT);

        if (lines.isEmpty())
            throw new DomainException(
                "Cannot place an empty order"
            );

        status = Status.PLACED;
    }

    // PLACED -> PAID
    void pay(PaymentRef payment) {
        requireStatus(Status.PLACED);

        paymentRef = Objects.requireNonNull(payment);

        status = Status.PAID;
    }

    // PAID -> SHIPPED
    void ship(TrackingNumber tracking, Instant at) {
        requireStatus(Status.PAID);

        shipment = new Shipment(tracking, at);

        status = Status.SHIPPED;
    }

    // SHIPPED -> DELIVERED
    void deliver() {
        requireStatus(Status.SHIPPED);

        status = Status.DELIVERED;
    }

    // DRAFT / PLACED / PAID -> CANCELLED
    void cancel(String reason) {
        if (status != Status.DRAFT &&
            status != Status.PLACED &&
            status != Status.PAID) {

            throw new DomainException(
                "Order cannot be cancelled in " + status
            );
        }

        if (reason == null || reason.trim().isEmpty())
            throw new DomainException(
                "Cancellation reason required"
            );

        cancellationReason = reason;

        status = Status.CANCELLED;
    }

    // Calculate total from order lines
    Money total() {
        Money result = Money.zero(currency);

        for (OrderLine line : lines.values()) {
            result = result.plus(line.total());
        }

        return result;
    }

    List<OrderLine> getLines() {
        return Collections.unmodifiableList(
            new ArrayList<>(lines.values())
        );
    }

    Status getStatus() {
        return status;
    }

    OrderId getId() {
        return id;
    }

    Optional<Shipment> getShipment() {
        return Optional.ofNullable(shipment);
    }

    Optional<String> getCancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    Optional<PaymentRef> getPaymentRef() {
        return Optional.ofNullable(paymentRef);
    }

    void display() {
        System.out.println("\nOrder ID: " + id);
        System.out.println("Status: " + status);

        System.out.println("Order Lines:");

        for (OrderLine line : lines.values()) {
            System.out.println(
                line.getProductId() +
                " x " + line.getQuantity().getValue() +
                " = " + line.total()
            );
        }

        System.out.println("Order Total: " + total());

        if (shipment != null) {
            System.out.println(
                "Tracking Number: " +
                shipment.getTrackingNumber()
            );

            System.out.println(
                "Shipped At: " + shipment.getShippedAt()
            );
        }
    }
}

// 13. MAIN PROGRAM
public class Main {

    public static void main(String[] args) {

        System.out.println("===== ORDER MANAGEMENT SYSTEM =====");

        // Create order
        Order order = new Order(
            new OrderId("ORD101"),
            Currency.INR
        );

        // Create products
        ProductId p1 = new ProductId("P101");
        ProductId p2 = new ProductId("P102");

        // Add order lines
        order.addLine(
            p1,
            Money.of("1000.00", Currency.INR),
            Quantity.of(2)
        );

        order.addLine(
            p2,
            Money.of("500.00", Currency.INR),
            Quantity.of(1)
        );

        // Add same product again
        order.addLine(
            p1,
            Money.of("1000.00", Currency.INR),
            Quantity.of(1)
        );

        order.display();

        // Place order
        order.place();

        System.out.println("\nAfter placing: " + order.getStatus());

        // Pay order
        order.pay(new PaymentRef("PAY101"));

        System.out.println("After payment: " + order.getStatus());

        // Ship order
        order.ship(
            new TrackingNumber("TRK12345"),
            Instant.now()
        );

        System.out.println("After shipping: " + order.getStatus());

        // Display shipment information
        order.display();

        // Deliver order
        order.deliver();

        System.out.println("\nAfter delivery: " + order.getStatus());

        // Test adding line after placement
        try {
            order.addLine(
                new ProductId("P103"),
                Money.of("200.00", Currency.INR),
                Quantity.of(1)
            );
        } catch (DomainException e) {
            System.out.println(
                "\nInvalid Operation: " + e.getMessage()
            );
        }

        // Test cancelling delivered order
        try {
            order.cancel("Changed my mind");
        } catch (DomainException e) {
            System.out.println(
                "Cancellation Error: " + e.getMessage()
            );
        }

        // Test empty order
        try {
            Order emptyOrder = new Order(
                new OrderId("ORD102"),
                Currency.INR
            );

            emptyOrder.place();

        } catch (DomainException e) {
            System.out.println(
                "Empty Order Error: " + e.getMessage()
            );
        }

        // Test cancelled order cannot be shipped
        try {
            Order cancelledOrder = new Order(
                new OrderId("ORD103"),
                Currency.INR
            );

            cancelledOrder.addLine(
                new ProductId("P104"),
                Money.of("800.00", Currency.INR),
                Quantity.of(1)
            );

            cancelledOrder.place();
            cancelledOrder.cancel("Customer request");

            cancelledOrder.ship(
                new TrackingNumber("TRK999"),
                Instant.now()
            );

        } catch (DomainException e) {
            System.out.println(
                "Shipping Error: " + e.getMessage()
            );
        }

        System.out.println("\n===== PROGRAM COMPLETED =====");
    }
}
