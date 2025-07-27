package com.iris.backend.repository;

import com.iris.backend.model.Photo;
import com.iris.backend.model.TimelineEntry;
import com.iris.backend.model.TimelineEntryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.iris.backend.model.User;
import java.util.List;

@Repository
public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, TimelineEntryId> {
    List<TimelineEntry> findByUserOrderBySavedAtDesc(User user);
    void deleteByUserAndPhoto(User user, Photo photo);
}