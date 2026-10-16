package com.example.ticketing.reservation;

import java.time.LocalDateTime;

import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.performance.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seat_id")
	private Seat seat;

	@Column(nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationStatus status;

	@Column(nullable = false)
	private LocalDateTime reservedAt;

	/** 결제사(PG)가 돌려준 결제 식별자. 환불할 때 쓴다. */
	private String paymentKey;

	private LocalDateTime paidAt;

	protected Reservation() {
	}

	/** 좌석을 먼저 잡아 두고(PENDING), 결제가 승인되면 CONFIRMED가 된다. */
	public Reservation(Seat seat, Long memberId) {
		this.seat = seat;
		this.memberId = memberId;
		this.status = ReservationStatus.PENDING;
		this.reservedAt = LocalDateTime.now();
	}

	public boolean isOwnedBy(Long memberId) {
		return this.memberId.equals(memberId);
	}

	public boolean isPending() {
		return status == ReservationStatus.PENDING;
	}

	/** PG에 보낼 주문 번호. 같은 예매는 항상 같은 값이라 결제 재시도 시 중복 결제 방지 키로도 쓴다. */
	public String orderId() {
		return "R-" + id;
	}

	public void confirmPayment(String paymentKey) {
		if (!isPending()) {
			throw new BusinessException(ErrorCode.RESERVATION_NOT_PAYABLE);
		}
		this.status = ReservationStatus.CONFIRMED;
		this.paymentKey = paymentKey;
		this.paidAt = LocalDateTime.now();
	}

	public void cancel() {
		if (status == ReservationStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
		}
		this.status = ReservationStatus.CANCELLED;
		seat.release();
	}

	public Long getId() {
		return id;
	}

	public Seat getSeat() {
		return seat;
	}

	public Long getMemberId() {
		return memberId;
	}

	public ReservationStatus getStatus() {
		return status;
	}

	public LocalDateTime getReservedAt() {
		return reservedAt;
	}

	public String getPaymentKey() {
		return paymentKey;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

}
