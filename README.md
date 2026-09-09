# 🎓 출석하자 Backend

NFC 기반 출석 요청을 검증하고, 출석 기록과 관리자 기능을 제공하는 **출석 관리 시스템의 API 서버**입니다.

인증, 출석, 세션, 사용자, NFC 태그, 통계, 알림과 조직 API를 구현했으며, AWS 환경에 직접 배포해 React 관리자 웹과의 실제 연동을 검증했습니다.

## 📖 서비스 소개

**출석하자 Backend는 학생의 출석 요청을 처리하고, 관리자가 출석 데이터를 조회·관리할 수 있도록 공통 API를 제공합니다.**

서버에서 사용자와 NFC 태그, 활성 세션, 출석 가능 시간과 중복 요청을 검증해 출석 상태를 결정합니다. 조직별 데이터 격리, 동시 체크인 제어와 출석 처리 이후의 실시간 이벤트 전달도 담당합니다.

현재 **AWS에 배포한 서버와 관리자 웹의 API 연동을 검증한 상태**입니다. Android 학생 앱은 Fake Repository 기반으로 개발 중이며, 실제 서버 연동은 아직 완료되지 않았습니다.

<details>
<summary><strong>🌐 전체 서비스 구성 및 관련 저장소</strong></summary>

출석하자는 **학생용 Android 앱**, **관리자용 어드민 웹**, **공통 백엔드 API**로 구성하는 출석 관리 서비스입니다. 이 저장소는 인증과 출석 데이터 처리를 담당하는 백엔드를 다룹니다.

| 구성 | 사용자 | 역할 |
| --- | --- | --- |
| 🎓 Android 앱 | 출석 대상자(학생) | 출석 체크, 본인 기록 조회, 정정 요청, 알림 확인 |
| 🖥️ 어드민 웹 | 관리자 | 세션·출석 기록·사용자·NFC 태그·알림 관리 |
| ⚙️ 백엔드 API | 앱·웹 공통 | 인증·인가, 출석 검증, 데이터 저장·조회, 조직별 데이터 격리 |

### 서비스 연동 구조

```text
🎓 학생 · Android 앱
   출석 체크 · 본인 기록 조회 · 정정 요청
                 │
                 │ 연동 예정
                 ▼
          ⚙️ Spring Boot API
          인증 · 출석 검증 · 조직별 데이터 관리
                 ▲
                 │ REST API 연동 검증 완료
                 │
🧑‍💼 관리자 · 어드민 웹
   세션 · 출석 현황 · 사용자 · NFC 태그 관리
```

| 연결 영역 | 현재 상태 |
| --- | --- |
| Backend 배포 | AWS EC2·RDS 환경 배포 및 동작 검증 |
| 관리자 웹 → Backend | 로컬 웹에서 AWS Backend API 연동 검증 |
| 학생 앱 → Backend | Fake Repository 기반 개발 중, 실제 연동 예정 |
| 실시간 출석 이벤트 | Backend의 STOMP WebSocket 구현, 관리자 웹 수신은 미적용 |

백엔드의 실시간 이벤트 구현과 웹의 실시간 수신 적용은 별개입니다. 현재 관리자 웹과의 연동 검증 범위는 REST API 기반 기능입니다.

### 관련 저장소

