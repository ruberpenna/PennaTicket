# PennaTicket - 공연 좌석 예매 시스템

레거시 Spring(3~4) 경험을 바탕으로 최신 자바/스프링 스택을 익히기 위한 프로젝트.

## 기술 스택
- Java 21 (Gradle toolchain이 자동으로 내려받음)
- Spring Boot 4.1 / Spring Framework 7 / Spring Security 7
- Spring Data JPA (Hibernate 7) + PostgreSQL 17, Flyway
- PostgreSQL (운영 `ticketing`, 테스트 `ticketing_test`)
- Redis 8 (분산 락)
- Kafka 4.3 (Docker, KRaft 단일 노드). 예매 이벤트 → 알림
- Gradle 9 (Kotlin DSL)

## 실행
### 1. 필요한 서버
PostgreSQL, Redis, Kafka. 같은 PC(localhost)에 있으면 설정 없이 바로 뜬다. 다른 서버에 있으면 2번에서 주소를 지정한다.

DB는 최초 1회 `postgres` 슈퍼유저로 만든다. 테이블은 앱이 뜰 때 Flyway가 만든다.
```sql
CREATE ROLE ticketing LOGIN PASSWORD '비밀번호';
CREATE DATABASE ticketing OWNER ticketing;
CREATE DATABASE ticketing_test OWNER ticketing;
```

### 2. 서버 주소와 비밀번호 (저장소에 넣지 않음)
`config-example` 폴더를 `config`로 복사해서 값을 채운다. `config/`는 `.gitignore`에 있고, Spring Boot가 실행 위치의 `./config/`를 자동으로 읽는다.
```bash
cp -r config-example config
```
- `config/application.properties`: DB/Redis/Kafka 주소, Redis 비밀번호, JWT 키
- `config/application-test.properties`: **테스트 DB 주소 (필수)**. `config/application.properties`가 테스트 설정보다 우선하기 때문에, 이 파일이 없으면 테스트가 운영 DB를 쓰게 된다. 그래서 `TestDatabaseGuard`가 DB 이름에 `test`가 없으면 테스트를 멈춘다.

DB 비밀번호는 `pgpass.conf`(Windows: `%APPDATA%\postgresql\pgpass.conf`)에 넣으면 JDBC 드라이버와 psql이 모두 읽는다.
```
DB서버주소:5432:*:ticketing:비밀번호
```
환경 변수(`DB_URL`, `REDIS_HOST`, `KAFKA_BOOTSTRAP`, `SPRING_DATASOURCE_PASSWORD` 등)로 줘도 된다.

### 3. 실행
```bash
./gradlew bootRun
```

```bash
curl http://localhost:8090/api/performances
curl http://localhost:8090/api/performances/1/seats
curl -X POST http://localhost:8090/api/reservations -H "Authorization: Bearer {accessToken}" -H "Content-Type: application/json" -d '{"seatId":1}'
```

테스트: `./gradlew test` (`ticketing_test` DB 사용)

### 4. 회원가입, 로그인, 토큰
1. `POST /api/auth/signup` `{ "email", "password"(8~64자), "name" }`
2. `POST /api/auth/login` → `{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600 }`
3. 이후 요청 헤더에 `Authorization: Bearer {accessToken}`

| 경로 | 권한 |
|---|---|
| `POST /api/auth/signup`, `/api/auth/login`, `GET /api/performances/**`, Swagger | 누구나 |
| `/api/reservations/**`, `/api/members/me` | 로그인 |
| `/api/admin/**` | `ADMIN` |

- **JWT 서명 키**: 환경 변수 `JWT_SECRET`(32바이트 이상)으로 준다. 없으면 기동할 때마다 임의 키가 생겨 재시작 시 기존 토큰이 무효가 된다.
- **관리자 만들기**: 가입한 뒤 DB에서 권한을 올리고 **다시 로그인**한다 (권한은 토큰 안에 들어 있어서 재발급해야 반영됨).
  ```sql
  UPDATE member SET role = 'ADMIN' WHERE email = '관리자이메일';
  ```

