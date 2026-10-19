package com.example.ticketing.reservation;

import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.common.TicketingMetrics;
import com.example.ticketing.payment.PaymentClient.ApproveResponse;
import com.example.ticketing.payment.PaymentClient.PgError;
import com.example.ticketing.payment.PaymentGateway;
import com.example.ticketing.reservation.ReservationDtos.PaymentResponse;
import com.example.ticketing.reservation.ReservationDtos.PaymentTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 예매 결제/환불 흐름. 일부러 @Transactional을 붙이지 않는다.
 *
 * 외부 API(PG) 호출을 DB 트랜잭션 안에서 하면, PG가 느릴 때 DB 커넥션과 락을 그만큼 오래 붙잡는다.
 * 그래서 [트랜잭션1: 검증] → [PG 호출] → [트랜잭션2: 확정] 으로 나눈다.
 */
@Service
public class ReservationPaymentService {

	private static final Logger log = LoggerFactory.getLogger(ReservationPaymentService.class);

	private final ReservationService reservationService;
	private final PaymentGateway paymentGateway;
	private final TicketingMetrics metrics;

	public ReservationPaymentService(ReservationService reservationService, PaymentGateway paymentGateway,
			TicketingMetrics metrics) {
		this.reservationService = reservationService;
		this.paymentGateway = paymentGateway;
		this.metrics = metrics;
	}

	public PaymentResponse pay(Long reservationId, Long memberId, String paymentToken) {
		PaymentTarget target = reservationService.preparePayment(reservationId, memberId);

		ApproveResponse approved;
		try {
			approved = paymentGateway.approve(target.orderId(), target.amount(), paymentToken);
			metrics.payment("approved");
		}
		catch (RuntimeException e) {
			BusinessException failure = toBusinessException(e);
			metrics.payment(failure.errorCode() == ErrorCode.PAYMENT_DECLINED ? "declined" : "unavailable");
			throw failure;
		}

		try {
			return reservationService.confirmPayment(reservationId, approved.paymentKey());
		}
		catch (BusinessException e) {
			// 결제하는 사이 예매가 취소된 경우: 돈은 빠져나갔는데 예매는 없다 → 결제를 되돌린다(보상 처리)
			log.warn("결제 승인 후 예매 확정 실패 → PG 결제 취소 reservationId={}", reservationId);
			paymentGateway.cancel(approved.paymentKey());
			throw e;
		}
	}

	/** 결제된 예매면 PG 환불이 성공한 뒤에만 DB를 취소 상태로 바꾼다. */
	public void cancel(Long reservationId, Long memberId) {
		String paymentKey = reservationService.findPaymentKeyToRefund(reservationId, memberId);
		if (paymentKey != null) {
			try {
				paymentGateway.cancel(paymentKey);
			}
			catch (RuntimeException e) {
				throw toBusinessException(e);
			}
		}
		reservationService.cancel(reservationId, memberId);
	}

	/** 4xx = PG가 거절(재시도해도 같음), 그 외(타임아웃, 5xx, 연결 실패) = 통신 장애 */
	private BusinessException toBusinessException(RuntimeException e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof HttpClientErrorException clientError) {
				PgError error = clientError.getResponseBodyAs(PgError.class);
				String reason = (error != null) ? error.message() : clientError.getStatusText();
				return new BusinessException(ErrorCode.PAYMENT_DECLINED, "결제가 거절되었습니다: " + reason);
			}
		}
		log.error("PG 통신 실패", e);
		return new BusinessException(ErrorCode.PAYMENT_UNAVAILABLE);
	}

}
