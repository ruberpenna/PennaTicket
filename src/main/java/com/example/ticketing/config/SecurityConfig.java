package com.example.ticketing.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Spring Security 6+ 방식: WebSecurityConfigurerAdapter 상속 대신 SecurityFilterChain 빈 + 람다 DSL.
 * 세션 없이(STATELESS) 매 요청의 Authorization: Bearer 토큰으로 인증한다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
			@Value("${ticketing.monitoring.allowed-ips}") List<String> monitoringIps) throws Exception {
		return http
			.csrf(csrf -> csrf.disable()) // 쿠키 세션을 안 쓰므로 CSRF 대상이 아니다
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/info").permitAll()
				// Prometheus는 토큰 없이 주기적으로 긁어 간다 → 로그인 대신 "허용된 IP에서 온 요청만" 통과
				.requestMatchers("/actuator/prometheus").access(fromIps(monitoringIps))
				.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
				.requestMatchers("/mock-pg/**").permitAll() // 개발용 가짜 결제사 (외부 서버 역할)
				.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/performances/**").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			// Bearer 토큰 → JwtDecoder로 검증 → JwtAuthenticationConverter로 권한(ROLE_xxx) 변환
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
			.build();
	}

	/** 요청한 쪽 IP가 허용 대역(CIDR, 예: 192.168.0.0/24) 안에 있으면 통과 */
	private static AuthorizationManager<RequestAuthorizationContext> fromIps(List<String> cidrs) {
		List<IpAddressMatcher> matchers = cidrs.stream().map(String::trim).map(IpAddressMatcher::new).toList();
		return (authentication, context) -> new AuthorizationDecision(
				matchers.stream().anyMatch(matcher -> matcher.matches(context.getRequest())));
	}

}
