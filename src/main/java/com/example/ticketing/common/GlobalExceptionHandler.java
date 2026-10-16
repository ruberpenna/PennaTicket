package com.example.ticketing.common;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 예전처럼 직접 만든 에러 포맷 대신 RFC 9457 ProblemDetail로 응답한다.
 * 검증 실패(@Valid) 같은 스프링 기본 예외는 상위 클래스가 ProblemDetail로 처리해 준다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException e) {
		ErrorCode code = e.errorCode();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), e.getMessage());
		problem.setTitle(code.name());
		return problem;
	}

}
