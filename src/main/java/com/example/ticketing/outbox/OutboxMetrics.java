package com.example.ticketing.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * "아직 Kafka로 못 보낸 이벤트 수" 게이지. 평소엔 0 근처여야 한다.
 * 계속 늘어나면 Kafka가 죽었거나 릴레이가 멈춘 것 → Grafana 경보 대상 1순위.
 *
 * Gauge는 값을 저장하지 않고, Prometheus가 긁어 갈 때마다(15초) 함수를 호출해 현재 값을 읽는다.
 */
@Component
class OutboxMetrics implements MeterBinder {

	private final OutboxEventRepository outboxEventRepository;

	OutboxMetrics(OutboxEventRepository outboxEventRepository) {
		this.outboxEventRepository = outboxEventRepository;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder("ticketing.outbox.pending", outboxEventRepository, OutboxEventRepository::countByPublishedAtIsNull)
			.description("Kafka로 아직 발행되지 않은 Outbox 이벤트 수")
			.register(registry);
	}

}
