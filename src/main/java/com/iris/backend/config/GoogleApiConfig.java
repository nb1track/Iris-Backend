package com.iris.backend.config;

import com.google.maps.GeoApiContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleApiConfig {

    /**
     * Represents the API key used to authenticate requests to the Google Maps API.
     * The value of this API key is injected from the application properties
     * using the key `gcp.maps.api-key`.
     *
     * This key is utilized in configuring the {@link GeoApiContext} to enable
     * communication with Google Maps services.
     */
    @Value("${gcp.maps.api-key}")
    private String apiKey;

    /**
     * Configures and provides a {@link GeoApiContext} bean for interacting with Google Maps Services.
     * The context is built using the API key injected into the application configuration.
     *
     * @return An instance of {@link GeoApiContext} configured for use with Google Maps Services.
     */
    @Bean
    public GeoApiContext geoApiContext() {
        return new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
    }
}