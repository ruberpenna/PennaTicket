package com.example.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.example.ticketing.member.Member;
import com.example.ticketing.member.MemberRepository;
import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.PerformanceRepository;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.reservation.ReservationResult.Reserved;
import com.example.ticketing.reservation.lock.LockStrategy;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 5가지 동시성 제어 방식을 같은 조건에서 돌려 시간과 결과를 비교한다. (k6 대신 Java 가상 스레드로 부하 생성)
 *
 * - 시나리오 A "인기 좌석": 300명이 좌석 1개에 동시에 몰림 → 경합 최대
 * - 시나리오 B "분산 예매": 300명이 각자 다른 좌석 300개를 동시에 예매 → 경합 없음
 *
 * 결과 표는 테스트 출력(build/test-results/test/TEST-...LockStrategyBenchmarkTest.xml)에 찍힌다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LockStrategyBenchmarkTest {

	private static final int USERS = 300;

	@Autowired
	ReservationService reservationService;

	@Autowired
	PerformanceRepository performanceRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	HikariDataSource dataSource;

	private List<Long> memberIds;

	record Result(String scenario, LockStrategy strategy, long millis, int reserved, int rejected,
			Map<String, Integer> errors) {
	}

	@BeforeAll
	void 회원_300명을_만든다() {
		String batch = "bench-" + System.nanoTime();
		memberIds = memberRepository
			.saveAll(IntStream.range(0, USERS)
				.mapToObj(i -> new Member(batch + "-" + i + "@test.local", "{noop}unused", "부하테스트"))
				.toList())
			.stream()
			.map(Member::getId)
			.toList();
	}

	@Test
	void 다섯_가지_방식_비교() throws Exception {
		run("워밍업", LockStrategy.PESSIMISTIC, seats(1, true)); // JIT, 커넥션 풀 예열 (결과 버림)

		List<Result> results = new ArrayList<>();
		for (LockStrategy strategy : LockStrategy.values()) {
			results.add(run("A 인기 좌석 1개", strategy, seats(1, true)));
		}
		for (LockStrategy strategy : LockStrategy.values()) {
			results.add(run("B 각자 다른 좌석", strategy, seats(USERS, false)));
		}

		print(results);

		for (Result r : results) {
			assertThat(r.errors()).as(r.strategy() + " 예외").isEmpty();
			assertThat(r.reserved()).as(r.scenario() + " " + r.strategy() + " 성공 수")
				.isEqualTo(r.scenario().startsWith("A") ? 1 : USERS);
		}
	}

	/** count개 좌석을 만들고, 사용자 i가 노릴 좌석 id 목록을 돌려준다 (same이면 전원 같은 좌석) */
	private List<Long> seats(int count, boolean same) {
		Performance performance = performanceRepository
			.save(new Performance("부하 테스트", "테스트홀", LocalDateTime.now().plusDays(1)));
		List<Long> seatIds = seatRepository
			.saveAll(IntStream.rangeClosed(1, count)
				.mapToObj(n -> new Seat(performance, "A", String.valueOf(n), SeatGrade.R, 100_000))
				.toList())
			.stream()
			.map(Seat::getId)
			.toList();
		return IntStream.range(0, USERS).mapToObj(i -> same ? seatIds.getFirst() : seatIds.get(i)).toList();
	}

	private Result run(String scenario, LockStrategy strategy, List<Long> targetSeatIds) throws InterruptedException {
		AtomicInteger reserved = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		Map<String, Integer> errors = new ConcurrentHashMap<>();
		CountDownLatch ready = new CountDownLatch(USERS);
		CountDownLatch start = new CountDownLatch(1);
		long began;

		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < USERS; i++) {
				Long seatId = targetSeatIds.get(i);
				Long memberId = memberIds.get(i);
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await(); // 300명이 출발선에 모일 때까지 기다렸다가 동시에 출발
						if (reservationService.reserve(seatId, memberId, strategy) instanceof Reserved) {
							reserved.incrementAndGet();
						}
						else {
							rejected.incrementAndGet();
						}
					}
					catch (Exception e) {
						errors.merge(e.getClass().getSimpleName(), 1, Integer::sum);
					}
					return null;
				});
			}
			ready.await();
			began = System.nanoTime();
			start.countDown();
		} // close() = 모든 작업이 끝날 때까지 기다림
		long millis = (System.nanoTime() - began) / 1_000_000;
		return new Result(scenario, strategy, millis, reserved.get(), rejected.get(), new TreeMap<>(errors));
	}

	private void print(List<Result> results) {
		StringBuilder table = new StringBuilder();
		table.append("\n===== 동시성 제어 방식 비교 (동시 사용자 ").append(USERS)
			.append("명, DB 커넥션 풀 ").append(dataSource.getMaximumPoolSize()).append("개) =====\n");
		table.append(String.format("%-18s | %-18s | %8s | %6s | %6s | %s%n", "시나리오", "방식", "시간(ms)", "성공", "거절",
				"예외"));
		for (Result r : results) {
			table.append(String.format("%-18s | %-18s | %8d | %6d | %6d | %s%n", r.scenario(), r.strategy(), r.millis(),
					r.reserved(), r.rejected(), r.errors().isEmpty() ? "-" : r.errors()));
		}
		System.out.println(table);
	}

}
