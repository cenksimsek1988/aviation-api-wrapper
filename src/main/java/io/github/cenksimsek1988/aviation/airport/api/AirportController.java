package io.github.cenksimsek1988.aviation.airport.api;

import io.github.cenksimsek1988.aviation.airport.AirportService;
import io.github.cenksimsek1988.aviation.airport.IcaoCode;
import io.github.cenksimsek1988.aviation.security.Authorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/airports")
@Tag(name = "Airports")
class AirportController {

    private final AirportService airportService;

    AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    /**
     * Returns the airport. {@code Cache-Control: max-age} is the provider's own fresh time, unchanged, and {@code Age}
     * is how long ago we fetched it (RFC 9111): a client computes what is left as {@code max-age - Age}. The error
     * answers are documented where they are made, in {@link AirportApiExceptionHandler}.
     */
    @GetMapping(path = "/{icao}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.AIRPORTS_READ + "')")
    @Operation(summary = "Airport details by ICAO code",
            description = "Needs the authority " + Authorities.AIRPORTS_READ + ". For now every caller has it: there "
                    + "is no login yet.")
    @ApiResponse(responseCode = "200", description = "The airport", headers = {
            @Header(name = HttpHeaders.CACHE_CONTROL, description = "The provider's max-age for this data"),
            @Header(name = HttpHeaders.AGE, description = "Seconds since the data was fetched from the provider")})
    public ResponseEntity<AirportResponse> findByIcao(
            @Parameter(description = "Four-letter ICAO code, any case", example = "LIPE") @PathVariable String icao) {
        AirportService.Lookup lookup = airportService.findByIcao(IcaoCode.parse(icao));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(lookup.freshFor()).cachePublic())
                .header(HttpHeaders.AGE, String.valueOf(lookup.age().toSeconds()))
                .body(AirportResponse.of(lookup.airport()));
    }
}
