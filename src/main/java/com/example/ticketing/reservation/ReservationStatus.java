package com.example.ticketing.reservation;

public enum ReservationStatus {

	/** 좌석만 잡아 둔 상태. 결제 승인을 기다린다. */
	PENDING,

	/** 결제까지 끝난 예매 */
	CONFIRMED,

	CANCELLED

}