| 프로젝트 | 저장소 |
| --- | --- |
| 🎓 학생용 Android 앱 | [attendance-app](https://github.com/Gahyun-313/attendance-app) |
| 🖥️ 관리자용 어드민 웹 | [attendance-web-claude](https://github.com/Gahyun-313/attendance-web-claude) |
| ⚙️ 백엔드 API | 현재 저장소 |

</details>

<br>

## 📌 프로젝트 정보

| 항목 | 내용 |
| --- | --- |
| 형태 | 개인 프로젝트 |
| 담당 | Backend 설계·개발·테스트·배포 |
| 서버 | Java 17, Spring Boot |
| 데이터 | MySQL, Redis |
| 배포 | Docker, AWS EC2, RDS |
| 클라이언트 | Android 학생 앱, React 관리자 웹 |

<br>

## 👨‍💻 담당 역할

- 출석·세션·사용자·NFC 태그·통계·알림·조직 도메인과 REST API 설계 및 구현
- Spring Security와 JWT 기반 인증·인가, Google·Kakao 관리자 가입 흐름 구현
- 조직별 데이터가 섞이지 않도록 공유 스키마 멀티테넌시 적용
- STOMP WebSocket을 이용한 관리자 출석 현황 실시간 갱신
- Redis 캐싱과 Redisson 분산 락을 이용한 조회 및 동시 체크인 처리
- 단위·Repository·통합·k6 부하 테스트 작성 및 결과 검증
- Docker 기반 실행 환경 구성과 AWS EC2·RDS 배포

<br>

## 🛠️ Tech Stack

| Category | Stack |
| --- | --- |
| Core | Java 17, Spring Boot, Gradle |
| Security | Spring Security, JWT |
| Data | Spring Data JPA, MySQL |
| Cache / Lock | Redis, Redisson |
| Realtime | WebSocket, STOMP |
| Test | JUnit, Mockito, H2, MockMvc, k6 |
| Deployment | Docker, Docker Compose, AWS EC2, RDS |

<br>

## ✨ 주요 구현

### NFC 출석 처리

JWT 사용자, NFC 태그, 활성 세션, 출석 가능 시간과 중복 여부를 서버에서 검증하고 최종 출석 상태를 결정합니다. 클라이언트의 판정값을 신뢰하지 않고 서버를 단일 진실 공급원으로 유지했습니다.

[출석 처리 흐름 자세히 보기](./docs/ATTENDANCE_FLOW.md)

### 동시 체크인 제어

같은 사용자의 동시 체크인 20건에서 발생하던 500 오류를 k6로 재현했습니다. Redisson 분산 락, 트랜잭션 종료 후 락 해제, `READ_COMMITTED` 격리 수준을 함께 적용해 성공 1건과 의도한 409 응답 19건으로 안정화했습니다.

[문제 해결 과정 자세히 보기](./docs/TROUBLESHOOTING.md)

### 실시간 출석 현황

출석 트랜잭션이 커밋된 뒤 이벤트를 발행하고, STOMP WebSocket으로 관리자 화면에 최신 출석 기록과 집계를 전달합니다. 구독 시점에 ADMIN 권한도 검증합니다.

[실시간 처리 흐름 자세히 보기](./docs/REALTIME_FLOW.md)

### 조직별 데이터 격리

공유 스키마 방식으로 조직을 구분하고 목록 조회뿐 아니라 ID 직접 접근, 상태 변경, 통계와 캐시까지 조직 단위로 격리했습니다.

[아키텍처 자세히 보기](./docs/ARCHITECTURE.md)

### 측정 기반 성능 개선

| Scenario | Before | After |
| --- | --- | --- |
| 동일 사용자 동시 체크인 20건 | 성공 1 / 409 12 / 500 7 | 성공 1 / 409 19 / 500 0 |
| 통계 조회 | 평균 9.27ms / p95 16.04ms | 10만 건에서 평균 8.63ms / p95 14.28ms |
| 출석 기록 정렬 | `using_filesort: true` | 복합 인덱스로 `using_filesort: false` |

측정 조건과 각 개선이 검증하는 범위를 분리해 기록했습니다.

[테스트 전략 및 결과 보기](./docs/TESTING.md)

<br>

## 🚀 배포

- 애플리케이션, MySQL, Redis를 Docker Compose로 구성하고 로컬 기동 검증
- 운영 환경에서는 EC2의 애플리케이션·Redis와 RDS MySQL을 분리
- 환경변수로 DB 접속 정보와 JWT Secret을 주입하고 운영 환경에서 `ddl-auto: validate` 사용
- EC2 배포 후 로그인 및 관리자 Web 연동 검증

현재 Nginx 리버스 프록시와 HTTPS는 적용하지 않았습니다.

[배포 구성과 트러블슈팅 보기](./docs/DEPLOYMENT.md)

<br>

## 📚 Documentation

| Document | Description |
| --- | --- |
| [Architecture](./docs/ARCHITECTURE.md) | 서버 구조, 인증과 멀티테넌시 설계 |
| [Attendance Flow](./docs/ATTENDANCE_FLOW.md) | NFC 체크인 검증 및 상태 결정 흐름 |
| [Realtime Flow](./docs/REALTIME_FLOW.md) | 트랜잭션 이후 WebSocket 이벤트 전달 |
| [Testing](./docs/TESTING.md) | 테스트 계층과 k6 측정 결과 |
| [Troubleshooting](./docs/TROUBLESHOOTING.md) | 동시 체크인 문제의 분석과 해결 |
| [Deployment](./docs/DEPLOYMENT.md) | Docker 및 AWS 배포 구성 |
