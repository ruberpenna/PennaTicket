package com.example.ticketing.performance;

import java.util.List;

import com.example.ticketing.performance.PerformanceDtos.PerformanceResponse;
import com.example.ticketing.performance.PerformanceDtos.SeatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공연 조회")
@RestController
@RequestMapping("/api/performances")
public class PerformanceController {

	private final PerformanceService performanceService;

	public PerformanceController(PerformanceService performanceService) {
		this.performanceService = performanceService;
	}

	@Operation(summary = "공연 목록")
	@GetMapping
	List<PerformanceResponse> list() {
		return performanceService.findAll();
	}

	@Operation(summary = "공연 상세")
	@GetMapping("/{performanceId}")
	PerformanceResponse detail(@PathVariable Long performanceId) {
		return performanceService.findById(performanceId);
	}

	@Operation(summary = "공연 좌석 목록")
	@GetMapping("/{performanceId}/seats")
	List<SeatResponse> seats(@PathVariable Long performanceId) {
		return performanceService.findSeats(performanceId);
	}

}
