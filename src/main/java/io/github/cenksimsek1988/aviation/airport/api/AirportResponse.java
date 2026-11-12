package io.github.cenksimsek1988.aviation.airport.api;

import io.github.cenksimsek1988.aviation.airport.Airport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The airport as the API returns it. It is separate from the domain {@link Airport}, so the domain and the providers
 * can change without changing the public JSON.
 */
@Schema(name = "Airport", description = "An airport. Values come from the data provider as it sends them; the "
        + "ICAO code, latitude and longitude are always present and valid, so clients need not check them again.")
record AirportResponse(
        @Schema(description = "Four-letter ICAO code, upper case; always valid", example = "LIPE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String icao,
        @Schema(description = "Three-letter IATA code, or null", example = "BLQ")
        @Nullable String iata,
        @Schema(description = "Name as the provider spells it, or null", example = "BOLOGNA/BORGO PANIGALE AIRPORT")
        @Nullable String name,
        @Schema(description = "State or province code, or null", example = "BO")
        @Nullable String region,
        @Schema(description = "Country code (ISO 3166-1 alpha-2), or null", example = "IT")
        @Nullable String country,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Location location,
        @Schema(description = "Runways; empty, never null, when the provider lists none",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Runway> runways) {

    static AirportResponse of(Airport airport) {
        Airport.Location location = airport.location();
        return new AirportResponse(
                airport.icao().value(),
                airport.iata(),
                airport.name(),
                airport.region(),
                airport.country(),
                new Location(location.latitude(), location.longitude(), Quantity.of(location.elevation())),
                airport.runways().stream()
                        .map(runway -> new Runway(runway.designator(), Quantity.of(runway.length()),
                                Quantity.of(runway.width()), runway.surface()))
                        .toList());
    }

    @Schema(name = "Location", description = "Position in WGS84 degrees, always within the valid ranges")
    record Location(
            @Schema(description = "Degrees north, -90..90", example = "44.5308") double latitude,
            @Schema(description = "Degrees east, -180..180", example = "11.297") double longitude,
            @Schema(description = "Field elevation above mean sea level, or null") @Nullable Quantity elevation) {
    }

    @Schema(name = "Runway")
    record Runway(
            @Schema(description = "Both ends, or null", example = "12/30") @Nullable String designator,
            @Schema(description = "Length, or null") @Nullable Quantity length,
            @Schema(description = "Width, or null") @Nullable Quantity width,
            @Schema(description = "The provider's surface code, for example H for hard, or null", example = "H")
            @Nullable String surface) {
    }

    @Schema(name = "Quantity", description = "A number with its unit, as the provider sends it; nothing is converted")
    record Quantity(
            @Schema(example = "38") BigDecimal value,
            @Schema(description = "Unit symbol, for example m or ft", example = "m") String unit) {

        static @Nullable Quantity of(io.github.cenksimsek1988.aviation.airport.@Nullable Quantity quantity) {
            return quantity == null ? null : new Quantity(quantity.value(), quantity.unit());
        }
    }
}
