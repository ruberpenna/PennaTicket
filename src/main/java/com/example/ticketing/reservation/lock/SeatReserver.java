package com.example.ticketing.reservation.lock;

import com.example.ticketing.reservation.ReservationResult;

/** 좌석 하나를 한 명에게만 예매해 주는 방법(전략). 구현마다 동시성 제어 방식이 다르다. */
public interface SeatReserver {

	LockStrategy strategy();

	ReservationResult reserve(Long seatId, Long memberId);

}
