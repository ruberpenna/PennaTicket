package com.example.ticketing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async 활성화. 실행기는 Spring Boot가 만든 applicationTaskExecutor를 쓰는데,
 * spring.threads.virtual.enabled=true라서 작업마다 가상 스레드가 하나씩 뜬다 (스레드 풀 크기 고민 X).
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

	private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

	/** void @Async 메서드의 예외는 호출한 쪽으로 돌아오지 않는다. 그냥 두면 조용히 사라지므로 로그라도 남긴다. */
	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return (ex, method, params) -> log.error("비동기 작업 실패: {}.{} args={}", method.getDeclaringClass().getSimpleName(),
				method.getName(), params, ex);
	}

}
