import java.math.BigDecimal;
import java.math.RoundingMode;
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

    Money minus(Percentage discount) {
        long result = BigDecimal.valueOf(minor)
            .multiply(BigDecimal.valueOf(
                10000 - discount.getBasisPoints()
            ))
            .divide(
                BigDecimal.valueOf(10000),
                0,
                RoundingMode.HALF_EVEN
            )
            .longValueExact();

        return new Money(result, currency);
    }

    Money percentageOf(int basisPoints) {
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

    private void requireSame(Money other) {
        if (currency != other.currency)
            throw new IllegalArgumentException("Currency mismatch!");
    }

    Currency getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        return currency + " " +
            BigDecimal.valueOf(minor)
                .movePointLeft(currency.minorUnits)
                .toPlainString();
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
}

// 3. PERCENTAGE
final class Percentage {
    private final int basisPoints;

    private Percentage(int basisPoints) {
        this.basisPoints = basisPoints;
    }

    static Percentage ofBasisPoints(int bp) {
        if (bp < 0 || bp > 10000)
            throw new IllegalArgumentException(
                "Discount must be between 0 and 100%"
            );

        return new Percentage(bp);
    }

    Money applyTo(Money money) {
        return money.percentageOf(basisPoints);
    }

    int getBasisPoints() {
        return basisPoints;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Percentage)) return false;

        Percentage other = (Percentage) obj;

        return basisPoints == other.basisPoints;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(basisPoints);
    }

    @Override
    public String toString() {
        return (basisPoints / 100.0) + "%";
    }
}

// 4. QUANTITY
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

// 5. PRODUCT
class Product {
    private final String sku;
    private final String name;
    private final Money price;

    Product(String sku, String name, Money price) {
        this.sku = sku;
        this.name = name;
        this.price = price;
    }

    String getSku() {
        return sku;
    }

    String getName() {
        return name;
    }

    Money getPrice() {
        return price;
    }
}

// 6. CART LINE
class CartLine {
    private final Product product;
    private final Quantity quantity;

    CartLine(Product product, Quantity quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    Money total() {
        return product.getPrice().times(quantity);
    }

    Product getProduct() {
        return product;
    }

    Quantity getQuantity() {
        return quantity;
    }
}

// 7. CART
class Cart {
    private final Map<String, CartLine> items =
        new LinkedHashMap<>();

    private final Currency currency;

    Cart(Currency currency) {
        this.currency = currency;
    }

    void add(Product product, Quantity quantity) {
        if (product.getPrice().getCurrency() != currency)
            throw new IllegalArgumentException(
                "Cart currency mismatch"
            );

        String sku = product.getSku();

        if (items.containsKey(sku)) {
            CartLine old = items.get(sku);

            Quantity newQuantity =
                old.getQuantity().plus(quantity);

            items.put(
                sku,
                new CartLine(product, newQuantity)
            );
        } else {
            items.put(
                sku,
                new CartLine(product, quantity)
            );
        }
    }

    Money total() {
        Money result = Money.zero(currency);

        for (CartLine item : items.values()) {
            result = result.plus(item.total());
        }

        return result;
    }

    void display() {
        for (CartLine item : items.values()) {
            System.out.println(
                item.getProduct().getName() +
                " x " + item.getQuantity().getValue() +
                " = " + item.total()
            );
        }
    }
}

// 8. MAIN PROGRAM
public class Main {
    public static void main(String[] args) {

        // Create products
        Product p1 = new Product(
            "P101",
            "Headphones",
            Money.of("1000.00", Currency.INR)
        );

        Product p2 = new Product(
            "P102",
            "Keyboard",
            Money.of("1500.00", Currency.INR)
        );

        // Create shopping cart
        Cart cart = new Cart(Currency.INR);

        // Add products
        cart.add(p1, Quantity.of(2));
        cart.add(p2, Quantity.of(1));

        System.out.println("===== SHOPPING CART =====");

        cart.display();

        // Calculate total
        Money total = cart.total();

        System.out.println("\nCart Total: " + total);

        // Apply 10% discount
        Percentage discount =
            Percentage.ofBasisPoints(1000);

        Money discountAmount =
            discount.applyTo(total);

        Money finalAmount =
            total.minus(discount);

        System.out.println("Discount: " + discountAmount);
        System.out.println("Final Amount: " + finalAmount);

        // Test invalid discount
        try {
            Percentage.ofBasisPoints(25000);
        } catch (IllegalArgumentException e) {
            System.out.println(
                "\nInvalid Discount: " + e.getMessage()
            );
        }

        // Test invalid quantity
        try {
            Quantity.of(0);
        } catch (IllegalArgumentException e) {
            System.out.println(
                "Invalid Quantity: " + e.getMessage()
            );
        }

        // Test different currencies
        try {
            Money eur = Money.of("100.00", Currency.EUR);
            Money usd = Money.of("100.00", Currency.USD);

            eur.plus(usd);

        } catch (IllegalArgumentException e) {
            System.out.println(
                "Currency Error: " + e.getMessage()
            );
        }

        // Test negative money
        try {
            Money.of("-50.00", Currency.INR);
        } catch (IllegalArgumentException e) {
            System.out.println(
                "Money Error: " + e.getMessage()
            );
        }
    }
}