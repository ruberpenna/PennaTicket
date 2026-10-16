package com.example.ticketing.outbox;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String aggregateType;

	@Column(nullable = false)
	private Long aggregateId;

	@Column(nullable = false)
	private String eventType;

	@Column(nullable = false)
	private String payload;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime publishedAt;

	protected OutboxEvent() {
	}

	public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.createdAt = LocalDateTime.now();
	}

	public void markPublished() {
		this.publishedAt = LocalDateTime.now();
	}

	public boolean isPublished() {
		return publishedAt != null;
	}

	public Long getId() {
		return id;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public Long getAggregateId() {
		return aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getPublishedAt() {
		return publishedAt;
	}

}
