package com.example.ticketing.notification;

import java.time.Duration;
import java.util.List;

import com.example.ticketing.outbox.OutboxRelay;

/**
 * 알림은 Outbox → Kafka → 소비자를 거쳐 비동기로 저장된다. 테스트에서는 자동 릴레이를 꺼 두었으므로
 * 기다리는 동안 릴레이를 직접 돌려 주면서, 알림이 원하는 개수만큼 쌓일 때까지 조금씩 확인한다.
 * (Awaitility 같은 라이브러리가 하는 일을 간단히 직접 구현)
 */
public final class NotificationAwait {

	private static final Duration TIMEOUT = Duration.ofSeconds(20); // 소비자 그룹이 처음 붙는 데 몇 초 걸릴 수 있다

	private NotificationAwait() {
	}

	public static List<Notification> await(NotificationRepository repository, OutboxRelay relay, Long memberId,
			int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (System.nanoTime() < deadline) {
			relay.publishPending();
			List<Notification> found = repository.findTop20ByMemberIdOrderByIdDesc(memberId);
			if (found.size() >= expected) {
				return found;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("알림 " + expected + "개가 " + TIMEOUT.toSeconds() + "초 안에 저장되지 않음: "
				+ repository.findTop20ByMemberIdOrderByIdDesc(memberId).size() + "개");
	}

}
