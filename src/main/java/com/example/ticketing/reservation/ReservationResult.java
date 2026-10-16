package com.example.ticketing.reservation;

/**
 * sealed interface: 예매 결과는 이 두 가지뿐이라는 걸 컴파일러가 안다.
 * 그래서 switch 패턴 매칭에서 default 없이도 빠짐없이 처리했는지 검사해 준다.
 */
public sealed interface ReservationResult {

	record Reserved(Long reservationId, Long seatId) implements ReservationResult {
	}

	record Rejected(Long seatId, String reason) implements ReservationResult {
	}

}
