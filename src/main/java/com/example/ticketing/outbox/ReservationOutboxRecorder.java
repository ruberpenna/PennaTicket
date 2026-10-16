package com.example.ticketing.outbox;

import com.example.ticketing.reservation.ReservationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * 예매 이벤트를 Outbox 테이블에 적는다.
 *
 * BEFORE_COMMIT: 이벤트를 발행한 트랜잭션이 커밋되기 "직전", 같은 트랜잭션 안에서 실행된다.
 * → 예매 상태 변경과 outbox 행이 함께 커밋되거나 함께 롤백된다 (둘 중 하나만 저장될 수 없음).
 * 예매 코드(ReservationService)는 그대로 publishEvent만 하고, Outbox가 있는지도 모른다.
 */
@Component
class ReservationOutboxRecorder {

	static final String AGGREGATE_TYPE = "Reservation";

	private final OutboxEventRepository outboxEventRepository;
	private final JsonMapper jsonMapper;

	ReservationOutboxRecorder(OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper) {
		this.outboxEventRepository = outboxEventRepository;
		this.jsonMapper = jsonMapper;
	}

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	void record(ReservationEvent event) {
		outboxEventRepository.save(new OutboxEvent(AGGREGATE_TYPE, event.reservationId(),
				event.getClass().getSimpleName(), jsonMapper.writeValueAsString(event)));
	}

}
