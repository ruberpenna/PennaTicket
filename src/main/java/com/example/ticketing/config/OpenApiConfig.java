package com.example.ticketing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: http://localhost:8090/swagger-ui.html (로그인 API로 받은 토큰을 우측 상단 Authorize에 입력) */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI ticketingOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Ticketing API").description("공연 좌석 예매 시스템").version("v1"))
			.components(new Components().addSecuritySchemes("bearerAuth",
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

}
