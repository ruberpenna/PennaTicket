package com.example.ticketing.notification;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.example.ticketing.outbox.OutboxRelay;
import com.example.ticketing.reservation.ReservationEvent;
import com.example.ticketing.reservation.ReservationEvent.Cancelled;
import com.example.ticketing.reservation.ReservationEvent.Confirmed;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka의 예매 이벤트를 읽어 알림을 보낸다.
 *
 * 6-1단계(@TransactionalEventListener + @Async)와 달리 이벤트가 DB(Outbox)와 Kafka에 남아 있어서
 * 앱이 꺼졌다 켜져도, 이 소비자가 잠시 죽어 있어도 이어서 처리한다.
 * 처리가 끝나면 Spring Kafka가 offset("어디까지 읽었나")을 커밋한다. 처리 중 예외가 나면 다시 받는다.
 */
@Component
public class ReservationEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(ReservationEventConsumer.class);

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

	private final NotificationSender sender;
	private final JsonMapper jsonMapper;
	private final Duration simulatedDelay;

	public ReservationEventConsumer(NotificationSender sender, JsonMapper jsonMapper,
			@Value("${ticketing.notification.simulated-delay:1s}") Duration simulatedDelay) {
		this.sender = sender;
		this.jsonMapper = jsonMapper;
		this.simulatedDelay = simulatedDelay;
	}

	@KafkaListener(topics = "${ticketing.kafka.reservation-topic}")
	public void on(ConsumerRecord<String, String> record) throws InterruptedException {
		Long eventId = Long.valueOf(header(record, OutboxRelay.HEADER_EVENT_ID));
		String eventType = header(record, OutboxRelay.HEADER_EVENT_TYPE);
		log.info("Kafka 수신 partition={} offset={} key={} eventId={} type={}", record.partition(), record.offset(),
				record.key(), eventId, eventType);

		ReservationEvent event = switch (eventType) {
			case "Confirmed" -> jsonMapper.readValue(record.value(), Confirmed.class);
			case "Cancelled" -> jsonMapper.readValue(record.value(), Cancelled.class);
			default -> null;
		};
		if (event == null) {
			log.warn("모르는 이벤트 종류라 건너뜀 type={}", eventType); // 새 이벤트가 생겨도 기존 소비자가 죽지 않게
			return;
		}

		Thread.sleep(simulatedDelay); // 외부 알림 API가 느린 상황 흉내

		switch (event) {
			case Confirmed c -> sender.send(eventId, c.memberId(), NotificationType.RESERVATION_CONFIRMED,
					"[예매 완료] %s %s (%s) %s원".formatted(c.performanceTitle(), c.seat(),
							c.startAt().format(DATE_TIME), won(c.amount())));
			case Cancelled c -> sender.send(eventId, c.memberId(), NotificationType.RESERVATION_CANCELLED,
					"[예매 취소] %s %s%s".formatted(c.performanceTitle(), c.seat(),
							c.refunded() ? " - 결제 금액이 환불됩니다." : ""));
		}
	}

	private static String header(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		return (header != null) ? new String(header.value(), StandardCharsets.UTF_8) : "";
	}

	private static String won(int amount) {
		return NumberFormat.getNumberInstance(Locale.KOREA).format(amount);
	}

}
