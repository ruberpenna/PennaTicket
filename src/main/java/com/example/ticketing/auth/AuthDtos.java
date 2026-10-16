package com.example.ticketing.auth;

import com.example.ticketing.member.Member;
import com.example.ticketing.member.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record SignupRequest(
			@NotBlank @Email @Size(max = 100) String email,
			@NotBlank @Size(min = 8, max = 64) String password,
			@NotBlank @Size(max = 50) String name) {
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

		static TokenResponse bearer(String token, long expiresInSeconds) {
			return new TokenResponse(token, "Bearer", expiresInSeconds);
		}

	}

	public record MemberResponse(Long id, String email, String name, MemberRole role) {

		static MemberResponse from(Member m) {
			return new MemberResponse(m.getId(), m.getEmail(), m.getName(), m.getRole());
		}

	}

}
