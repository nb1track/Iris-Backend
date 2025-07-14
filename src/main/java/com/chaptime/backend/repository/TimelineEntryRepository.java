package com.chaptime.backend.repository;

import com.chaptime.backend.model.Photo;
import com.chaptime.backend.model.TimelineEntry;
import com.chaptime.backend.model.TimelineEntryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.chaptime.backend.model.User;
import java.util.List;

@Repository
public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, TimelineEntryId> {
    List<TimelineEntry> findByUserOrderBySavedAtDesc(User user);
    void deleteByUserAndPhoto(User user, Photo photo);
}