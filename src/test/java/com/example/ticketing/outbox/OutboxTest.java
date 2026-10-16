package com.example.ticketing.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.ticketing.TestMembers;
import com.example.ticketing.common.BusinessException;
import com.example.ticketing.member.MemberRepository;
import com.example.ticketing.notification.Notification;
import com.example.ticketing.notification.NotificationAwait;
import com.example.ticketing.notification.NotificationRepository;
import com.example.ticketing.performance.Performance;
import com.example.ticketing.performance.PerformanceRepository;
import com.example.ticketing.performance.Seat;
import com.example.ticketing.performance.SeatGrade;
import com.example.ticketing.performance.SeatRepository;
import com.example.ticketing.reservation.ReservationResult.Reserved;
import com.example.ticketing.reservation.ReservationService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Transactional Outbox가 보장하는 것들을 확인한다.
 * 테스트 설정에서는 자동 릴레이가 꺼져 있다 = "릴레이(또는 Kafka 연결)가 멈춘 상황"을 그대로 재현할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxTest {

	@Autowired
	ReservationService reservationService;

	@Autowired
	OutboxRelay outboxRelay;

	@Autowired
	OutboxEventRepository outboxEventRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	PerformanceRepository performanceRepository;

	@Autowired
	SeatRepository seatRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	KafkaTemplate<String, String> kafkaTemplate;

	@Value("${ticketing.kafka.reservation-topic}")
	String topic;

	private Long memberId;

	private Long reservationId;

	@BeforeEach
	void 결제_대기_예매를_하나_만든다() {
		Performance performance = performanceRepository
			.save(new Performance("Outbox 테스트", "테스트홀", LocalDateTime.now().plusDays(5)));
		Long seatId = seatRepository.save(new Seat(performance, "C", "7", SeatGrade.S, 99_000)).getId();
		memberId = TestMembers.create(memberRepository);
		reservationId = ((Reserved) reservationService.reserve(seatId, memberId)).reservationId();
	}

	@Test
	void 예매_확정과_보낼_이벤트가_같은_트랜잭션으로_저장된다() {
		reservationService.confirmPayment(reservationId, "pay_outbox_test");

		List<OutboxEvent> events = outboxEvents();
		assertThat(events).singleElement().satisfies(e -> {
			assertThat(e.getEventType()).isEqualTo("Confirmed");
			assertThat(e.getPayload()).contains("\"performanceTitle\":\"Outbox 테스트\"", "\"amount\":99000");
			assertThat(e.isPublished()).as("아직 Kafka로 안 보냄").isFalse();
		});
	}

	@Test
	void 확정이_실패해서_롤백되면_이벤트도_저장되지_않는다() {
		reservationService.confirmPayment(reservationId, "pay_first");

		// 이미 확정된 예매를 다시 확정 → 예외 → 트랜잭션 롤백
		assertThatThrownBy(() -> reservationService.confirmPayment(reservationId, "pay_second"))
			.isInstanceOf(BusinessException.class);

		assertThat(outboxEvents()).hasSize(1); // 두 번째 이벤트는 남지 않았다
	}

	@Test
	void 릴레이가_멈춘_동안_쌓인_이벤트는_다시_돌면_발행되어_알림이_된다() throws InterruptedException {
		reservationService.confirmPayment(reservationId, "pay_relay_test");

		Thread.sleep(1500); // 릴레이가 꺼져 있으니 아무 일도 일어나지 않아야 한다
		assertThat(notificationRepository.findTop20ByMemberIdOrderByIdDesc(memberId)).isEmpty();
		assertThat(outboxEvents().getFirst().isPublished()).isFalse();

		// 릴레이 재개 (서버 재시작, Kafka 복구 등)
		List<Notification> sent = NotificationAwait.await(notificationRepository, outboxRelay, memberId, 1);

		assertThat(outboxEvents().getFirst().isPublished()).isTrue();
		assertThat(sent.getFirst().getMessage()).contains("[예매 완료] Outbox 테스트 C구역 7번", "99,000원");
	}

	@Test
	void 같은_이벤트가_Kafka로_두_번_와도_알림은_한_번만_저장된다() throws Exception {
		reservationService.confirmPayment(reservationId, "pay_duplicate_test");
		NotificationAwait.await(notificationRepository, outboxRelay, memberId, 1);

		// 릴레이가 Kafka로 보낸 뒤 "보냄" 표시를 하기 전에 죽은 상황 → 같은 이벤트가 한 번 더 나간다
		OutboxEvent event = outboxEvents().getFirst();
		ProducerRecord<String, String> duplicate = new ProducerRecord<>(topic, String.valueOf(reservationId),
				event.getPayload());
		duplicate.headers()
			.add(OutboxRelay.HEADER_EVENT_ID, String.valueOf(event.getId()).getBytes(StandardCharsets.UTF_8))
			.add(OutboxRelay.HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
		kafkaTemplate.send(duplicate).get(5, TimeUnit.SECONDS);

		Thread.sleep(3000); // 소비자가 중복 메시지를 처리할 시간
		assertThat(notificationRepository.findTop20ByMemberIdOrderByIdDesc(memberId)).hasSize(1);
	}

	private List<OutboxEvent> outboxEvents() {
		return outboxEventRepository.findByAggregateTypeAndAggregateIdOrderById("Reservation", reservationId);
	}

}
