package io.github.cenksimsek1988.aviation;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(info = @Info(title = "Aviation API Wrapper", version = "v1",
        description = "Airport details by ICAO code, from the aviationweather.gov data API"))
public class AviationApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AviationApiApplication.class, args);
    }
}