### 5. Swagger UI와 관리자 API
- Swagger UI: http://localhost:8090/swagger-ui.html
- 로그인 API로 받은 `accessToken`을 우측 상단 **Authorize**에 넣으면 보호된 API를 바로 호출할 수 있다.

```json
POST /api/admin/performances
{
  "title": "오페라의 유령",
  "venue": "샤롯데씨어터",
  "startAt": "2026-12-24T19:00:00",
  "sections": [
    { "section": "A", "grade": "VIP", "price": 190000, "seatCount": 10 },
    { "section": "B", "grade": "R",   "price": 160000, "seatCount": 20 }
  ]
}
```
Windows Git Bash의 curl로 한글 JSON을 보내면 인코딩이 깨져 400이 날 수 있다. UTF-8 파일로 저장해서 `--data-binary @파일`로 보내거나 Swagger UI를 쓴다.

### 6. 예매 → 결제 흐름
```
POST /api/reservations {seatId}                  → PENDING (좌석만 잡음)
POST /api/reservations/{id}/payment {paymentToken} → PG 승인 → CONFIRMED
DELETE /api/reservations/{id}                    → (결제됐으면 PG 환불 후) CANCELLED, 좌석 반환
```
PG는 이 앱 안의 **모의 PG**(`/mock-pg/**`)를 HTTP로 호출한다. 실제 PG로 바꿀 때는 `PAYMENT_BASE_URL`만 바꾼다.

| paymentToken | 모의 PG 동작 | 결과 |
|---|---|---|
| `tok_success` | 승인 | 200, CONFIRMED |
| `tok_flaky` | 첫 요청만 503 | 재시도 1번 후 200 |
| `tok_declined` | 400 카드 거절 | 재시도 없이 402 `PAYMENT_DECLINED` |
| `tok_timeout` | 응답 지연 | read-timeout(2s) × 3번 후 503 `PAYMENT_UNAVAILABLE` |

설계 포인트:
- **PG 호출은 DB 트랜잭션 밖에서** 한다 (`ReservationPaymentService`). 느린 외부 API가 DB 커넥션/락을 붙잡지 않게.
- **일시적 장애만 재시도** (타임아웃, 5xx). 4xx는 다시 보내도 같으니 재시도하지 않는다.
- **멱등성 키**(`Idempotency-Key: R-{예매id}`)를 보내서 재시도해도 중복 결제가 안 된다.
- 결제 승인 후 예매 확정에 실패하면 **PG 결제를 취소**한다 (보상 처리).

남은 과제(TODO):
- 결제하지 않은 PENDING 예매가 좌석을 계속 잡고 있다 → 일정 시간 후 자동 취소(`@Scheduled`).
- 타임아웃이 났는데 PG에서는 실제로 승인된 경우 → PG 조회 API로 결과 대사(reconciliation).

## 패키지 구조
```
com.example.ticketing
├── auth          # 회원가입/로그인, JWT 발급, @CurrentMemberId
├── member        # 회원 엔티티
├── common        # ErrorCode, BusinessException, ProblemDetail 예외 처리, BaseTimeEntity, PageResponse
├── config        # SecurityConfig, JwtConfig(인코더/디코더/권한 변환), JpaConfig, OpenApiConfig(Swagger)
├── performance   # 공연, 좌석 (조회 API, 관리자 등록/수정 API)
├── reservation   # 예매/결제/취소, 내 예매 목록, 관리자 매출 집계
│   └── lock      # 좌석 동시 예매 제어 5가지 (SeatReserver 구현체)
├── payment       # PG 연동: PaymentClient(@HttpExchange), PaymentGateway(@Retryable)
├── outbox        # Transactional Outbox: 이벤트 기록(Recorder), Kafka 발행(Relay)
├── notification  # Kafka 소비자 → 알림 (중복 무시), 내 알림 API
└── mockpg        # 개발용 가짜 결제사 서버 (/mock-pg/**)
```

## Repository 쿼리 작성 방식 (예전 DAO + mapper.xml 대신)
[ReservationRepository](src/main/java/com/example/ticketing/reservation/ReservationRepository.java)에 방식별 예제가 모여 있다.

