package com.example.ticketing.notification;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findTop20ByMemberIdOrderByIdDesc(Long memberId);

	boolean existsByEventId(Long eventId);

}
