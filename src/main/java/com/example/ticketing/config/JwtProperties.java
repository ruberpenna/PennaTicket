package com.example.ticketing.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml의 ticketing.jwt.* 를 record로 바인딩한다 (예전 @Value 여러 개 대신).
 *
 * @param secret HS256 서명 키. 32바이트(256비트) 이상. 비어 있으면 기동 때마다 임의 키를 만든다.
 * @param expiry 액세스 토큰 유효 기간
 * @param issuer 토큰 발급자(iss) 값. 검증할 때도 같은 값인지 확인한다.
 */
@ConfigurationProperties("ticketing.jwt")
public record JwtProperties(String secret, @DefaultValue("1h") Duration expiry,
		@DefaultValue("ticketing") String issuer) {
}
