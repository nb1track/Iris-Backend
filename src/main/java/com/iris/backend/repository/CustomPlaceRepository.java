package com.iris.backend.repository;

import com.iris.backend.model.CustomPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CustomPlaceRepository extends JpaRepository<CustomPlace, UUID> {
    // Hier können wir später spezielle Abfragen hinzufügen,
    // z.B. um alle abgelaufenen Places zu finden.
}