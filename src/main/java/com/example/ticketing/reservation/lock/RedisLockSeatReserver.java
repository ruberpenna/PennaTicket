package com.example.ticketing.reservation.lock;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.example.ticketing.reservation.ReservationResult;
import com.example.ticketing.reservation.ReservationResult.Rejected;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ⑤ Redis 분산 락: SET lock:seat:{id} {토큰} NX PX 5000
 * - NX: 키가 없을 때만 저장 → 먼저 저장한 한 명만 락을 얻는다
 * - PX: 만료 시간 → 서버가 죽어도 락이 영원히 남지 않는다
 * - 해제는 "내 토큰일 때만 삭제"를 Lua 스크립트로 원자적으로 (남의 락을 지우지 않도록)
 *
 * 중요: 락은 트랜잭션 "바깥"에서 잡고, 커밋이 끝난 "뒤"에 푼다.
 * 트랜잭션 안에서 풀면, 커밋 전에 다음 사람이 들어와 아직 AVAILABLE인 좌석을 읽을 수 있다.
 *
 * 락을 기다리지 않는다(try-lock). 누가 이미 잡고 있으면 "다른 고객이 선택 중인 좌석"으로 바로 거절한다.
 * → 처음에는 락이 풀릴 때까지 기다리는 스핀 락으로 만들었는데, 300명이 몰리면 한 명씩 락을 얻어
 *   매번 DB를 확인하느라 8초 넘게 걸렸다 (LockStrategyBenchmarkTest). 좌석은 한 명만 가질 수 있으니
 *   기다려 봐야 결과는 "이미 팔림"이다. 기다림이 의미 있는 경우(재고 여러 개 차감 등)에만 대기 락을 쓴다.
 *
 * 장점: 락 경합을 DB 밖(Redis)에서 끝내서 DB 커넥션을 쓰지 않는다. 단점: Redis라는 운영 대상이 하나 더 생긴다.
 * (실무에서는 이 부분을 Redisson 라이브러리로 쓰는 경우가 많다. 여기서는 원리를 보이려고 직접 구현)
 */
@Component
class RedisLockSeatReserver implements SeatReserver {

	private static final Duration LEASE_TIME = Duration.ofSeconds(5);

	private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
			if redis.call('get', KEYS[1]) == ARGV[1] then
			  return redis.call('del', KEYS[1])
			end
			return 0
			""", Long.class);

	private final StringRedisTemplate redis;
	private final TransactionTemplate transactionTemplate;
	private final SeatReservationSupport support;

	RedisLockSeatReserver(StringRedisTemplate redis, TransactionTemplate transactionTemplate,
			SeatReservationSupport support) {
		this.redis = redis;
		this.transactionTemplate = transactionTemplate;
		this.support = support;
	}

	@Override
	public LockStrategy strategy() {
		return LockStrategy.REDIS_LOCK;
	}

	@Override
	public ReservationResult reserve(Long seatId, Long memberId) {
		String key = "lock:seat:" + seatId;
		String token = UUID.randomUUID().toString();

		if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, LEASE_TIME))) {
			return new Rejected(seatId, "다른 고객이 선택 중인 좌석입니다.");
		}
		try {
			return transactionTemplate
				.execute(status -> support.reserveIfAvailable(support.findSeat(seatId), memberId));
		}
		finally {
			redis.execute(UNLOCK_SCRIPT, List.of(key), token);
		}
	}

}
