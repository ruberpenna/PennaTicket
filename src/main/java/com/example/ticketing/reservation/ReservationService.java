package com.example.ticketing.reservation;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.common.PageResponse;
import com.example.ticketing.common.TicketingMetrics;
import com.example.ticketing.reservation.ReservationDtos.GradeSalesResponse;
import com.example.ticketing.reservation.ReservationDtos.MyReservationResponse;
import com.example.ticketing.reservation.ReservationDtos.PaymentResponse;
import com.example.ticketing.reservation.ReservationDtos.PaymentTarget;
import com.example.ticketing.reservation.lock.LockStrategy;
import com.example.ticketing.reservation.lock.SeatReserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

	private static final int MAX_PAGE_SIZE = 50;

	private final ReservationRepository reservationRepository;
	private final Map<LockStrategy, SeatReserver> seatReservers;
	private final LockStrategy defaultStrategy;
	private final ApplicationEventPublisher events;
	private final TicketingMetrics metrics;

	/** SeatReserver 구현 빈 5개가 List로 주입된다 → 전략별 Map으로 정리 */
	public ReservationService(ReservationRepository reservationRepository, List<SeatReserver> seatReservers,
			@Value("${ticketing.reservation.lock-strategy:PESSIMISTIC}") LockStrategy defaultStrategy,
			ApplicationEventPublisher events, TicketingMetrics metrics) {
		this.reservationRepository = reservationRepository;
		this.seatReservers = seatReservers.stream()
			.collect(Collectors.toUnmodifiableMap(SeatReserver::strategy, Function.identity()));
		this.defaultStrategy = defaultStrategy;
		this.events = events;
		this.metrics = metrics;
	}

	/** 설정(ticketing.reservation.lock-strategy)에 지정된 방식으로 좌석을 잡는다. */
	public ReservationResult reserve(Long seatId, Long memberId) {
		return reserve(seatId, memberId, defaultStrategy);
	}

	/** 방식을 직접 골라서 예매 (동시성 비교 테스트용) */
	public ReservationResult reserve(Long seatId, Long memberId, LockStrategy strategy) {
		long started = System.nanoTime();
		ReservationResult result = seatReservers.get(strategy).reserve(seatId, memberId);
		metrics.reservation(strategy.name(), result instanceof ReservationResult.Reserved,
				Duration.ofNanos(System.nanoTime() - started));
		return result;
	}

	@Transactional
	public void cancel(Long reservationId, Long memberId) {
		Reservation reservation = getOwnedReservation(reservationId, memberId);
		boolean refunded = reservation.getPaymentKey() != null;
		reservation.cancel();
		events.publishEvent(ReservationEvent.cancelled(reservation, refunded));
	}

	/** 결제에 필요한 정보만 꺼내고 트랜잭션은 바로 끝낸다. PG 호출은 트랜잭션 밖에서 한다. */
	@Transactional(readOnly = true)
	public PaymentTarget preparePayment(Long reservationId, Long memberId) {
		Reservation reservation = getOwnedReservation(reservationId, memberId);
		if (!reservation.isPending()) {
			throw new BusinessException(ErrorCode.RESERVATION_NOT_PAYABLE);
		}
		return new PaymentTarget(reservation.orderId(), reservation.getSeat().getPrice());
	}

	@Transactional
	public PaymentResponse confirmPayment(Long reservationId, String paymentKey) {
		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		reservation.confirmPayment(paymentKey);
		// 지금 바로 알림을 보내지 않고 "확정됐다"고 알리기만 한다.
		// 듣는 쪽(@TransactionalEventListener)은 이 트랜잭션이 커밋된 뒤에야 실행된다 → 롤백되면 알림도 안 간다.
		events.publishEvent(ReservationEvent.confirmed(reservation));
		return PaymentResponse.from(reservation);
	}

	/** 취소 전에 환불할 결제가 있는지 확인한다. 결제 전이면 null */
	@Transactional(readOnly = true)
	public String findPaymentKeyToRefund(Long reservationId, Long memberId) {
		Reservation reservation = getOwnedReservation(reservationId, memberId);
		if (reservation.getStatus() == ReservationStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
		}
		return reservation.getPaymentKey();
	}

	private Reservation getOwnedReservation(Long reservationId, Long memberId) {
		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		if (!reservation.isOwnedBy(memberId)) {
			throw new BusinessException(ErrorCode.RESERVATION_FORBIDDEN);
		}
		return reservation;
	}

	/** 엔티티 → DTO 변환까지 트랜잭션 안에서 끝낸다 (open-in-view: false). */
	@Transactional(readOnly = true)
	public PageResponse<MyReservationResponse> findMyReservations(Long memberId, int page, int size) {
		PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
		return PageResponse
			.from(reservationRepository.findMyReservations(memberId, pageable).map(MyReservationResponse::from));
	}

	@Transactional(readOnly = true)
	public List<GradeSalesResponse> salesByGrade(Long performanceId) {
		return reservationRepository.salesByGrade(performanceId).stream().map(GradeSalesResponse::from).toList();
	}

}
