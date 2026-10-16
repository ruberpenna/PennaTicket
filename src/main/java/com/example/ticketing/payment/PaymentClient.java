package com.example.ticketing.payment;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * HTTP Interface: 외부 API를 "인터페이스 + 어노테이션"으로 선언하면 구현은 Spring이 만든다.
 * 예전처럼 RestTemplate으로 URL 문자열 조립 → exchange() → 응답 파싱을 직접 하지 않는다.
 * (Feign과 같은 방식인데 Spring 기본 기능이라 의존성이 필요 없다)
 *
 * 주소/타임아웃은 application.yml의 spring.http.serviceclient.payment.* 에서 설정한다.
 */
@HttpExchange("/v1/payments")
public interface PaymentClient {

	/** Idempotency-Key: 같은 키로 여러 번 보내도 결제는 한 번만 되도록 PG에 알려 주는 값 */
	@PostExchange
	ApproveResponse approve(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestBody ApproveRequest request);

	@PostExchange("/{paymentKey}/cancel")
	CancelResponse cancel(@PathVariable String paymentKey);

	record ApproveRequest(String orderId, long amount, String paymentToken) {
	}

	record ApproveResponse(String paymentKey, String orderId, long amount, LocalDateTime approvedAt) {
	}

	record CancelResponse(String paymentKey, String status) {
	}

	/** PG가 4xx/5xx 때 주는 에러 본문 */
	record PgError(String code, String message) {
	}

}
