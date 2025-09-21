package com.iris.backend.repository;

import com.iris.backend.model.CustomPlace;
import com.iris.backend.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.iris.backend.model.User;
import java.util.List;
import java.util.List;
import java.util.UUID;

@Repository
public interface CustomPlaceRepository extends JpaRepository<CustomPlace, UUID> {

    @Query(value = """
    SELECT DISTINCT ph.*
    FROM
        photos ph
    JOIN
        users u ON ph.uploader_id = u.id,
        jsonb_to_recordset(?2::jsonb) AS h(latitude float, longitude float, "timestamp" timestptz)
    WHERE
        ph.google_place_id = ?1
        AND ph.visibility = 'PUBLIC'
        AND ST_DWithin(
            (SELECT location FROM google_places WHERE id = ?1),
            ST_MakePoint(h.longitude, h.latitude)::geography,
            500
        )
        AND ph.uploaded_at BETWEEN (h.timestamp - interval '5 hours') AND h.timestamp
    ORDER BY ph.uploaded_at DESC
    """, nativeQuery = true)
    List<Photo> findPhotosForPlaceMatchingHistoricalBatch(
            Long placeId,       // Wird zu ?1
            String historyJson  // Wird zu ?2
    );


    List<CustomPlace> findAllByCreatorOrderByCreatedAtDesc(User creator);

    List<CustomPlace> findAllByIsTrendingTrueOrderByCreatedAtDesc();
}