package com.example.ticketing.performance;

import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
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
import jakarta.persistence.Version;

@Entity
public class Seat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "performance_id")
	private Performance performance;

	@Column(nullable = false)
	private String section;

	@Column(nullable = false)
	private String seatNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SeatGrade grade;

	@Column(nullable = false)
	private int price;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SeatStatus status = SeatStatus.AVAILABLE;

	/** 낙관적 락 실험용. 지금은 비관적 락(SeatRepository#findByIdForUpdate)을 쓴다. */
	@Version
	private Long version;

	protected Seat() {
	}

	public Seat(Performance performance, String section, String seatNumber, SeatGrade grade, int price) {
		this.performance = performance;
		this.section = section;
		this.seatNumber = seatNumber;
		this.grade = grade;
		this.price = price;
	}

	public boolean isAvailable() {
		return status == SeatStatus.AVAILABLE;
	}

	public void reserve() {
		if (!isAvailable()) {
			throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
		}
		this.status = SeatStatus.RESERVED;
	}

	public void release() {
		this.status = SeatStatus.AVAILABLE;
	}

	public Long getId() {
		return id;
	}

	public Performance getPerformance() {
		return performance;
	}

	public String getSection() {
		return section;
	}

	public String getSeatNumber() {
		return seatNumber;
	}

	public SeatGrade getGrade() {
		return grade;
	}

	public int getPrice() {
		return price;
	}

	public SeatStatus getStatus() {
		return status;
	}

}
