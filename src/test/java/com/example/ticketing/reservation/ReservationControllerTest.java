package com.example.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.LocalDateTime;

import com.example.ticketing.TestMembers;
import com.example.ticketing.member.MemberRepository;
import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.PerformanceRepository;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.performance.SeatRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * jwt(): 실제 토큰을 만들지 않고 "이 회원이 로그인했다"는 상태만 흉내 낸다 (spring-security-test).
 * 로그인 흐름 자체는 AuthControllerTest에서 진짜 토큰으로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	PerformanceRepository performanceRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	MemberRepository memberRepository;

	private Long seatId;

	private long memberId;

	private long otherMemberId;

	@BeforeEach
	void setUp() {
		Performance performance = performanceRepository
			.save(new Performance("예매 API 테스트", "테스트홀", LocalDateTime.now().plusDays(7)));
		seatId = seatRepository.save(new Seat(performance, "A", "1", SeatGrade.R, 120_000)).getId();
		memberId = TestMembers.create(memberRepository);
		otherMemberId = TestMembers.create(memberRepository);
	}

	@Test
	void 로그인한_회원으로_예매되고_내_예매_목록에_보인다() {
		assertThat(reserve(loginAs(memberId))).hasStatus(HttpStatus.CREATED);

		assertThat(mvc.get().uri("/api/reservations/me").with(loginAs(memberId)))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.content[0].performanceTitle")
			.isEqualTo("예매 API 테스트");
	}

	@Test
	void 다른_회원의_예매는_취소할_수_없다() throws Exception {
		Integer reservationId = JsonPath.read(reserve(loginAs(memberId)).getResponse().getContentAsString(),
				"$.reservationId");

		assertThat(mvc.delete().uri("/api/reservations/{id}", reservationId).with(loginAs(otherMemberId)))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.delete().uri("/api/reservations/{id}", reservationId).with(loginAs(memberId)))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void 로그인하지_않으면_예매할_수_없다() {
		assertThat(mvc.post()
			.uri("/api/reservations")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"seatId\": " + seatId + " }")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult reserve(JwtRequestPostProcessor login) {
		return mvc.post()
			.uri("/api/reservations")
			.with(login)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"seatId\": " + seatId + " }")
			.exchange();
	}

	private static JwtRequestPostProcessor loginAs(long memberId) {
		return jwt().jwt(token -> token.subject(String.valueOf(memberId)).claim("roles", "USER"));
	}

}
