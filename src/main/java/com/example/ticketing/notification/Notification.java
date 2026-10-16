package com.example.ticketing.notification;

import com.example.ticketing.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** 회원에게 보낸 알림 기록. (실제 서비스라면 여기서 알림톡/메일/푸시 API를 호출한다) */
@Entity
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(nullable = false)
	private String message;

	/** 이 알림을 만든 이벤트(outbox_event.id). unique라서 같은 이벤트로 알림이 두 번 저장되지 않는다. */
	@Column(unique = true)
	private Long eventId;

	protected Notification() {
	}

	public Notification(Long eventId, Long memberId, NotificationType type, String message) {
		this.eventId = eventId;
		this.memberId = memberId;
		this.type = type;
		this.message = message;
	}

	public Long getId() {
		return id;
	}

	public Long getEventId() {
		return eventId;
	}

	public Long getMemberId() {
		return memberId;
	}

	public NotificationType getType() {
		return type;
	}

	public String getMessage() {
		return message;
	}

}
