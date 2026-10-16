package com.example.ticketing.reservation.lock;

import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.reservation.ReservationResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ① 비관적 락: SELECT ... FOR UPDATE
 * 같은 좌석을 노리는 트랜잭션은 앞 트랜잭션이 커밋할 때까지 DB에서 기다린다.
 * 장점: 단순하고 확실. 단점: 기다리는 동안 DB 커넥션을 붙잡고 있다.
 */
@Component
class PessimisticSeatReserver implements SeatReserver {

	private final SeatRepository seatRepository;
	private final SeatReservationSupport support;

	PessimisticSeatReserver(SeatRepository seatRepository, SeatReservationSupport support) {
		this.seatRepository = seatRepository;
		this.support = support;
	}

	@Override
	public LockStrategy strategy() {
		return LockStrategy.PESSIMISTIC;
	}

	@Override
	@Transactional
	public ReservationResult reserve(Long seatId, Long memberId) {
		Seat seat = seatRepository.findByIdForUpdate(seatId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));
		return support.reserveIfAvailable(seat, memberId);
	}

}
