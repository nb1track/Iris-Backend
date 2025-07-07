package com.chaptime.backend.repository;

import com.chaptime.backend.model.TimelineEntry;
import com.chaptime.backend.model.TimelineEntryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, TimelineEntryId> {
}