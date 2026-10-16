package com.example.ticketing.performance;

import java.net.URI;

import com.example.ticketing.performance.PerformanceDtos.CreatePerformanceRequest;
import com.example.ticketing.performance.PerformanceDtos.PerformanceResponse;
import com.example.ticketing.performance.PerformanceDtos.UpdatePerformanceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용. 권한 검사는 SecurityConfig의 /api/admin/** 규칙에서 한다. */
@Tag(name = "관리자 - 공연")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/performances")
public class AdminPerformanceController {

	private final PerformanceService performanceService;

	public AdminPerformanceController(PerformanceService performanceService) {
		this.performanceService = performanceService;
	}

	@Operation(summary = "공연 등록", description = "구역별 등급/가격/좌석 수를 받아 좌석까지 함께 만든다.")
	@PostMapping
	ResponseEntity<PerformanceResponse> create(@Valid @RequestBody CreatePerformanceRequest request) {
		PerformanceResponse created = performanceService.create(request);
		return ResponseEntity.created(URI.create("/api/performances/" + created.id())).body(created);
	}

	@Operation(summary = "공연 정보 수정")
	@PutMapping("/{performanceId}")
	PerformanceResponse update(@PathVariable Long performanceId,
			@Valid @RequestBody UpdatePerformanceRequest request) {
		return performanceService.update(performanceId, request);
	}

}
