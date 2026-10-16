package com.example.ticketing.reservation.lock;

import com.example.ticketing.reservation.ReservationResult;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ② 낙관적 락: Seat의 @Version
 * 잠그지 않고 읽어서 바꾼 뒤, 커밋할 때 "UPDATE ... WHERE version = 읽은 값"으로 저장한다.
 * 그사이 누가 먼저 바꿨으면 0건 수정 → 예외 → 이미 예매된 좌석으로 처리한다.
 *
 * 예외가 커밋 시점에 나기 때문에 @Transactional 메서드 안에서는 잡을 수 없다.
 * 그래서 TransactionTemplate으로 트랜잭션을 직접 열고, 바깥에서 예외를 잡는다.
 *
 * 좌석 예매에는 재시도가 의미 없다 (다시 읽어도 이미 RESERVED). 재고 차감처럼 "다시 하면 될 수도 있는" 경우에만 재시도한다.
 */
@Component
class OptimisticSeatReserver implements SeatReserver {

	private final TransactionTemplate transactionTemplate;
	private final SeatReservationSupport support;

	OptimisticSeatReserver(TransactionTemplate transactionTemplate, SeatReservationSupport support) {
		this.transactionTemplate = transactionTemplate;
		this.support = support;
	}

	@Override
	public LockStrategy strategy() {
		return LockStrategy.OPTIMISTIC;
	}

	@Override
	public ReservationResult reserve(Long seatId, Long memberId) {
		try {
			return transactionTemplate
				.execute(status -> support.reserveIfAvailable(support.findSeat(seatId), memberId));
		}
		catch (ObjectOptimisticLockingFailureException e) {
			return SeatReservationSupport.rejected(seatId); // 다른 사람이 먼저 커밋함
		}
	}

}
