package com.example.ticketing.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
	DUPLICATE_SECTION(HttpStatus.BAD_REQUEST, "구역 이름이 중복되었습니다."),
	SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
	SEAT_NOT_AVAILABLE(HttpStatus.CONFLICT, "이미 예매된 좌석입니다."),
	RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매 내역을 찾을 수 없습니다."),
	RESERVATION_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 예매만 결제하거나 취소할 수 있습니다."),
	RESERVATION_NOT_PAYABLE(HttpStatus.CONFLICT, "결제할 수 없는 예매입니다. (결제 대기 상태가 아님)"),
	PAYMENT_DECLINED(HttpStatus.PAYMENT_REQUIRED, "결제가 거절되었습니다."),
	PAYMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제 서버와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요."),
	RESERVATION_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 예매입니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}

}