| 방식 | 메서드 | 사용처 |
|---|---|---|
| 메서드 이름 쿼리 | `findByMemberIdOrderByIdDesc` | N+1 비교 실습 |
| JPQL + `join fetch` + `Pageable` | `findMyReservations` | `GET /api/reservations/me` |
| native SQL + 인터페이스 프로젝션 | `salesByGrade` | `GET /api/admin/performances/{id}/sales` |

N+1 확인: `ReservationQueryCountTest`가 Hibernate Statistics로 실제 SQL 수를 센다
(지연 로딩 7번 vs `join fetch` 1번). 실행된 SQL은 로그(`org.hibernate.SQL`)에서 볼 수 있다.

## 이벤트로 알림 분리 (Transactional Outbox + Kafka)
```
[예매 트랜잭션]
ReservationService.confirmPayment() ── publishEvent(Confirmed)
  └ ReservationOutboxRecorder (BEFORE_COMMIT) → outbox_event 행 저장     ← 예매 변경과 함께 커밋/롤백
                                   │
OutboxRelay (@Scheduled 300ms)     │  published_at이 빈 행을 FOR UPDATE SKIP LOCKED로 가져와
  └ Kafka "reservation-events" 토픽으로 전송 (key=예매id, 헤더 eventId/eventType) → ack 받으면 published_at 표시
                                   │
ReservationEventConsumer (@KafkaListener, 파티션 3개 / 소비자 3개)
  └ NotificationSender.send(eventId, ...) → 이미 처리한 eventId면 무시 → notification 저장
```
| 상황 | 결과 |
|---|---|
| 결제 확정이 롤백됨 | outbox 행도 롤백 → 알림 없음 |
| 커밋 직후 앱이 죽음 | 이벤트는 DB에 남아 있음 → 재시작 후 릴레이가 보냄 |
| Kafka가 잠시 죽음 | 보내기 실패 → outbox에 남아 다음 주기에 재시도 |
| 릴레이가 보내고 표시 전에 죽음 | 같은 이벤트가 한 번 더 감 → 소비자가 eventId로 중복 무시 |
| 알림 소비자가 잠시 죽음 | Kafka에 남아 있음 → 살아나면 마지막 offset부터 이어서 처리 |

- 결제 응답은 알림을 기다리지 않는다: 결제 0.28초, 알림은 약 1.5초 뒤 저장(알림 API 지연 1초 흉내 포함).
- 같은 예매의 이벤트는 같은 키 → 같은 파티션 → 순서대로 처리된다 (확정 → 취소).
- 전달 보장은 **최소 한 번(at-least-once)**. "정확히 한 번"은 받는 쪽 멱등 처리로 만든다.
- 확인: `GET /api/notifications/me`, `OutboxTest`

이 단계에 오기 전 6-1에서는 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 알림을 보냈다.
간단하지만 이벤트가 메모리에만 있어서, 커밋 직후 서버가 죽으면 알림이 사라지는 한계가 있었다.

### Kafka 서버 (Docker)
`apache/kafka:4.3.1` 단일 노드 KRaft 모드 (ZooKeeper 없음). 토픽은 앱이 뜰 때 `KafkaConfig`가 만든다.
테스트는 `reservation-events-test` 토픽과 별도 소비자 그룹을 쓰고, 자동 릴레이를 끈 채 `publishPending()`을 직접 부른다.

## 좌석 동시 예매 제어 5가지 비교
`ticketing.reservation.lock-strategy`로 고른다 (기본 `PESSIMISTIC`). 구현은 [reservation/lock](src/main/java/com/example/ticketing/reservation/lock) 패키지.

