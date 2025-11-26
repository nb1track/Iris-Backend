package com.iris.backend.repository;

import com.iris.backend.model.BlockedNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BlockedNumberRepository extends JpaRepository<BlockedNumber, UUID> {
    boolean existsByPhoneNumber(String phoneNumber);
}