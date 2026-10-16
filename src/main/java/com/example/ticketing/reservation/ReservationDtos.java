package com.example.ticketing.reservation;

import java.time.LocalDateTime;

import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.reservation.ReservationRepository.GradeSales;
import jakarta.validation.constraints.NotBlank;

public final class ReservationDtos {

	private ReservationDtos() {
	}

	public record MyReservationResponse(Long reservationId, ReservationStatus status, LocalDateTime reservedAt,
			Long performanceId, String performanceTitle, LocalDateTime startAt, String section, String seatNumber,
			SeatGrade grade, int price) {

		/** 트랜잭션 안에서 호출해야 한다. fetch join으로 가져왔기 때문에 여기서 추가 쿼리는 나가지 않는다. */
		static MyReservationResponse from(Reservation r) {
			Seat s = r.getSeat();
			Performance p = s.getPerformance();
			return new MyReservationResponse(r.getId(), r.getStatus(), r.getReservedAt(), p.getId(), p.getTitle(),
					p.getStartAt(), s.getSection(), s.getSeatNumber(), s.getGrade(), s.getPrice());
		}

	}

	/** PG에 보낼 값: 주문 번호(멱등성 키 겸용)와 금액 */
	public record PaymentTarget(String orderId, long amount) {
	}

	public record PaymentRequest(@NotBlank String paymentToken) {
	}

	public record PaymentResponse(Long reservationId, ReservationStatus status, String paymentKey, int amount,
			LocalDateTime paidAt) {

		static PaymentResponse from(Reservation r) {
			return new PaymentResponse(r.getId(), r.getStatus(), r.getPaymentKey(), r.getSeat().getPrice(),
					r.getPaidAt());
		}

	}

	public record GradeSalesResponse(String grade, long soldCount, long amount) {

		static GradeSalesResponse from(GradeSales sales) {
			return new GradeSalesResponse(sales.getGrade(), sales.getSoldCount(), sales.getAmount());
		}

	}

}
