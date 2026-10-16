package com.example.ticketing.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox에 쌓인 이벤트를 Kafka로 옮긴다 (릴레이).
 *
 * 보내기 → Kafka가 받았다고 확인(ack) → published_at 표시. 이 순서라서
 * Kafka로 보낸 뒤 표시하기 전에 죽으면 다음에 "한 번 더" 보낸다 → 최소 한 번(at-least-once) 전달.
 * 그래서 받는 쪽은 같은 이벤트가 두 번 와도 괜찮게 만들어야 한다 (eventId로 중복 무시).
 */
@Component
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	public static final String HEADER_EVENT_ID = "eventId";
	public static final String HEADER_EVENT_TYPE = "eventType";

	private static final int BATCH_SIZE = 100;

	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String topic;

	public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate,
			@Value("${ticketing.kafka.reservation-topic}") String topic) {
		this.outboxEventRepository = outboxEventRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
	}

	/** @return 이번에 보낸 개수 */
	@Transactional
	public int publishPending() {
		List<OutboxEvent> pending = outboxEventRepository.lockUnpublished(BATCH_SIZE);
		int sent = 0;
		for (OutboxEvent event : pending) {
			try {
				ProducerRecord<String, String> record = new ProducerRecord<>(topic,
						String.valueOf(event.getAggregateId()), // 키가 같으면 같은 파티션 → 같은 예매의 이벤트는 순서대로
						event.getPayload());
				record.headers()
					.add(HEADER_EVENT_ID, String.valueOf(event.getId()).getBytes(StandardCharsets.UTF_8))
					.add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));

				kafkaTemplate.send(record).get(5, TimeUnit.SECONDS); // Kafka가 저장했다고 답할 때까지 기다림
				event.markPublished();
				sent++;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			catch (Exception e) {
				// Kafka가 죽어 있으면 여기서 멈춘다. 표시 안 된 이벤트는 DB에 남아 있다가 다음 주기에 다시 보낸다.
				log.warn("Outbox 이벤트 발행 실패 id={} → 다음 주기에 재시도 ({})", event.getId(), e.getMessage());
				break;
			}
		}
		if (sent > 0) {
			log.info("Outbox → Kafka 발행 {}건", sent);
		}
		return sent;
	}

}
