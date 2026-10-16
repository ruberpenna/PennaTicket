package com.example.ticketing.reservation.lock;

import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.reservation.Reservation;
import com.example.ticketing.reservation.ReservationRepository;
import com.example.ticketing.reservation.ReservationResult;
import com.example.ticketing.reservation.ReservationResult.Rejected;
import com.example.ticketing.reservation.ReservationResult.Reserved;
import org.springframework.stereotype.Component;

/** 전략들이 공통으로 쓰는 "좌석 확인 → 예매 처리 → 예매 저장". 호출하는 쪽의 트랜잭션 안에서 실행된다. */
@Component
class SeatReservationSupport {

	private final SeatRepository seatRepository;
	private final ReservationRepository reservationRepository;

	SeatReservationSupport(SeatRepository seatRepository, ReservationRepository reservationRepository) {
		this.seatRepository = seatRepository;
		this.reservationRepository = reservationRepository;
	}

	Seat findSeat(Long seatId) {
		return seatRepository.findById(seatId).orElseThrow(() -> new BusinessException(ErrorCode.SEAT_NOT_FOUND));
	}

	ReservationResult reserveIfAvailable(Seat seat, Long memberId) {
		if (!seat.isAvailable()) {
			return rejected(seat.getId());
		}
		seat.reserve();
		return saveReservation(seat, memberId);
	}

	ReservationResult saveReservation(Seat seat, Long memberId) {
		Reservation reservation = reservationRepository.save(new Reservation(seat, memberId));
		return new Reserved(reservation.getId(), seat.getId());
	}

	static Rejected rejected(Long seatId) {
		return new Rejected(seatId, ErrorCode.SEAT_NOT_AVAILABLE.message());
	}

}
