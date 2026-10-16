package com.example.ticketing.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 실제로는 알림톡/메일/푸시 API를 부르는 자리. 여기서는 "보냈다"는 기록만 저장한다. */
@Service
public class NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

	private final NotificationRepository notificationRepository;

	public NotificationSender(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	/**
	 * 멱등 처리: Kafka는 같은 이벤트를 두 번 줄 수 있다(at-least-once). 이미 처리한 eventId면 무시한다.
	 * 동시에 두 번 들어오는 경우는 DB unique 제약이 막는다.
	 *
	 * @return 새로 보냈으면 true, 중복이라 무시했으면 false
	 */
	@Transactional
	public boolean send(Long eventId, Long memberId, NotificationType type, String message) {
		if (notificationRepository.existsByEventId(eventId)) {
			log.info("이미 처리한 이벤트 → 무시 eventId={}", eventId);
			return false;
		}
		try {
			notificationRepository.saveAndFlush(new Notification(eventId, memberId, type, message));
		}
		catch (DataIntegrityViolationException e) {
			log.info("동시에 처리된 중복 이벤트 → 무시 eventId={}", eventId);
			return false;
		}
		log.info("알림 발송 eventId={} memberId={} message={}", eventId, memberId, message);
		return true;
	}

}
