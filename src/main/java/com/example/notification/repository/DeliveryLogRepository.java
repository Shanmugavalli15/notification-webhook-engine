package com.example.notification.repository;

import com.example.notification.model.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {
    List<DeliveryLog> findByNotificationIdOrderByTimestampDesc(Long notificationId);

    @Query("SELECT AVG(d.durationMs) FROM DeliveryLog d WHERE d.notification.owner.id = :ownerId AND d.success = true AND d.durationMs IS NOT NULL")
    Double avgDeliveryDurationForOwner(@Param("ownerId") Long ownerId);
}
