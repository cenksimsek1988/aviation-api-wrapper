package io.github.cenksimsek1988.aviation.provider.awc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.cenksimsek1988.aviation.airport.Airport;
import io.github.cenksimsek1988.aviation.airport.IcaoCode;
import io.github.cenksimsek1988.aviation.airport.Quantity;
import io.github.cenksimsek1988.aviation.provider.ProviderAnswer;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AwcAirportMapperTest {

    private static final IcaoCode LIPE = IcaoCode.parse("LIPE");
    private static final Duration MAX_AGE = Duration.ofHours(1);

    @Test
    void emptyAnswerIsNotFound() {
        assertThat(AwcAirportMapper.toAnswer(LIPE, List.of(), MAX_AGE))
                .isEqualTo(new ProviderAnswer.NotFound<Airport>(MAX_AGE));
        assertThat(AwcAirportMapper.toAnswer(LIPE, null, MAX_AGE))
                .isEqualTo(new ProviderAnswer.NotFound<Airport>(MAX_AGE));
    }

    @Test
    void picksTheAirportAskedForAmongSeveral() {
        ProviderAnswer<Airport> answer = AwcAirportMapper.toAnswer(LIPE,
                List.of(wire("LIPB", "46.46", "11.32", "240"), wire("lipe ", "44.5308", "11.297", "38")), MAX_AGE);

        assertThat(answer).isInstanceOfSatisfying(ProviderAnswer.Found.class,
                found -> assertThat(((Airport) found.value()).location().latitude()).isEqualTo(44.5308));
    }

    @Test
    void answerWithoutTheAirportAskedForIsBad() {
        assertThat(AwcAirportMapper.toAnswer(LIPE, List.of(wire("LIPB", "46.46", "11.32", "240")), MAX_AGE))
                .isInstanceOf(ProviderAnswer.Bad.class);
    }

    @Test
    void badRequiredValueMakesTheAnswerBad() {
        assertThat(answer(wire("LIPE", null, "11.297", "38"))).isInstanceOf(ProviderAnswer.Bad.class);
        assertThat(answer(wire("LIPE", "north", "11.297", "38"))).isInstanceOf(ProviderAnswer.Bad.class);
        assertThat(answer(wire("LIPE", "444.5", "11.297", "38"))).isInstanceOf(ProviderAnswer.Bad.class);
    }

    @Test
    void readsNumbersSentAsJsonNumbersOrAsText() {
        AwcAirport wire = JsonMapper.builder().build().readValue(
                "{\"icaoId\": \"LIPE\", \"lat\": 44.5308, \"lon\": \"11.297\", \"elev\": 38.10}", AwcAirport.class);

        Airport airport = found(wire);

        assertThat(airport.location().latitude()).isEqualTo(44.5308);
        assertThat(airport.location().longitude()).isEqualTo(11.297);
        assertThat(airport.location().elevation()).isEqualTo(new Quantity(new BigDecimal("38.10"), "m"));
    }

    @Test
    void badOptionalValuesBecomeNullAndTheAirportStays() {
        Airport airport = found(wire("LIPE", "44.5308", "11.297", "high",
                runway("12/30", "unknown", ""), null, runway("04/22", "99999999999x148", "H")));

        assertThat(airport.location().elevation()).isNull();
        assertThat(airport.runways()).as("the empty runway entry is skipped").containsExactly(
                new Airport.Runway("12/30", null, null, null),
                new Airport.Runway("04/22", null, null, "H"));
    }

    @Test
    void cleansPaddingAndPlaceholders() {
        AwcAirport padded = new AwcAirport("KJFK", "-", "NEW YORK/JOHN F KENNEDY INTL ", " ", "",
                "40.6399", "-73.7787", "4", null);

        Airport airport = found(IcaoCode.parse("KJFK"), padded);

        assertThat(airport.name()).isEqualTo("NEW YORK/JOHN F KENNEDY INTL");
        assertThat(airport.iata()).isNull();
        assertThat(airport.region()).isNull();
        assertThat(airport.country()).isNull();
        assertThat(airport.runways()).isEmpty();
    }

    private static Airport found(AwcAirport wire) {
        return found(LIPE, wire);
    }

    private static Airport found(IcaoCode asked, AwcAirport wire) {
        ProviderAnswer<Airport> answer = AwcAirportMapper.toAnswer(asked, List.of(wire), MAX_AGE);
        assertThat(answer).isInstanceOf(ProviderAnswer.Found.class);
        return ((ProviderAnswer.Found<Airport>) answer).value();
    }

    private static ProviderAnswer<Airport> answer(AwcAirport wire) {
        return AwcAirportMapper.toAnswer(LIPE, List.of(wire), MAX_AGE);
    }

    private static AwcAirport wire(String icao, String lat, String lon, String elev, AwcAirport.Runway... runways) {
        return new AwcAirport(icao, "BLQ", "BOLOGNA", "BO", "IT", lat, lon, elev, Arrays.asList(runways));
    }

    private static AwcAirport.Runway runway(String id, String dimension, String surface) {
        return new AwcAirport.Runway(id, dimension, surface);
    }
}
