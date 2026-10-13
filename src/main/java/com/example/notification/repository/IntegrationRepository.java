package com.example.notification.repository;

import com.example.notification.model.Integration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntegrationRepository extends JpaRepository<Integration, Long> {
    List<Integration> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);
    Optional<Integration> findByIdAndOwner_Id(Long id, Long ownerId);
    List<Integration> findByEnabledTrue();
}
