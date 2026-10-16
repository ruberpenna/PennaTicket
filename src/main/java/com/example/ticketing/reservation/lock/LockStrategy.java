package com.example.ticketing.reservation.lock;

/** 좌석 동시 예매를 막는 방법. application.yml의 ticketing.reservation.lock-strategy로 고른다. */
public enum LockStrategy {

	/** SELECT ... FOR UPDATE: 좌석 행을 잠그고 순서대로 처리 */
	PESSIMISTIC,

	/** @Version: 잠그지 않고 진행, 커밋 때 누가 먼저 바꿨으면 실패 */
	OPTIMISTIC,

	/** UPDATE ... WHERE status = 'AVAILABLE': 조회 없이 한 문장으로 선점 */
	CONDITIONAL_UPDATE,

	/** PostgreSQL pg_advisory_xact_lock: DB가 제공하는 이름 붙은 락 (DB 기반 분산 락) */
	ADVISORY_LOCK,

	/** Redis SET NX PX: 트랜잭션 바깥에서 거는 분산 락 */
	REDIS_LOCK

}
