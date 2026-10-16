package com.example.ticketing.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.example.ticketing.common.BusinessException;
import org.junit.jupiter.api.Test;

class SeatTest {

	private final Performance performance = new Performance("테스트 공연", "테스트홀", LocalDateTime.now().plusDays(1));

	@Test
	void 예매하면_좌석이_RESERVED가_된다() {
		Seat seat = new Seat(performance, "A", "1", SeatGrade.VIP, 170_000);

		seat.reserve();

		assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
	}

	@Test
	void 이미_예매된_좌석은_다시_예매할_수_없다() {
		Seat seat = new Seat(performance, "A", "1", SeatGrade.VIP, 170_000);
		seat.reserve();

		assertThatThrownBy(seat::reserve).isInstanceOf(BusinessException.class);
	}

}
