package com.example.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import com.example.ticketing.TestMembers;
import com.example.ticketing.member.MemberRepository;
import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.PerformanceRepository;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.performance.SeatRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * N+1 문제를 눈으로 확인하는 실습. Hibernate Statistics로 실제 실행된 SQL 수를 센다.
 * 테스트 로그에 찍히는 SQL(org.hibernate.SQL)도 같이 보면 차이가 확실히 보인다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationQueryCountTest {

	private static final int RESERVATION_COUNT = 3;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	ReservationService reservationService;

	@Autowired
	PerformanceRepository performanceRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	TransactionTemplate transactionTemplate;

	private Long memberId;

	@BeforeEach
	void 서로_다른_공연_3개를_한_좌석씩_예매해_둔다() {
		memberId = TestMembers.create(memberRepository); // 매번 새 회원이라 이전 실행 데이터와 섞이지 않는다
		for (int i = 1; i <= RESERVATION_COUNT; i++) {
			Performance performance = performanceRepository
				.save(new Performance("N+1 실습 " + i, "테스트홀", LocalDateTime.now().plusDays(i)));
			Seat seat = seatRepository.save(new Seat(performance, "A", "1", SeatGrade.R, 100_000));
			reservationService.reserve(seat.getId(), memberId);
		}
	}

	@Test
	void 지연_로딩으로_연관_객체를_꺼내면_N_플러스_1_쿼리가_나간다() {
		long queries = countQueries(() -> reservationRepository.findByMemberIdOrderByIdDesc(memberId)
			.forEach(r -> r.getSeat().getPerformance().getTitle()));

		// 예매 목록 1번 + 좌석 3번 + 공연 3번
		assertThat(queries).isEqualTo(1 + RESERVATION_COUNT + RESERVATION_COUNT);
	}

	@Test
	void fetch_join이면_한_번에_가져온다() {
		long queries = countQueries(() -> reservationRepository.findMyReservations(memberId, PageRequest.of(0, 10))
			.forEach(r -> r.getSeat().getPerformance().getTitle()));

		// 조인 쿼리 1번. 첫 페이지 결과가 페이지 크기보다 작으면 Spring Data가 count 쿼리도 생략한다.
		assertThat(queries).isEqualTo(1);
	}

	/** 새 트랜잭션(= 비어 있는 영속성 컨텍스트)에서 작업을 실행하고, 그동안 실행된 SQL 수를 돌려준다. */
	private long countQueries(Runnable work) {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		transactionTemplate.executeWithoutResult(status -> {
			statistics.clear();
			work.run();
		});
		return statistics.getPrepareStatementCount();
	}

}
