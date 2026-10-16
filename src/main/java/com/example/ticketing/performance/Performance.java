package com.example.ticketing.performance;

import java.time.LocalDateTime;

import com.example.ticketing.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Performance extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String venue;

	@Column(nullable = false)
	private LocalDateTime startAt;

	protected Performance() {
	}

	public Performance(String title, String venue, LocalDateTime startAt) {
		this.title = title;
		this.venue = venue;
		this.startAt = startAt;
	}

	public void changeInfo(String title, String venue, LocalDateTime startAt) {
		this.title = title;
		this.venue = venue;
		this.startAt = startAt;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getVenue() {
		return venue;
	}

	public LocalDateTime getStartAt() {
		return startAt;
	}

}
