package com.example.ticketing.payment;

import com.example.ticketing.payment.PaymentClient.ApproveRequest;
import com.example.ticketing.payment.PaymentClient.ApproveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * PaymentClient 호출에 재시도 정책을 입힌다.
 * @Retryable은 프록시로 동작하므로 반드시 "다른 빈"에서 이 메서드를 불러야 적용된다 (같은 클래스 안 호출 X).
 */
@Component
public class PaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);

	private final PaymentClient paymentClient;

	public PaymentGateway(PaymentClient paymentClient) {
		this.paymentClient = paymentClient;
	}

	/**
	 * 재시도 대상: 일시적인 장애만
	 * - ResourceAccessException: 연결 실패, 타임아웃
	 * - HttpServerErrorException: PG 5xx
	 * 카드 거절 같은 4xx(HttpClientErrorException)는 다시 보내도 결과가 같으니 재시도하지 않는다.
	 *
	 * 최초 1번 + 재시도 2번, 대기 200ms → 400ms (지수 백오프, ±50ms 지터).
	 * 같은 주문 번호를 Idempotency-Key로 보내므로 재시도해도 중복 결제되지 않는다.
	 */
	@Retryable(includes = { ResourceAccessException.class, HttpServerErrorException.class }, maxRetries = 2,
			delay = 200, multiplier = 2, jitter = 50)
	public ApproveResponse approve(String orderId, long amount, String paymentToken) {
		log.info("PG 승인 요청 orderId={} amount={}", orderId, amount);
		return paymentClient.approve(orderId, new ApproveRequest(orderId, amount, paymentToken));
	}

	public void cancel(String paymentKey) {
		log.info("PG 결제 취소 paymentKey={}", paymentKey);
		paymentClient.cancel(paymentKey);
	}

}
