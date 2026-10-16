package com.example.ticketing.reservation;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 예전 DAO + mapper.xml 역할. 구현 클래스는 Spring Data JPA가 실행 시점에 만들어 준다.
 * 기본 제공: findById, findAll, save, deleteById, count ...
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	/**
	 * 메서드 이름 쿼리: select ... from reservation where member_id = ? order by id desc
	 * 연관된 좌석/공연은 지연 로딩이라, 꺼내 쓰면 예매 건수만큼 쿼리가 더 나간다(N+1). 비교 실습용.
	 */
	List<Reservation> findByMemberIdOrderByIdDesc(Long memberId);

	/** select count(*) from reservation where seat_id = ? (연관 엔티티의 id로도 메서드 이름 쿼리가 된다) */
	long countBySeatId(Long seatId);

	/**
	 * JPQL + join fetch: 예매, 좌석, 공연을 조인 한 번으로 가져온다(N+1 해결).
	 * fetch join이 들어간 쿼리는 count 쿼리를 자동으로 못 만들어서 countQuery를 따로 적는다.
	 */
	@Query(value = """
			select r from Reservation r
			  join fetch r.seat s
			  join fetch s.performance p
			 where r.memberId = :memberId
			 order by r.reservedAt desc, r.id desc
			""",
			countQuery = "select count(r) from Reservation r where r.memberId = :memberId")
	Page<Reservation> findMyReservations(Long memberId, Pageable pageable);

	/**
	 * native SQL: 통계처럼 SQL을 직접 쓰는 게 나은 경우. 결과는 인터페이스 프로젝션으로 받는다
	 * (getter 이름 = 컬럼 별칭).
	 */
	@Query(value = """
			select s.grade      as grade,
			       count(*)     as soldCount,
			       sum(s.price) as amount
			  from reservation r
			  join seat s on s.id = r.seat_id
			 where s.performance_id = :performanceId
			   and r.status = 'CONFIRMED'
			 group by s.grade
			 order by amount desc
			""", nativeQuery = true)
	List<GradeSales> salesByGrade(Long performanceId);

	interface GradeSales {

		String getGrade();

		long getSoldCount();

		long getAmount();

	}

}
