package com.example.ticketing.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	/**
	 * 아직 안 보낸 이벤트를 오래된 순으로 가져오면서 행을 잠근다.
	 * SKIP LOCKED: 다른 서버의 릴레이가 이미 잠근 행은 건너뛴다 → 서버가 여러 대여도 같은 이벤트를 동시에 보내지 않는다.
	 */
	@Query(value = """
			select * from outbox_event
			 where published_at is null
			 order by id
			 limit :limit
			 for update skip locked
			""", nativeQuery = true)
	List<OutboxEvent> lockUnpublished(int limit);

	List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderById(String aggregateType, Long aggregateId);

	/** 아직 Kafka로 못 보낸 이벤트 수 (부분 인덱스 idx_outbox_unpublished를 탄다) */
	long countByPublishedAtIsNull();

}
