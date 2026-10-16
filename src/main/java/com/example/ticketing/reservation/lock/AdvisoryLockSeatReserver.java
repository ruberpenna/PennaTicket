package com.example.ticketing.reservation.lock;

import com.example.ticketing.reservation.ReservationResult;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ④ PostgreSQL Advisory Lock: pg_advisory_xact_lock(종류, id)
 * 테이블 행이 아니라 "숫자 이름"에 거는 락. 서버가 여러 대여도 같은 DB를 보니까 분산 락으로 동작한다.
 * _xact_ 버전은 트랜잭션이 끝나면 자동으로 풀려서 해제를 깜빡할 일이 없다.
 *
 * 좌석 행이 아직 없는 경우(예: "회원별 1인 1매 제한")처럼 잠글 행이 없을 때도 쓸 수 있는 게 장점.
 */
@Component
class AdvisoryLockSeatReserver implements SeatReserver {

	/** 락 이름 충돌 방지용 "종류" 번호. 좌석 = 1. 다른 기능에서 쓰면 다른 번호를 쓴다. */
	private static final int SEAT_LOCK_NAMESPACE = 1;

	private final EntityManager entityManager;
	private final SeatReservationSupport support;

	AdvisoryLockSeatReserver(EntityManager entityManager, SeatReservationSupport support) {
		this.entityManager = entityManager;
		this.support = support;
	}

	@Override
	public LockStrategy strategy() {
		return LockStrategy.ADVISORY_LOCK;
	}

	@Override
	@Transactional
	public ReservationResult reserve(Long seatId, Long memberId) {
		entityManager.createNativeQuery("select pg_advisory_xact_lock(:namespace, :id)")
			.setParameter("namespace", SEAT_LOCK_NAMESPACE)
			.setParameter("id", Math.toIntExact(seatId))
			.getSingleResult(); // 다른 트랜잭션이 같은 좌석 락을 갖고 있으면 여기서 기다린다
		return support.reserveIfAvailable(support.findSeat(seatId), memberId);
	}

}
