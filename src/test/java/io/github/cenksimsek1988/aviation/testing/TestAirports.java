package io.github.cenksimsek1988.aviation.testing;

import io.github.cenksimsek1988.aviation.airport.Airport;
import io.github.cenksimsek1988.aviation.airport.IcaoCode;
import io.github.cenksimsek1988.aviation.airport.Quantity;
import java.math.BigDecimal;
import java.util.List;

/** Ready-made airports and provider JSON for tests. */
public final class TestAirports {

    private TestAirports() {
    }

    /** A domain airport with this code and name. */
    public static Airport airport(String icao, String name) {
        return new Airport(IcaoCode.parse(icao), null, name, null, "IT",
                new Airport.Location(44.5308, 11.297, new Quantity(BigDecimal.valueOf(38), "m")),
                List.of(new Airport.Runway("12/30", new Quantity(BigDecimal.valueOf(9196), "ft"),
                        new Quantity(BigDecimal.valueOf(148), "ft"), "H")));
    }

    /** aviationweather.gov's JSON answer for one simple airport. */
    public static String awcJson(String icao) {
        return """
                [{"icaoId":"%s","iataId":null,"name":"TEST AIRPORT","state":null,"country":"IT",\
                "lat":41.8,"lon":12.25,"elev":5,"runways":[]}]""".formatted(icao);
    }
}
