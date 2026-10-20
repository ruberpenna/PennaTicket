package com.example.ticketing.common;

import java.time.Duration;
import java.util.List;

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

	/**
	 * 카운터를 0으로 미리 만들어 둔다.
	 * 처음 실패한 순간에야 카운터가 생기면 Prometheus에는 "이전 값"이 없어서 increase()가 0으로 계산되고,
	 * 첫 장애에 경보가 울리지 않는다. (실제로 PG 장애 2건이 경보에 안 잡혀서 발견)
	 */
	public TicketingMetrics(MeterRegistry registry) {
		this.registry = registry;
		for (String result : List.of("approved", "declined", "unavailable")) {
			paymentCounter(result);
		}
		for (String result : List.of("sent", "duplicate")) {
			notificationCounter(result);
		}
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
		paymentCounter(result).increment();
	}

	/** 알림 처리 결과: sent | duplicate(같은 이벤트 중복 수신) */
	public void notification(String result) {
		notificationCounter(result).increment();
	}

	// register()는 같은 이름+태그면 이미 만든 카운터를 돌려준다 (매번 새로 만들지 않음)
	private Counter paymentCounter(String result) {
		return Counter.builder("ticketing.payments").description("결제 시도 수").tag("result", result).register(registry);
	}

	private Counter notificationCounter(String result) {
		return Counter.builder("ticketing.notifications")
			.description("알림 처리 수")
			.tag("result", result)
			.register(registry);
	}

}
