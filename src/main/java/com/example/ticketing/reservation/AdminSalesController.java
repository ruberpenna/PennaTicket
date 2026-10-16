package com.example.ticketing.reservation;

import java.util.List;

import com.example.ticketing.reservation.ReservationDtos.GradeSalesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 매출")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/performances")
public class AdminSalesController {

	private final ReservationService reservationService;

	public AdminSalesController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@Operation(summary = "등급별 판매 현황", description = "native SQL(group by)로 집계한다.")
	@GetMapping("/{performanceId}/sales")
	List<GradeSalesResponse> sales(@PathVariable Long performanceId) {
		return reservationService.salesByGrade(performanceId);
	}

}
