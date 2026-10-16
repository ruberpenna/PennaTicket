package com.example.ticketing.common;

public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.message());
	}

	/** 기본 메시지 대신 구체적인 사유를 줄 때 (예: PG가 알려준 거절 사유) */
	public BusinessException(ErrorCode errorCode, String detail) {
		super(detail);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

}
