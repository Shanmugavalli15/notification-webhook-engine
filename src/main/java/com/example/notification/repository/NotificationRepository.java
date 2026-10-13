package com.example.notification.repository;

import com.example.notification.model.Notification;
import com.example.notification.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Legacy method — used by existing dashboard
    List<Notification> findAllByOrderByCreatedAtDesc();

    // Eagerly fetch integration to avoid LazyInitializationException during async delivery
    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.integration WHERE n.id = :id")
    Optional<Notification> findByIdWithIntegration(@Param("id") Long id);

    // Owner-scoped queries — use owner.id (not ownerId, which is a field)
    Optional<Notification> findByIdAndOwner_Id(Long id, Long ownerId);
    Page<Notification> findByOwner_IdOrderByCreatedAtDesc(Long ownerId, Pageable pageable);
    Page<Notification> findByOwner_IdAndStatusOrderByCreatedAtDesc(Long ownerId, NotificationStatus status, Pageable pageable);
    Page<Notification> findByOwner_IdAndIntegration_IdOrderByCreatedAtDesc(Long ownerId, Long integrationId, Pageable pageable);

    // Idempotency — composite unique index on (owner_id, idempotency_key)
    Optional<Notification> findByOwner_IdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    // Retry scheduler: events due for retry
    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.nextRetryAt <= :now ORDER BY n.nextRetryAt ASC")
    List<Notification> findDueForRetry(@Param("status") NotificationStatus status,
                                        @Param("now") LocalDateTime now);

    // Startup recovery: events left in PROCESSING or PENDING (unfinished before shutdown)
    @Query("SELECT n FROM Notification n WHERE n.status IN (com.example.notification.model.NotificationStatus.PENDING, com.example.notification.model.NotificationStatus.PROCESSING) AND n.createdAt < :cutoff")
    List<Notification> findStuckEvents(@Param("cutoff") LocalDateTime cutoff);

    // Dashboard stats
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.owner.id = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.owner.id = :ownerId AND n.status = :status")
    long countByOwnerIdAndStatus(@Param("ownerId") Long ownerId, @Param("status") NotificationStatus status);
}
