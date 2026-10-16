package com.example.ticketing.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 짧은 주기로 Outbox를 확인해 Kafka로 보낸다 (폴링 방식).
 * ticketing.outbox.relay.enabled=false면 꺼진다 → "릴레이가 멈춘 동안 이벤트가 쌓였다가 다시 나가는지" 테스트용.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "ticketing.outbox.relay.enabled", matchIfMissing = true)
class OutboxRelayScheduler {

	private final OutboxRelay outboxRelay;

	OutboxRelayScheduler(OutboxRelay outboxRelay) {
		this.outboxRelay = outboxRelay;
	}

	@Scheduled(fixedDelayString = "${ticketing.outbox.relay.interval:300ms}")
	void relay() {
		outboxRelay.publishPending();
	}

}
