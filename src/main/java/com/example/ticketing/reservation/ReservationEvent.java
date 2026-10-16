package com.example.ticketing.reservation;

import java.time.LocalDateTime;

/**
 * 예매에서 "일어난 일"을 알리는 이벤트. 예매 쪽은 누가 이걸 듣는지 모른다 (알림, 통계, 포인트 ...).
 * 이벤트에는 엔티티 대신 필요한 값을 복사해 담는다. 듣는 쪽은 다른 스레드/트랜잭션이라 지연 로딩을 할 수 없다.
 */
public sealed interface ReservationEvent {

	Long reservationId();

	Long memberId();

	record Confirmed(Long reservationId, Long memberId, String performanceTitle, LocalDateTime startAt,
			String seat, int amount) implements ReservationEvent {
	}

	record Cancelled(Long reservationId, Long memberId, String performanceTitle, String seat,
			boolean refunded) implements ReservationEvent {
	}

	static Confirmed confirmed(Reservation r) {
		return new Confirmed(r.getId(), r.getMemberId(), r.getSeat().getPerformance().getTitle(),
				r.getSeat().getPerformance().getStartAt(), seatName(r), r.getSeat().getPrice());
	}

	static Cancelled cancelled(Reservation r, boolean refunded) {
		return new Cancelled(r.getId(), r.getMemberId(), r.getSeat().getPerformance().getTitle(), seatName(r),
				refunded);
	}

	private static String seatName(Reservation r) {
		return r.getSeat().getSection() + "구역 " + r.getSeat().getSeatNumber() + "번";
	}

}
