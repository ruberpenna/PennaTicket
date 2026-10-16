package com.example.ticketing.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러 파라미터에 붙이면 로그인한 회원 id(JWT의 sub)가 Long으로 들어온다.
 * 예전처럼 세션에서 꺼내거나 요청 파라미터로 받지 않는다.
 *
 * <pre>
 * ResponseEntity&lt;?&gt; reserve(@CurrentMemberId Long memberId, ...)
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
@Parameter(hidden = true) // Swagger 문서에는 노출하지 않음
public @interface CurrentMemberId {
}