| 방식 | 원리 | 기다리는 곳 |
|---|---|---|
| `PESSIMISTIC` | `SELECT ... FOR UPDATE` | DB 행 락 (커넥션 점유) |
| `OPTIMISTIC` | `@Version`, 커밋 때 충돌하면 실패 | 안 기다림 (늦으면 실패) |
| `CONDITIONAL_UPDATE` | `UPDATE ... WHERE status='AVAILABLE'` 영향 행 수로 판단 | DB 행 락 (아주 짧게) |
| `ADVISORY_LOCK` | PostgreSQL `pg_advisory_xact_lock(1, 좌석id)` | DB 락 (커넥션 점유) |
| `REDIS_LOCK` | Redis `SET NX PX` + Lua 해제, 못 잡으면 즉시 거절 | 안 기다림 (DB 커넥션 안 씀) |

`LockStrategyBenchmarkTest` 결과 (동시 300명, 커넥션 풀 10, 실행마다 수치는 조금씩 다름):

| 방식 | A: 300명 → 좌석 1개 | B: 300명 → 각자 다른 좌석 |
|---|---|---|
| PESSIMISTIC | 1045ms | 631ms |
| OPTIMISTIC | 341ms | 470ms |
| CONDITIONAL_UPDATE | 810ms | 622ms |
| ADVISORY_LOCK | 1390ms | 488ms |
| REDIS_LOCK (대기 스핀 락) | **8437ms** | 536ms |
| REDIS_LOCK (try-lock, 현재) | 1437ms | 797ms |

모든 방식에서 A는 성공 1 / 거절 299, B는 성공 300, 예외 0 (`ReservationConcurrencyTest`가 5가지 모두 검증).

배운 점:
- **"기다린다"가 비용이다.** 좌석은 한 명만 가질 수 있으니, 기다렸다 들어가 봐야 "이미 팔림". 스핀 락은 300명이 줄 서서 한 명씩 DB를 확인해 8초가 걸렸다 → 못 잡으면 즉시 거절로 바꿔 1.4초.
- **사전 확인(락 전에 좌석 조회)은 동시에 몰리는 순간엔 소용없었다.** 모두가 확인하는 시점엔 아직 빈 좌석이기 때문.
- 이 규모(단일 DB, 서버 1대)에서는 DB만으로 충분히 빠르다. Redis 락은 DB가 병목이 되거나 서버가 여러 대일 때, 혹은 대기열·좌석 선점(TTL) 같은 Redis 고유 기능과 함께 쓸 때 의미가 커진다.

