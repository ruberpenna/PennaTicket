# PennaTicket - Claude 작업 안내

공연 좌석 예매 시스템. 레거시 Spring(3~4) 경험자가 최신 스택을 익히려는 학습 프로젝트다.
설계와 단계별 내용은 README.md에 있다. 이 파일은 작업 방법과 지켜야 할 규칙만 담는다.

## 대화
- **항상 한국어로 답한다.** 사용자는 원격(휴대폰)으로 접속하는 경우가 많으니 결론부터 짧게, 명령은 복사해서 바로 쓸 수 있게 준다.
- 사용자가 서버(리눅스)에서 실행할 명령을 줄 때는 비밀번호가 화면이나 대화에 남지 않는 방식(`read -s` 등)을 쓴다.

## 스택
Java 21 (Gradle toolchain이 자동 설치) · Spring Boot 4.1 / Spring 7 / Security 7 · JPA(Hibernate 7) · PostgreSQL · Flyway ·
Redis 8 · Kafka 4.3 · Micrometer/Prometheus/Grafana · Gradle 9 (Kotlin DSL). Jackson은 3 (`tools.jackson`).

## 서버 구성
- 실제 주소는 **`config/application.properties`** (git 제외)에 있다. 저장소(공개)에는 IP를 넣지 않는다.
- DB 서버: PostgreSQL. 운영 DB `ticketing`, 테스트 DB `ticketing_test`. 비밀번호는 `%APPDATA%\postgresql\pgpass.conf`.
- 모니터링 서버: Redis, Kafka(Docker), Prometheus/Grafana(`monitoring/` docker compose). **매일 새벽 재부팅**되며 자동으로 다시 켜진다.
  이 서버는 성능이 낮고 상시 운영용이 아니라서 **앱을 올리지 않는다** (사용자 결정).
- 비밀값 파일은 열어 보거나 출력하지 않는다: `config/application.yml`(Redis 비밀번호), `config/application.properties`의 `ticketing.jwt.secret`, pgpass.conf.

## 앱 실행 / 종료
앱은 상시 운영하지 않고 **필요할 때만 띄운다.** 사용자가 "앱 띄워 줘"라고 하면 아래처럼 한다.
- 실행 (백그라운드): `./gradlew bootRun --no-daemon > "$TEMP/ticketing-bootrun.log" 2>&1` 을 run_in_background로
- 준비 확인: `curl -s http://localhost:8090/actuator/health` 가 `UP` 이 될 때까지 대기 (기동 약 25초)
- 확인할 것: 기동 로그의 `Started TicketingApplication`, DB/Redis/Kafka 연결 경고 여부
- 종료: 백그라운드 작업을 멈춘 뒤, 남은 java 프로세스도 정리한다
  (PowerShell: `Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'pennaTicket' } | Stop-Process`)
- 사용자가 PC 앞에서 직접 띄울 때는 `run.bat` (ASCII만 쓸 것: bat에 한글 + `chcp 65001`이면 cmd가 줄을 잘못 읽는다)
- 포트 8090 (8080은 이 PC의 다른 프로그램이 사용 중)
- Swagger UI: http://localhost:8090/swagger-ui.html

## 테스트
- `./gradlew test --no-daemon` (전체 약 3~5분). 결과 요약은 `build/test-results/test/*.xml`에서 읽는다.
- 테스트는 실제 서버(테스트 DB, Redis, Kafka)를 쓴다. Kafka가 꺼져 있으면 알림 관련 테스트가 실패하니, 실패 시 먼저 서버 연결을 확인한다.
- `config/application-test.properties`가 테스트 DB를 가리킨다. 없으면 `TestDatabaseGuard`가 테스트를 멈춘다 (운영 DB 보호).
- 테스트에서는 Outbox 자동 릴레이가 꺼져 있고 `OutboxRelay.publishPending()`을 직접 부른다.

## 운영 DB 확인용 데이터
- 앱 동작을 실제로 확인하느라 운영 DB(`ticketing`)에 회원/예매를 만들었으면 **끝난 뒤 반드시 지운다**
  (알림 → outbox_event → 예매 → 회원 순서, 예매된 좌석은 AVAILABLE로 되돌림). 지운 뒤 개수를 확인해서 보고한다.
- 사용자의 실제 비밀번호를 대신 입력하지 않는다. 확인용 계정은 임의 비밀번호로 만들고 저장하지 않는다.
- psql: `"/c/Program Files/PostgreSQL/15/bin/psql.exe" -h <DB서버> -U ticketing -d ticketing -w` (pgpass로 인증)

## Git
- G: 드라이브는 소유자 정보가 없어서 git이 `dubious ownership`을 낸다. 명령마다 `git -c safe.directory=G:/pennaTicket ...`
- 작성자는 저장소 설정(ruberpenna)에 들어 있다. 단계/작업 단위로 커밋하고 GitHub(origin/main)에 push한다.
- 커밋 전 확인: `config/`가 제외되는지, 추적 파일에 내부 IP나 비밀값이 없는지.
- **앱이 떠 있으면 Gradle이 `gradle/wrapper/gradle-wrapper.jar`를 잠근다.** checkout/rebase/merge 전에는 앱을 먼저 끈다
  (잠긴 채 rebase하다 작업 폴더 파일이 사라졌던 적이 있다).

## Windows 환경 주의
- 셸은 Git Bash와 PowerShell 5.1. PowerShell 7 전용 문법(`Read-Host -MaskInput` 등)을 사용자에게 안내하지 않는다.
- Git Bash의 curl로 한글 JSON을 보내면 깨진다 → UTF-8 파일로 저장해 `--data-binary @파일`로 보내거나 Python urllib 사용.
- 방화벽 등 시스템 설정은 직접 바꾸지 않고 사용자가 실행할 명령을 안내한다.
