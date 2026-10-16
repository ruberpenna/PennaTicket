package com.example.ticketing.reservation;

import java.net.URI;

import com.example.ticketing.auth.CurrentMemberId;
import com.example.ticketing.common.PageResponse;
import com.example.ticketing.reservation.ReservationDtos.MyReservationResponse;
import com.example.ticketing.reservation.ReservationDtos.PaymentRequest;
import com.example.ticketing.reservation.ReservationDtos.PaymentResponse;
import com.example.ticketing.reservation.ReservationResult.Rejected;
import com.example.ticketing.reservation.ReservationResult.Reserved;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "예매")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

	private final ReservationService reservationService;
	private final ReservationPaymentService reservationPaymentService;

	public ReservationController(ReservationService reservationService,
			ReservationPaymentService reservationPaymentService) {
		this.reservationService = reservationService;
		this.reservationPaymentService = reservationPaymentService;
	}

	public record ReserveRequest(@NotNull Long seatId) {
	}

	@Operation(summary = "좌석 예매", description = "좌석을 잡아 두고 PENDING 상태로 만든다. 결제해야 CONFIRMED가 된다.")
	@PostMapping
	ResponseEntity<?> reserve(@CurrentMemberId Long memberId, @Valid @RequestBody ReserveRequest request) {
		// switch 패턴 매칭 + record 분해: sealed라서 default가 필요 없다
		return switch (reservationService.reserve(request.seatId(), memberId)) {
			case Reserved reserved -> ResponseEntity
				.created(URI.create("/api/reservations/" + reserved.reservationId()))
				.body(reserved);
			case Rejected(Long seatId, String reason) -> ResponseEntity
				.of(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "좌석 " + seatId + ": " + reason))
				.build();
		};
	}

	@Operation(summary = "내 예매 목록", description = "예매·좌석·공연을 fetch join 한 번으로 조회한다.")
	@GetMapping("/me")
	PageResponse<MyReservationResponse> myReservations(@CurrentMemberId Long memberId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
		return reservationService.findMyReservations(memberId, page, size);
	}

	@Operation(summary = "결제", description = """
			PENDING 예매를 결제한다. paymentToken(모의 PG):
			tok_success(승인), tok_declined(카드 거절), tok_flaky(첫 시도 503 → 재시도로 성공), tok_timeout(응답 지연 → 실패)
			""")
	@PostMapping("/{reservationId}/payment")
	PaymentResponse pay(@CurrentMemberId Long memberId, @PathVariable Long reservationId,
			@Valid @RequestBody PaymentRequest request) {
		return reservationPaymentService.pay(reservationId, memberId, request.paymentToken());
	}

	@Operation(summary = "예매 취소", description = "본인 예매만 취소할 수 있다. 결제된 예매는 PG 환불 후 취소된다.")
	@DeleteMapping("/{reservationId}")
	ResponseEntity<Void> cancel(@CurrentMemberId Long memberId, @PathVariable Long reservationId) {
		reservationPaymentService.cancel(reservationId, memberId);
		return ResponseEntity.noContent().build();
	}

}
