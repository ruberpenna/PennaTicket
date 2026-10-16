package com.example.ticketing.performance;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, Long> {

	List<Seat> findByPerformanceIdOrderBySectionAscIdAsc(Long performanceId);

	/** SELECT ... FOR UPDATE: 같은 좌석을 동시에 잡으려는 트랜잭션은 줄을 서게 된다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Seat s where s.id = :id")
	Optional<Seat> findByIdForUpdate(Long id);

	/**
	 * 조건부 UPDATE: "비어 있으면 예매됨으로 바꿔라"를 DB가 한 문장으로 원자적으로 처리한다.
	 * 바뀐 행 수가 1이면 내가 잡은 것, 0이면 이미 누가 잡은 것. 미리 SELECT 할 필요가 없다.
	 * (version도 같이 올려서 낙관적 락을 쓰는 다른 트랜잭션과도 충돌을 감지하게 한다)
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Seat s
			   set s.status = com.example.ticketing.performance.SeatStatus.RESERVED, s.version = s.version + 1
			 where s.id = :id
			   and s.status = com.example.ticketing.performance.SeatStatus.AVAILABLE
			""")
	int reserveIfAvailable(Long id);

}
