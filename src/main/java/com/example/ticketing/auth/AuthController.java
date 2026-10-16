package com.example.ticketing.auth;

import java.net.URI;

import com.example.ticketing.auth.AuthDtos.LoginRequest;
import com.example.ticketing.auth.AuthDtos.MemberResponse;
import com.example.ticketing.auth.AuthDtos.SignupRequest;
import com.example.ticketing.auth.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원/인증")
@RestController
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@Operation(summary = "회원가입")
	@PostMapping("/api/auth/signup")
	ResponseEntity<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
		MemberResponse member = authService.signup(request);
		return ResponseEntity.created(URI.create("/api/members/me")).body(member);
	}

	@Operation(summary = "로그인", description = "액세스 토큰(JWT)을 발급한다. 이후 요청에 Authorization: Bearer {토큰}")
	@PostMapping("/api/auth/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@Operation(summary = "내 정보")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/api/members/me")
	MemberResponse me(@CurrentMemberId Long memberId) {
		return authService.findMember(memberId);
	}

}
