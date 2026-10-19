package com.example.ticketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import com.example.ticketing.member.MemberRepository;
import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.PerformanceRepository;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.reservation.ReservationService;
import com.example.ticketing.reservation.lock.LockStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * /actuator/prometheus 가 비즈니스 지표를 내보내는지, 허용된 IP에서만 열리는지 확인한다.
 * @AutoConfigureMetrics: 테스트에서는 기본적으로 Prometheus 같은 지표 내보내기가 꺼져 있어서 켠다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
class MonitoringTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	ReservationService reservationService;

	@Autowired
	PerformanceRepository performanceRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	MemberRepository memberRepository;

	@Test
	void 예매하면_전략별_예매_지표가_Prometheus_형식으로_나온다() throws Exception {
		Performance performance = performanceRepository
			.save(new Performance("지표 테스트", "테스트홀", LocalDateTime.now().plusDays(1)));
		Long seatId = seatRepository.save(new Seat(performance, "M", "1", SeatGrade.A, 50_000)).getId();
		reservationService.reserve(seatId, TestMembers.create(memberRepository), LockStrategy.CONDITIONAL_UPDATE);
		reservationService.reserve(seatId, TestMembers.create(memberRepository), LockStrategy.CONDITIONAL_UPDATE);

		String body = scrape("127.0.0.1");

		assertThat(body)
			.contains("ticketing_reservations_total{application=\"ticketing\",result=\"reserved\",strategy=\"CONDITIONAL_UPDATE\"}")
			.contains("ticketing_reservations_total{application=\"ticketing\",result=\"rejected\",strategy=\"CONDITIONAL_UPDATE\"}")
			.contains("ticketing_reservation_duration_seconds_bucket") // 히스토그램 → p95 계산용
			.contains("ticketing_outbox_pending") // Outbox 적체 게이지
			.contains("hikaricp_connections_active") // DB 커넥션 풀 (자동)
			.contains("jvm_threads_live_threads"); // JVM (자동)
	}

	@Test
	void 허용되지_않은_IP에서는_지표를_볼_수_없다() {
		// 로그인하지 않은 요청이 거부되면 Spring Security는 403이 아니라 401(인증 필요)로 답한다
		assertThat(mvc.get().uri("/actuator/prometheus").with(request -> {
			request.setRemoteAddr("203.0.113.10"); // 외부 IP
			return request;
		})).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private String scrape(String remoteAddr) throws Exception {
		var result = mvc.get().uri("/actuator/prometheus").with(request -> {
			request.setRemoteAddr(remoteAddr);
			return request;
		}).exchange();
		assertThat(result).hasStatusOk();
		return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
	}

}
