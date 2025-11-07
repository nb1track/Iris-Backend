package com.iris.backend.config;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Stellt eine GeometryFactory als Spring Bean zur Verfügung,
     * die für PostGIS-Operationen (SRID 4326) optimiert ist.
     */
    @Bean
    public GeometryFactory geometryFactory() {
        // SRID 4326 ist der Standard für GPS-Koordinaten (WGS 84)
        return new GeometryFactory(new PrecisionModel(), 4326);
    }
}
