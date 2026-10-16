package com.example.ticketing.auth;

import java.time.Instant;
import java.util.List;

import com.example.ticketing.auth.AuthDtos.LoginRequest;
import com.example.ticketing.auth.AuthDtos.MemberResponse;
import com.example.ticketing.auth.AuthDtos.SignupRequest;
import com.example.ticketing.auth.AuthDtos.TokenResponse;
import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.config.JwtConfig;
import com.example.ticketing.config.JwtProperties;
import com.example.ticketing.member.Member;
import com.example.ticketing.member.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;

	public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			JwtProperties jwtProperties) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public MemberResponse signup(SignupRequest request) {
		if (memberRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		Member member = new Member(request.email(), passwordEncoder.encode(request.password()), request.name());
		return MemberResponse.from(memberRepository.save(member));
	}

	public TokenResponse login(LoginRequest request) {
		// 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 에러로 돌려준다 (가입 여부를 노출하지 않음)
		Member member = memberRepository.findByEmail(request.email())
			.filter(m -> passwordEncoder.matches(request.password(), m.getPassword()))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		return TokenResponse.bearer(issueToken(member), jwtProperties.expiry().toSeconds());
	}

	public MemberResponse findMember(Long memberId) {
		return memberRepository.findById(memberId)
			.map(MemberResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
	}

	private String issueToken(Member member) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(jwtProperties.issuer())
			.issuedAt(now)
			.expiresAt(now.plus(jwtProperties.expiry()))
			.subject(String.valueOf(member.getId())) // sub = 회원 id → @CurrentMemberId 로 꺼낸다
			.claim("email", member.getEmail())
			.claim(JwtConfig.ROLES_CLAIM, List.of(member.getRole().name()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
