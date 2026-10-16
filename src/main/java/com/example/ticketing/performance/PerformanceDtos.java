package com.example.ticketing.performance;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 예전의 getter/setter DTO 클래스 대신 record 하나로 끝낸다. */
public final class PerformanceDtos {

	private PerformanceDtos() {
	}

	public record PerformanceResponse(Long id, String title, String venue, LocalDateTime startAt) {

		static PerformanceResponse from(Performance p) {
			return new PerformanceResponse(p.getId(), p.getTitle(), p.getVenue(), p.getStartAt());
		}

	}

	/** 공연 등록: 구역별로 등급, 가격, 좌석 수를 받아 좌석을 한 번에 만든다. */
	public record CreatePerformanceRequest(
			@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 200) String venue,
			@NotNull @Future LocalDateTime startAt,
			@NotEmpty List<@Valid SectionRequest> sections) {
	}

	public record SectionRequest(
			@NotBlank @Size(max = 20) String section,
			@NotNull SeatGrade grade,
			@PositiveOrZero int price,
			@Min(1) @Max(500) int seatCount) {
	}

	public record UpdatePerformanceRequest(
			@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 200) String venue,
			@NotNull @Future LocalDateTime startAt) {
	}

	public record SeatResponse(Long id, String section, String seatNumber, SeatGrade grade, int price,
			SeatStatus status) {

		static SeatResponse from(Seat s) {
			return new SeatResponse(s.getId(), s.getSection(), s.getSeatNumber(), s.getGrade(), s.getPrice(),
					s.getStatus());
		}

	}

}