## 예전 vs 지금 (이 프로젝트에서 쓰인 곳)
| 예전 (Spring 3~4) | 지금 | 위치 |
|---|---|---|
| XML/JavaConfig 수동 설정 | Boot auto-config + `application.yml` | `application.yml` |
| `javax.persistence` | `jakarta.persistence` | 모든 엔티티 |
| `WebSecurityConfigurerAdapter` 상속 | `SecurityFilterChain` 빈 + 람다 DSL | `SecurityConfig` |
| getter/setter DTO 클래스 | `record` | `PerformanceDtos`, `ReservationResult` |
| `instanceof` + 캐스팅, if-else | `sealed` + switch 패턴 매칭, record 분해 | `ReservationController#reserve` |
| 직접 만든 에러 JSON | `ProblemDetail` (RFC 9457) | `GlobalExceptionHandler` |
| `hbm2ddl=update` | Flyway 마이그레이션 + `ddl-auto: validate` | `db/migration` |
| 톰캣 스레드 풀 튜닝 | 가상 스레드 `spring.threads.virtual.enabled` | `application.yml` |
| H2 인메모리 테스트 | 실제 PostgreSQL(테스트 전용 DB) | `application-test.yml` |
| `mockMvc.perform().andExpect()` 체인 | `MockMvcTester` (AssertJ 스타일) | `AdminPerformanceControllerTest` |
| 문자열 이어붙인 JSON (`"{\"a\":..."`) | text block (`"""`) | `AdminPerformanceControllerTest` |
| Swagger 2 / springfox | springdoc-openapi 3 (OpenAPI 3) | `OpenApiConfig` |
| 세션 로그인 + `HttpSession`에서 회원 꺼내기 | JWT(무상태) + OAuth2 Resource Server | `SecurityConfig`, `JwtConfig` |
| JWT 직접 파싱하는 커스텀 필터 (jjwt 등) | Spring Security 내장 `JwtEncoder`/`JwtDecoder` | `JwtConfig`, `AuthService` |
| `session.getAttribute("loginUser")` | `@CurrentMemberId Long memberId` (`@AuthenticationPrincipal` 메타 어노테이션) | `CurrentMemberId` |
| `@Value("${...}")` 여러 개 | `@ConfigurationProperties` record | `JwtProperties` |
| 비밀번호 MD5/SHA 직접 해시 | `DelegatingPasswordEncoder` (`{bcrypt}...`) | `JwtConfig#passwordEncoder` |
| 로그인 테스트하려면 실제 로그인 | `jwt()` 요청 후처리기로 로그인 상태 흉내 | `ReservationControllerTest` |
| DAO 구현 클래스 + mapper.xml | Repository 인터페이스 (구현은 자동 생성) | `ReservationRepository` |
| `LIMIT/OFFSET` + count 쿼리 직접 작성 | `Pageable` → `Page` | `findMyReservations` |
| `@RequestParam` 검증 하려면 `@Validated` 필요 | 컨트롤러 메서드 검증 기본 지원 (Spring 6.1+) | `ReservationController#myReservations` |
| `RestTemplate` + URL 문자열 조립 + 응답 파싱 | HTTP Interface `@HttpExchange` (선언만 하면 구현 자동) | `PaymentClient` |
| RestTemplate 빈 직접 만들고 타임아웃 코드로 설정 | `@ImportHttpServices` + `spring.http.serviceclient.*` 설정 | `PaymentConfig`, `application.yml` |
| 서비스에서 알림/포인트 서비스를 직접 호출 | `ApplicationEventPublisher` + `@TransactionalEventListener` | `ReservationService`, `ReservationOutboxRecorder` |
| JMS(ActiveMQ) 큐 | Kafka 토픽 + `@KafkaListener` (읽어도 남음, 재처리 가능) | `OutboxRelay`, `ReservationEventConsumer` |
| DB 저장 후 메시지 발송 (둘 중 하나만 성공할 수 있음) | Transactional Outbox (같은 트랜잭션에 이벤트 저장) | `outbox` 패키지 |
| Jackson 2 (`com.fasterxml.jackson`) | Jackson 3 (`tools.jackson`, Boot 4 기본) | `ReservationOutboxRecorder` |
| `@Async`용 스레드 풀 크기 튜닝 | 가상 스레드 실행기 (Boot가 자동 구성) | `AsyncConfig` |
| `instanceof` 분기로 이벤트 종류 구분 | sealed interface + switch 패턴 매칭 | `ReservationEvent`, `ReservationNotificationListener` |
| spring-retry / Resilience4j 추가 | Spring Framework 7 내장 `@Retryable` (`@EnableResilientMethods`) | `PaymentGateway` |
| `List` 요소 검증 직접 구현 | 타입 인자 검증 `List<@Valid SectionRequest>` | `PerformanceDtos` |

## 로드맵
- [x] 1. 프로젝트 뼈대, 공연/좌석 조회, 예매/취소 (DB 비관적 락)
- [x] 2. 공연 등록/수정 관리자 API, springdoc-openapi로 Swagger UI
- [x] 3. 회원가입/로그인 + JWT (`spring-boot-starter-security-oauth2-resource-server`), memberId를 토큰에서 꺼내기
- [x] 4. 결제 모의 서버 연동: HTTP Interface(`@HttpExchange`) + `RestClient`, Spring 7 `@Retryable` 재시도, 멱등성 키
- [x] 5. 동시성 비교: 비관적 / 낙관적 / 조건부 UPDATE / PostgreSQL Advisory Lock / Redis 분산 락, 가상 스레드 부하 테스트
- [x] 6-1. 예매 완료/취소 이벤트 → Spring 이벤트(`@TransactionalEventListener` + `@Async`)로 알림 분리
- [x] 6-2. 이벤트 유실 방지: Transactional Outbox → Kafka → 알림 소비자 (eventId로 중복 무시)
- [ ] 7. Actuator + Prometheus + Grafana 모니터링
- [ ] 8. (보너스) GraalVM 네이티브 이미지 빌드
