package com.example.ticketing.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.example.ticketing.TestMembers;
import com.example.ticketing.common.BusinessException;
import com.example.ticketing.common.ErrorCode;
import com.example.ticketing.member.MemberRepository;
import com.example.ticketing.mockpg.MockPgController;
import com.example.ticketing.notification.Notification;
import com.example.ticketing.notification.NotificationAwait;
import com.example.ticketing.notification.NotificationRepository;
import com.example.ticketing.notification.NotificationType;
import com.example.ticketing.outbox.OutboxEventRepository;
import com.example.ticketing.outbox.OutboxRelay;
import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.PerformanceRepository;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.performance.SeatStatus;
import com.example.ticketing.reservation.ReservationDtos.PaymentResponse;
import com.example.ticketing.reservation.ReservationResult.Reserved;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 실제 HTTP로 모의 PG를 호출한다. 그래서 진짜 서버(톰캣)를 띄운다.
 * base-url이 ${server.port}를 쓰므로, 빈 포트를 미리 골라 server.port로 지정한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
class ReservationPaymentTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			int port = socket.getLocalPort();
			registry.add("server.port", () -> port);
		}
		// 타임아웃 시나리오를 빨리 끝내기 위해 짧게
		registry.add("spring.http.serviceclient.payment.read-timeout", () -> "500ms");
		registry.add("ticketing.mock-pg.slow-response-delay", () -> "1s");
		registry.add("ticketing.notification.simulated-delay", () -> NOTIFICATION_DELAY.toMillis() + "ms");
	}

	/** 알림 리스너가 일부러 이만큼 느리게 동작한다 */
	private static final Duration NOTIFICATION_DELAY = Duration.ofMillis(800);

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	OutboxRelay outboxRelay;

	@Autowired
	OutboxEventRepository outboxEventRepository;

	@Autowired
	ReservationPaymentService paymentService;

	@Autowired
	ReservationService reservationService;

	@Autowired
	ReservationRepository reservationRepository;

	@Autowired
	PerformanceRepository performanceRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	MockPgController mockPg;

	private Long memberId;

	private Long seatId;

	private Long reservationId;

	@BeforeEach
	void 좌석을_예매해서_결제_대기_상태로_만든다() {
		Performance performance = performanceRepository
			.save(new Performance("결제 테스트", "테스트홀", LocalDateTime.now().plusDays(3)));
		seatId = seatRepository.save(new Seat(performance, "A", "1", SeatGrade.VIP, 150_000)).getId();
		memberId = TestMembers.create(memberRepository);
		reservationId = ((Reserved) reservationService.reserve(seatId, memberId)).reservationId();
	}

	@Test
	void 예매_직후에는_결제_대기_상태다() {
		assertThat(status()).isEqualTo(ReservationStatus.PENDING);
	}

	@Test
	void 결제가_승인되면_예매가_확정된다() {
		PaymentResponse response = paymentService.pay(reservationId, memberId, "tok_success");

		assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(response.paymentKey()).startsWith("pay_");
		assertThat(response.amount()).isEqualTo(150_000);
		assertThat(attempts()).isEqualTo(1);
	}

	@Test
	void 카드가_거절되면_재시도하지_않고_결제_대기로_남는다() {
		assertThatThrownBy(() -> paymentService.pay(reservationId, memberId, "tok_declined"))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.errorCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED);
				assertThat(e.getMessage()).contains("카드 한도가 초과되었습니다.");
			});

		assertThat(attempts()).isEqualTo(1); // 4xx는 다시 보내도 결과가 같다
		assertThat(status()).isEqualTo(ReservationStatus.PENDING);
	}

	@Test
	void PG가_일시적으로_503이면_재시도해서_성공한다() {
		PaymentResponse response = paymentService.pay(reservationId, memberId, "tok_flaky");

		assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(attempts()).isEqualTo(2); // 첫 시도 503 → 재시도 1번에 성공
	}

	@Test
	void PG가_계속_응답하지_않으면_재시도_후_실패한다() {
		assertThatThrownBy(() -> paymentService.pay(reservationId, memberId, "tok_timeout"))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.PAYMENT_UNAVAILABLE));

		assertThat(attempts()).isEqualTo(3); // 최초 1번 + 재시도 2번
		assertThat(status()).isEqualTo(ReservationStatus.PENDING);
	}

	@Test
	void 같은_예매를_두_번_결제할_수_없다() {
		paymentService.pay(reservationId, memberId, "tok_success");

		assertThatThrownBy(() -> paymentService.pay(reservationId, memberId, "tok_success"))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESERVATION_NOT_PAYABLE));
	}

	@Test
	void 다른_회원의_예매는_결제할_수_없다() {
		Long otherMemberId = TestMembers.create(memberRepository);

		assertThatThrownBy(() -> paymentService.pay(reservationId, otherMemberId, "tok_success"))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESERVATION_FORBIDDEN));
	}

	@Test
	void 결제된_예매를_취소하면_환불되고_좌석이_풀린다() {
		paymentService.pay(reservationId, memberId, "tok_success");

		paymentService.cancel(reservationId, memberId);

		assertThat(status()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
	}

	@Test
	void 결제_응답은_알림을_기다리지_않고_알림은_커밋_후_따로_저장된다() throws InterruptedException {
		long started = System.nanoTime();
		paymentService.pay(reservationId, memberId, "tok_success");
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

		assertThat(elapsed).as("결제 응답 시간").isLessThan(NOTIFICATION_DELAY); // 알림(0.8초)을 기다리지 않음
		assertThat(notifications()).as("응답 직후에는 아직 알림 처리 중").isEmpty();

		List<Notification> sent = awaitNotifications(1);
		assertThat(sent.getFirst().getType()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
		assertThat(sent.getFirst().getMessage()).contains("결제 테스트", "A구역 1번", "150,000원");
	}

	@Test
	void 결제가_거절되면_예매_완료_알림이_가지_않는다() throws InterruptedException {
		assertThatThrownBy(() -> paymentService.pay(reservationId, memberId, "tok_declined"))
			.isInstanceOf(BusinessException.class);

		outboxRelay.publishPending();
		Thread.sleep(NOTIFICATION_DELAY.plusMillis(1000));
		assertThat(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderById("Reservation", reservationId))
			.as("확정되지 않았으니 보낼 이벤트도 없다")
			.isEmpty();
		assertThat(notifications()).isEmpty();
	}

	@Test
	void 결제된_예매를_취소하면_환불_안내_알림이_간다() throws InterruptedException {
		paymentService.pay(reservationId, memberId, "tok_success");
		paymentService.cancel(reservationId, memberId);

		List<Notification> sent = awaitNotifications(2);
		assertThat(sent).extracting(Notification::getType)
			.containsExactlyInAnyOrder(NotificationType.RESERVATION_CONFIRMED, NotificationType.RESERVATION_CANCELLED);
		assertThat(sent).filteredOn(n -> n.getType() == NotificationType.RESERVATION_CANCELLED)
			.singleElement()
			.extracting(Notification::getMessage)
			.asString()
			.contains("환불");
	}

	private List<Notification> notifications() {
		return notificationRepository.findTop20ByMemberIdOrderByIdDesc(memberId);
	}

	private List<Notification> awaitNotifications(int expected) throws InterruptedException {
		return NotificationAwait.await(notificationRepository, outboxRelay, memberId, expected);
	}

	private ReservationStatus status() {
		return reservationRepository.findById(reservationId).orElseThrow().getStatus();
	}

	private int attempts() {
		return mockPg.attemptCount("R-" + reservationId);
	}

}
