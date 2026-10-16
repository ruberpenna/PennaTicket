package com.example.ticketing.reservation.lock;

import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.reservation.ReservationResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ③ 조건부 UPDATE: UPDATE seat SET status = 'RESERVED' WHERE id = ? AND status = 'AVAILABLE'
 * "확인하고 바꾸기"를 DB 한 문장으로 처리해서 그 사이에 끼어들 틈이 없다.
 * 장점: 조회가 없어 가장 짧고 빠른 경우가 많다. 단점: 엔티티 메서드(seat.reserve())의 규칙을 쿼리로 중복해서 적어야 한다.
 */
@Component
class ConditionalUpdateSeatReserver implements SeatReserver {

	private final SeatRepository seatRepository;
	private final SeatReservationSupport support;

	ConditionalUpdateSeatReserver(SeatRepository seatRepository, SeatReservationSupport support) {
		this.seatRepository = seatRepository;
		this.support = support;
	}

	@Override
	public LockStrategy strategy() {
		return LockStrategy.CONDITIONAL_UPDATE;
	}

	@Override
	@Transactional
	public ReservationResult reserve(Long seatId, Long memberId) {
		if (seatRepository.reserveIfAvailable(seatId) == 0) {
			support.findSeat(seatId); // 좌석이 아예 없으면 404, 있으면 이미 예매된 것
			return SeatReservationSupport.rejected(seatId);
		}
		return support.saveReservation(seatRepository.getReferenceById(seatId), memberId);
	}

}
