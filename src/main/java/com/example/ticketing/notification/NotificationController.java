package com.example.ticketing.notification;

import java.time.LocalDateTime;
import java.util.List;

import com.example.ticketing.auth.CurrentMemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class NotificationController {

	private final NotificationRepository notificationRepository;

	public NotificationController(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	public record NotificationResponse(Long id, NotificationType type, String message, LocalDateTime createdAt) {
	}

	@Operation(summary = "내 알림 (최근 20개)")
	@GetMapping("/api/notifications/me")
	@Transactional(readOnly = true)
	public List<NotificationResponse> myNotifications(@CurrentMemberId Long memberId) {
		return notificationRepository.findTop20ByMemberIdOrderByIdDesc(memberId)
			.stream()
			.map(n -> new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getCreatedAt()))
			.toList();
	}

}
