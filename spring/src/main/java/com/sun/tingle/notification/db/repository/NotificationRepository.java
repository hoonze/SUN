package com.sun.tingle.notification.db.repository;

import com.sun.tingle.notification.db.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    public Optional<List<NotificationEntity>> findALLByReceiverId(Long receiverId);

}