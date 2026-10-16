package com.example.ticketing.mockpg;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발용 "가짜 결제사(PG)". 실제로는 토스페이먼츠/나이스페이 같은 외부 서버 자리다.
 * 우리 앱은 이 컨트롤러를 직접 부르지 않고, HTTP로 호출한다 (PaymentClient → http://localhost:8090/mock-pg).
 *
 * paymentToken 값으로 상황을 흉내 낸다. (실제 PG에서는 결제창이 카드 정보 대신 이런 토큰을 넘겨준다)
 * <ul>
 * <li>tok_success : 승인</li>
 * <li>tok_declined : 카드 거절 (400) → 재시도하지 않아야 함</li>
 * <li>tok_flaky : 같은 주문의 첫 요청만 503, 두 번째부터 승인 → 재시도로 성공해야 함</li>
 * <li>tok_timeout : 응답을 늦게 줌 → 클라이언트 read-timeout → 재시도 후 실패</li>
 * </ul>
 */
@Hidden // Swagger 문서에서 숨김 (우리 서비스 API가 아님)
@RestController
@RequestMapping("/mock-pg/v1/payments")
public class MockPgController {

	private static final Logger log = LoggerFactory.getLogger(MockPgController.class);

	public record ApproveRequest(String orderId, long amount, String paymentToken) {
	}

	public record ApproveResponse(String paymentKey, String orderId, long amount, LocalDateTime approvedAt) {
	}

	public record CancelResponse(String paymentKey, String status) {
	}

	public record ErrorResponse(String code, String message) {
	}

	/** 멱등성 키(= 주문 번호)별 승인 결과. 같은 키로 다시 오면 새로 결제하지 않고 같은 결과를 돌려준다. */
	private final Map<String, ApproveResponse> approved = new ConcurrentHashMap<>();

	private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

	private final Duration slowResponseDelay;

	public MockPgController(@Value("${ticketing.mock-pg.slow-response-delay:3s}") Duration slowResponseDelay) {
		this.slowResponseDelay = slowResponseDelay;
	}

	@PostMapping
	ResponseEntity<?> approve(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestBody ApproveRequest request) throws InterruptedException {
		int attempt = attempts.computeIfAbsent(idempotencyKey, k -> new AtomicInteger()).incrementAndGet();
		log.info("[MOCK-PG] 승인 요청 key={} token={} amount={} (시도 {}번째)", idempotencyKey, request.paymentToken(),
				request.amount(), attempt);

		ApproveResponse already = approved.get(idempotencyKey);
		if (already != null) {
			log.info("[MOCK-PG] 이미 승인된 키 → 같은 결과 반환 (중복 결제 안 함)");
			return ResponseEntity.ok(already);
		}

		return switch (request.paymentToken()) {
			case "tok_success" -> ResponseEntity.ok(approveNow(idempotencyKey, request));
			case "tok_declined" -> ResponseEntity.badRequest()
				.body(new ErrorResponse("CARD_DECLINED", "카드 한도가 초과되었습니다."));
			case "tok_flaky" -> attempt == 1
					? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
						.body(new ErrorResponse("TEMPORARY_ERROR", "일시적인 오류"))
					: ResponseEntity.ok(approveNow(idempotencyKey, request));
			case "tok_timeout" -> {
				Thread.sleep(slowResponseDelay); // 가상 스레드라 잠들어도 부담이 적다
				yield ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
					.body(new ErrorResponse("TIMEOUT", "응답 지연"));
			}
			default -> ResponseEntity.badRequest().body(new ErrorResponse("INVALID_TOKEN", "알 수 없는 결제 토큰"));
		};
	}

	@PostMapping("/{paymentKey}/cancel")
	ResponseEntity<?> cancel(@PathVariable String paymentKey) {
		boolean exists = approved.values().stream().anyMatch(a -> a.paymentKey().equals(paymentKey));
		log.info("[MOCK-PG] 결제 취소 paymentKey={} exists={}", paymentKey, exists);
		if (!exists) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("NOT_FOUND", "결제 내역이 없습니다."));
		}
		return ResponseEntity.ok(new CancelResponse(paymentKey, "CANCELLED"));
	}

	/** 테스트에서 "몇 번 호출됐나(재시도 횟수)"를 확인하는 용도 */
	public int attemptCount(String idempotencyKey) {
		AtomicInteger count = attempts.get(idempotencyKey);
		return (count != null) ? count.get() : 0;
	}

	private ApproveResponse approveNow(String idempotencyKey, ApproveRequest request) {
		ApproveResponse response = new ApproveResponse("pay_" + UUID.randomUUID(), request.orderId(),
				request.amount(), LocalDateTime.now());
		approved.put(idempotencyKey, response);
		return response;
	}

}
