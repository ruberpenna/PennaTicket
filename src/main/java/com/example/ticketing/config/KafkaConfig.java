package com.example.ticketing.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 앱이 뜰 때 토픽이 없으면 만든다 (KafkaAdmin이 NewTopic 빈을 보고 생성) */
@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

	@Bean
	NewTopic reservationEventsTopic(@Value("${ticketing.kafka.reservation-topic}") String topic) {
		return TopicBuilder.name(topic)
			.partitions(3) // 소비자 최대 3개가 나눠서 병렬 처리
			.replicas(1) // Kafka 서버가 1대라 복제본 1개 (운영은 보통 3)
			.build();
	}

}
