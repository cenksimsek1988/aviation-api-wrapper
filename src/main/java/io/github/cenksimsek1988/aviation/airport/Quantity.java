package io.github.cenksimsek1988.aviation.airport;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A number together with its unit, for example 38 "m" or 9196 "ft": the Quantity pattern (Martin Fowler, Analysis
 * Patterns). The number never travels without its unit.
 *
 * <p>The unit is a plain string, written by the provider's mapper from the provider's documentation. A new unit
 * (kilometres, kelvin, hectopascal) therefore needs no new type, and the API's JSON shape stays the same. Values go
 * out as the provider sends them; nothing is converted. Conversion belongs to the day a client can ask for a unit.
 *
 * @param value the number, exactly as the provider sent it
 * @param unit  the unit symbol, for example "m" or "ft"
 */
public record Quantity(BigDecimal value, String unit) {

    public Quantity {
        Objects.requireNonNull(value, "value");
        if (unit.isBlank()) {
            throw new IllegalArgumentException("A quantity needs a unit");
        }
    }
}
