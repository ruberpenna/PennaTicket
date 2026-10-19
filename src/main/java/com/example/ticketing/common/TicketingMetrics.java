package com.example.ticketing.common;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 비즈니스 지표. JVM/HTTP/DB 커넥션 풀 같은 기술 지표는 Spring Boot가 자동으로 만들고,
 * "예매가 몇 건 성공/거절됐나", "결제가 얼마나 실패하나" 같은 업무 지표는 직접 만든다.
 *
 * Prometheus에서는 이름의 점(.)이 밑줄로 바뀌고 카운터에는 _total이 붙는다.
 * 예) ticketing.reservations → ticketing_reservations_total{strategy="PESSIMISTIC",result="reserved"}
 */
@Component
public class TicketingMetrics {

	private final MeterRegistry registry;

	public TicketingMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	/** 좌석 예매 시도 결과와 처리 시간 (동시성 제어 방식별) */
	public void reservation(String strategy, boolean reserved, Duration elapsed) {
		String result = reserved ? "reserved" : "rejected";
		Counter.builder("ticketing.reservations")
			.description("좌석 예매 시도 수")
			.tag("strategy", strategy)
			.tag("result", result)
			.register(registry)
			.increment();
		Timer.builder("ticketing.reservation.duration")
			.description("좌석 예매 처리 시간")
			.tag("strategy", strategy)
			.tag("result", result)
			.register(registry)
			.record(elapsed);
	}

	/** 결제 결과: approved | declined(카드 거절) | unavailable(PG 장애) */
	public void payment(String result) {
		Counter.builder("ticketing.payments").description("결제 시도 수").tag("result", result).register(registry).increment();
	}

	/** 알림 처리 결과: sent | duplicate(같은 이벤트 중복 수신) */
	public void notification(String result) {
		Counter.builder("ticketing.notifications")
			.description("알림 처리 수")
			.tag("result", result)
			.register(registry)
			.increment();
	}

}
