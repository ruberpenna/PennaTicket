package com.example.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.example.ticketing.TestMembers;
import com.example.ticketing.member.MemberRepository;
import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.PerformanceRepository;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.performance.SeatStatus;
import com.example.ticketing.reservation.ReservationResult.Reserved;
import com.example.ticketing.reservation.lock.LockStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 실제 PostgreSQL(ticketing_test) + Redis 위에서, 5가지 방식 모두 "한 좌석에 한 명"을 지키는지 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationConcurrencyTest {

	private static final int USERS = 100;

	@Autowired
	ReservationService reservationService;

	@Autowired
	PerformanceRepository performanceRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	MemberRepository memberRepository;

	private List<Long> memberIds;

	@BeforeAll
	void 회원_100명을_만든다() {
		memberIds = IntStream.range(0, USERS).mapToObj(i -> TestMembers.create(memberRepository)).toList();
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(LockStrategy.class)
	void 같은_좌석에_100명이_동시에_예매해도_한_명만_성공한다(LockStrategy strategy) throws Exception {
		// 실제 DB라 데이터가 남는다: 실행할 때마다 새 공연을 만들어 좌석 유니크 제약과 겹치지 않게 한다
		Performance performance = performanceRepository
			.save(new Performance("동시성 테스트 " + strategy, "테스트홀", LocalDateTime.now().plusDays(1)));
		Seat seat = seatRepository.save(new Seat(performance, "Z", "1", SeatGrade.S, 100_000));

		List<Future<ReservationResult>> futures = new ArrayList<>();
		// Java 21 가상 스레드: 스레드 100개를 부담 없이 만든다
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (Long memberId : memberIds) {
				futures.add(executor.submit(() -> reservationService.reserve(seat.getId(), memberId, strategy)));
			}
		}

		long successCount = 0;
		for (Future<ReservationResult> future : futures) {
			if (future.get() instanceof Reserved) { // 예외 없이 모두 Reserved 또는 Rejected여야 한다
				successCount++;
			}
		}
		assertThat(successCount).isEqualTo(1);
		assertThat(seatRepository.findById(seat.getId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.RESERVED);
		assertThat(reservationRepository.countBySeatId(seat.getId())).isEqualTo(1);
	}

}
