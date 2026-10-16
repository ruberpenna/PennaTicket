package com.example.ticketing.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

/**
 * 직접 JWT 필터를 만들지 않고 Spring Security의 OAuth2 Resource Server 기능을 쓴다.
 * - 발급: JwtEncoder (AuthService)
 * - 검증: JwtDecoder → Authorization: Bearer 토큰을 자동으로 검사 (SecurityConfig의 oauth2ResourceServer)
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	private static final int MIN_SECRET_BYTES = 32;

	/** 토큰의 roles 클레임 → ROLE_ 권한. hasRole("ADMIN")이 이 값을 본다. */
	public static final String ROLES_CLAIM = "roles";

	@Bean
	SecretKey jwtSecretKey(JwtProperties properties) {
		byte[] secret;
		if (StringUtils.hasText(properties.secret())) {
			secret = properties.secret().getBytes(StandardCharsets.UTF_8);
			if (secret.length < MIN_SECRET_BYTES) {
				throw new IllegalStateException("ticketing.jwt.secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
			}
		}
		else {
			secret = new byte[MIN_SECRET_BYTES];
			new SecureRandom().nextBytes(secret);
			log.warn("JWT_SECRET이 설정되지 않아 임의 키를 사용합니다. 재시작하면 이전 토큰은 모두 무효가 됩니다.");
		}
		return new SecretKeySpec(secret, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// 서명 + 만료(exp) + 발급자(iss)까지 검증
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	/** {bcrypt}$2a$... 처럼 알고리즘 이름을 앞에 붙여 저장한다. 나중에 알고리즘을 바꿔도 기존 해시를 읽을 수 있다. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
