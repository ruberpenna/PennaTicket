package com.example.ticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 6+ 방식: WebSecurityConfigurerAdapter 상속 대신 SecurityFilterChain 빈 + 람다 DSL.
 * 세션 없이(STATELESS) 매 요청의 Authorization: Bearer 토큰으로 인증한다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
			throws Exception {
		return http
			.csrf(csrf -> csrf.disable()) // 쿠키 세션을 안 쓰므로 CSRF 대상이 아니다
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/info").permitAll()
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

}
