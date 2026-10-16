package com.example.ticketing.payment;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * - @ImportHttpServices: PaymentClient 인터페이스의 구현(프록시)을 "payment" 그룹으로 빈 등록한다.
 *   Spring Boot가 spring.http.serviceclient.payment.* 설정(base-url, 타임아웃)을 RestClient에 적용해 준다.
 * - @EnableResilientMethods: Spring Framework 7에 들어온 @Retryable/@ConcurrencyLimit 활성화
 *   (예전엔 spring-retry나 Resilience4j를 따로 붙였다)
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "payment", types = PaymentClient.class)
@EnableResilientMethods
public class PaymentConfig {

}
