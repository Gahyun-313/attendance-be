# 🎓 출석하자 Backend

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white)

NFC 기반 출석 요청을 검증하고, 출석 기록과 관리자 기능을 제공하는 **출석 관리 시스템의 API 서버**입니다.

인증, 출석, 세션, 사용자, 그룹, NFC 태그, 통계, 알림과 조직 API를 구현했으며, AWS 환경에 직접 배포해 React 관리자 웹과의 실제 REST API 연동을 검증했습니다.

<img width="1707" height="921" alt="image" src="https://github.com/user-attachments/assets/71c71113-6f80-4f79-aa2d-55cc30a01a91" />

<br>

## 📖 서비스 소개

**출석하자 Backend는 학생의 출석 요청을 처리하고, 관리자가 출석 데이터를 조회·관리할 수 있도록 공통 API를 제공합니다.**

서버에서 사용자와 NFC 태그, 활성 세션, 출석 가능 시간과 중복 요청을 검증해 최종 출석 상태를 결정합니다. 조직별 데이터 격리, 동시 체크인 제어와 출석 처리 이후의 실시간 Event 전달도 담당합니다.

✅ **React 관리자 웹과의 REST API 연동을 완료했습니다.** AWS EC2·RDS에 배포한 서버를 대상으로 관리자 웹의 로그인, 대시보드, 세션·출석 기록·사용자·NFC 태그 관리 기능을 연동해 실제 동작을 검증했습니다.

현재 AWS 환경은 내린 상태이며, 아래 실행 방법으로 로컬에서 같은 구성을 실행할 수 있습니다.

<details>
<summary><strong>🌐 전체 서비스 구성 및 관련 저장소</strong></summary>

출석하자는 **학생용 Android 앱**, **관리자용 어드민 웹**, **공통 Backend API**로 구성하는 출석 관리 서비스입니다. 이 저장소는 인증과 출석 데이터 처리를 담당하는 Backend를 다룹니다.

| 구성 | 사용자 | 역할 |
| --- | --- | --- |
| 🎓 Android 앱 | 출석 대상자(학생) | 출석 체크, 본인 기록 조회, 정정 요청, 알림 확인 |
| 🖥️ 어드민 웹 | 관리자 | 세션·출석 기록·사용자·NFC 태그·알림 관리 |
| ⚙️ Backend API | 앱·웹 공통 | 인증·인가, 출석 검증, 데이터 저장·조회, 조직별 데이터 격리 |

### 서비스 연동 구조

```text
🧑‍💼 관리자 · 어드민 웹
   로그인 · 대시보드 · 세션 · 출석 현황 · 사용자 · NFC 태그 관리
                 │
                 │ ✅ REST API 연동 완료
                 ▼
          ⚙️ Spring Boot API
          인증 · 출석 검증 · 조직별 데이터 관리
                 ▲
                 │ 연동 예정
                 │
🎓 학생 · Android 앱
   출석 체크 · 본인 기록 조회 · 정정 요청
```

| 연결 영역 | 현재 상태 |
| --- | --- |
| 관리자 웹 → Backend | ✅ AWS 배포 서버와 REST API 연동 완료 |
| Backend 배포 | ✅ AWS EC2·RDS 배포 및 동작 검증 완료 (현재 내림) |
| 실시간 출석 Event | ✅ Backend STOMP WebSocket 구현 및 Test Client 검증 |
| 관리자 웹 실시간 수신 | 미적용 (현재 연동 범위는 REST API) |
| 학생 앱 → Backend | 연동 예정 (앱은 Fake Repository 기반으로 화면 개발 중) |

### 관련 저장소

| 프로젝트 | 저장소 |
| --- | --- |
| 🎓 학생용 Android 앱 | [attendance-app](https://github.com/Gahyun-313/attendance-app) |
| 🖥️ 관리자용 어드민 웹 | [attendance-web-claude](https://github.com/Gahyun-313/attendance-web-claude) |
| ⚙️ Backend API | 현재 저장소 |

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

- 출석·세션·사용자·그룹·NFC 태그·통계·알림·조직 도메인과 REST API 설계 및 구현
- Spring Security와 JWT 기반 인증·인가 구현
- Google·Kakao 관리자 가입 및 로그인 흐름 구현
- 조직별 데이터가 섞이지 않도록 공유 Schema Multi-tenancy 적용
- STOMP WebSocket을 이용한 출석 Event 전달 구현
- Redis Cache와 Redisson 분산 Lock을 이용한 조회 및 동시 체크인 처리
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

## 🔌 API

JWT 인증과 역할 기반 권한을 적용한 REST API를 도메인별로 구성했습니다. 관리자 API는 `ADMIN`, 학생 출석 API는 `STUDENT` 권한을 기준으로 접근을 제한합니다.

| Domain | 주요 기능 | 대표 Endpoint |
| --- | --- | --- |
| Authentication | 일반·소셜 로그인, Token 갱신, 로그아웃, 이메일 가입, 비밀번호 재설정 | `/api/auth/**` |
| User | 학생 계정과 내 정보 조회·수정, 비활성화·재활성화 | `/api/users/**` |
| Group | 출석 대상 그룹 생성·조회·수정·삭제 | `/api/groups/**` |
| Session | 출석 세션 생성·조회·수정 및 상태 전환 | `/api/sessions/**` |
| Attendance | NFC 체크인, 출석 기록 조회·수정, 세션별 현황 | `/api/attendances/**` |
| NFC Tag | NFC 태그 등록·조회·수정, 활성화 및 검증 | `/api/nfc-tags/**` |
| Notification | 알림 생성·조회·취소 | `/api/notifications/**` |
| Statistics | 전체·대시보드·사용자별 통계와 출석률 Ranking | `/api/statistics/**` |
| FCM Token | 기기별 FCM Token 등록·삭제 | `/api/fcm/tokens` |
| Organization | 관리자 소속 조직 정보 조회·수정 | `/api/organizations/me` |
| WebSocket | 출석 처리 이후 STOMP Event 전달 | `/ws/**` |

### 주요 API

| Method | Endpoint | 권한 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Public | 일반 로그인 |
| `POST` | `/api/auth/oauth/{provider}` | Public | Google·Kakao 소셜 로그인 |
| `POST` | `/api/auth/refresh` | Public | Access Token 갱신 |
| `POST` | `/api/sessions` | ADMIN | 출석 세션 생성 |
| `POST` | `/api/sessions/{sessionId}/start` | ADMIN | 출석 세션 시작 |
| `POST` | `/api/attendances/check-in` | STUDENT | NFC 출석 체크인 |
| `GET` | `/api/attendances/me` | STUDENT | 본인 출석 기록 조회 |
| `GET` | `/api/attendances/sessions/{sessionId}` | ADMIN | 세션별 출석 현황 조회 |
| `PUT` | `/api/attendances/{attendanceId}/status` | ADMIN | 출석 상태 수정 |
| `GET` | `/api/statistics/dashboard` | ADMIN | 관리자 대시보드 통계 조회 |
| `GET` | `/api/statistics/me` | 인증 필요 | 본인 출석 통계 조회 |
| `GET` | `/api/organizations/me` | ADMIN | 소속 조직 정보 조회 |

전체 Endpoint와 Request·Response, 권한, Enum 및 Error Code는 별도 문서에 정리했습니다.

[전체 API 명세 보기↗️](./docs/API_SPEC.md)

<br>

## ✨ 주요 구현

### NFC 출석 처리

JWT 사용자, NFC 태그, 활성 세션, 출석 가능 시간과 중복 여부를 서버에서 검증하고 최종 출석 상태를 결정합니다. 클라이언트의 판정값을 신뢰하지 않고 서버를 단일 진실 공급원으로 유지했습니다.

[출석 처리 흐름 자세히 보기↗️](./docs/ATTENDANCE_FLOW.md)

### 동시 체크인 제어

같은 사용자의 동시 체크인 20건에서 발생하던 500 오류를 k6로 재현했습니다. Redisson 분산 Lock, Transaction 종료 후 Lock 해제, `READ_COMMITTED` 격리 수준을 함께 적용해 성공 1건과 의도한 409 응답 19건으로 안정화했습니다.

[문제 해결 과정 자세히 보기↗️](./docs/TROUBLESHOOTING.md)

### 실시간 출석 Event

출석 Transaction이 Commit된 뒤 Event를 발행하고, STOMP WebSocket을 통해 최신 출석 기록과 집계 정보를 전달하도록 Backend를 구현했습니다. 연결과 구독 과정에서는 JWT와 `ADMIN` 권한을 검증합니다.

Backend의 STOMP Event 전달은 Test Client로 검증했으며, 현재 관리자 웹에는 WebSocket 실시간 수신을 적용하지 않았습니다.

[실시간 처리 흐름 자세히 보기↗️](./docs/REALTIME_FLOW.md)

### 조직별 데이터 격리

공유 Schema 방식으로 조직을 구분하고, 목록 조회뿐 아니라 ID 직접 접근, 상태 변경, 통계와 Cache까지 조직 단위로 격리했습니다.

[아키텍처 자세히 보기↗️](./docs/ARCHITECTURE.md)

### 측정 기반 성능 개선

| Scenario | Before | After |
| --- | --- | --- |
| 동일 사용자 동시 체크인 20건 | 성공 1 / 409 12 / 500 7 | 성공 1 / 409 19 / 500 0 |
| 통계 조회 | 평균 9.27ms / p95 16.04ms | 10만 건에서 평균 8.63ms / p95 14.28ms |
| 출석 기록 정렬 | `using_filesort: true` | 복합 Index로 `using_filesort: false` |

측정 조건과 각 개선이 검증하는 범위를 분리해 기록했습니다.

[테스트 전략 및 결과 보기↗️](./docs/TESTING.md)

<br>

## 📁 프로젝트 구조

```text
src/main/java/com/attendance
├── domain                 # 도메인별 controller · service · repository · entity · dto
│   ├── attendance         # NFC 체크인, 출석 기록, 출석 Event
│   ├── session            # 출석 세션, 자동 종료 Scheduler
│   ├── user               # 인증, 사용자, Google·Kakao OAuth
│   ├── group              # 출석 대상 그룹
│   ├── nfc                # NFC 태그
│   ├── notification       # 알림
│   ├── fcm                # 기기별 FCM Token
│   ├── statistics         # 통계, 출석률 Ranking
│   └── organization       # 조직(Multi-tenancy)
└── global
    ├── config             # Security, Redis, WebSocket, Firebase, Scheduling 설정
    ├── security           # JWT Filter·Provider, STOMP 인증 Interceptor
    ├── exception          # ErrorCode, 전역 예외 처리
    ├── response           # 공통 응답 포맷
    ├── entity             # 공통 Entity
    └── util

src/main/resources
├── application.yml                  # 공통 설정 (기본 프로파일: local)
├── application-local.yml.example    # 로컬 실행용 설정 예시
├── application-prod.yml             # 컨테이너 실행용 설정 (환경변수 주입)
└── db
    ├── init.sql                     # Docker Compose 최초 기동 시 Schema·시드 데이터 생성
    └── migration                    # Flyway Migration (V1 ~ V3)

k6/                                  # 동시 체크인·응답 시간 부하 테스트 Script
```

<br>

## ▶️ 실행 방법

### 요구 사항

| 항목 | 버전 |
| --- | --- |
| JDK | 17 |
| Docker / Docker Compose | Docker Compose 실행 시 필요 |
| MySQL | 8.0 (로컬 직접 실행 시) |
| Redis | 7 (로컬 직접 실행 시) |

### 1. Docker Compose로 실행 (권장)

애플리케이션, MySQL과 Redis를 한 번에 띄웁니다. MySQL은 최초 기동 시 `init.sql`로 Schema와 시드 데이터를 생성합니다.

```bash
git clone https://github.com/Gahyun-313/attendance-be.git
cd attendance-be

cp .env.example .env   # 값 채우기 (아래 환경변수 참고)
docker compose up --build
```

| 서비스 | 접속 주소 |
| --- | --- |
| API 서버 | `http://localhost:8080` |
| MySQL | `localhost:3307` |
| Redis | `localhost:6379` |

> 로컬에 MySQL(3306)이나 Redis(6379)가 이미 떠 있으면 포트 충돌이 날 수 있으니 먼저 종료합니다.
> DB 데이터까지 초기화하려면 `docker compose down -v` 후 다시 실행합니다.

### 2. 로컬에서 직접 실행

MySQL과 Redis를 준비한 뒤 `local` 프로파일로 실행합니다. Schema는 애플리케이션 기동 시 Flyway가 생성합니다.

```bash
# Redis가 없다면 컨테이너로 실행
docker run -d --name attendance-redis -p 6379:6379 redis:7-alpine

# 로컬 설정 파일 생성 후 DB 접속 정보, JWT Secret, Google Client ID, 메일 계정 등 수정
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

./gradlew bootRun        # Windows: gradlew.bat bootRun
```

`init.sql`의 시드 데이터를 함께 쓰려면 DB 이름을 `attendance`로 맞추고 `init.sql`을 먼저 실행합니다. `application-local.yml`과 `.env`는 `.gitignore`에 포함되어 있어 커밋되지 않습니다.

### 3. 테스트 실행

```bash
./gradlew test
```

테스트는 H2 In-memory DB를 사용하며, Flyway와 Firebase는 비활성화한 상태로 실행됩니다. 실행 전에 다음 두 가지가 필요합니다.

- `application-local.yml` 준비: 테스트도 `local` 프로파일을 사용해 JWT 설정 등을 이 파일에서 읽습니다.
- `localhost:6379`에서 Redis 실행: 분산 Lock용 `RedissonClient`는 Mock으로 대체하지만, 이메일 인증 코드 저장 등 실제 값 저장·조회는 로컬 Redis를 사용합니다.

### 4. k6 부하 테스트

```bash
cp k6/config.json.example k6/config.json   # 계정과 NFC 태그 UID 입력
k6 run k6/checkin-concurrency-test.js
```

| Script | 측정 대상 |
| --- | --- |
| `checkin-concurrency-test.js` | 동일 사용자 동시 체크인 |
| `checkin-response-time.js` | 체크인 응답 시간 |
| `stats-response-time.js` | 통계 조회 응답 시간 |

<br>

## 🔑 환경변수

컨테이너 실행(`prod` 프로파일) 시 `.env`로 주입합니다. **기동 필수** 항목이 비어 있으면 애플리케이션이 기동되지 않습니다.

| 변수 | 설명 | 기동 필수 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | MySQL 접속 정보 (Compose 내부에서는 `mysql`) | |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 계정 | ✅ (`DB_PASSWORD`) |
| `REDIS_HOST` / `REDIS_PORT` | Redis 접속 정보 (Compose 내부에서는 `redis`) | |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) | ✅ |
| `CORS_ALLOWED_ORIGINS` | 허용할 관리자 웹 Origin | |
| `GOOGLE_OAUTH_CLIENT_ID` | Google 로그인 Client ID | ✅ |
| `FIREBASE_CREDENTIALS_BASE64` | Firebase 서비스 계정 JSON의 Base64 값 | ✅ |
| `MAIL_HOST` / `MAIL_PORT` | SMTP 서버 (기본값 Gmail) | |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 이메일 가입·비밀번호 재설정 메일 발송 계정 | 메일 기능 사용 시 |

### 시드 계정 (Docker Compose 로컬 환경)

| 역할 | 아이디 | 비밀번호 |
| --- | --- | --- |
| ADMIN | `admin` | `admin1234!` |
| STUDENT | `student1` ~ `student3` | `student1!` ~ `student3!` |

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234!"}'
```

<br>

## 🚀 배포

- 애플리케이션, MySQL과 Redis를 Docker Compose로 구성하고 로컬 기동 검증
- 운영 환경에서는 EC2의 애플리케이션·Redis와 RDS MySQL을 분리
- 환경변수로 DB 접속 정보와 JWT Secret 주입
- 운영 환경에서 `ddl-auto: validate` 사용
- EC2 배포 후 로그인 및 관리자 웹 REST API 연동 검증

검증을 마친 뒤 현재는 AWS 환경(EC2·RDS)을 내린 상태이며, Nginx Reverse Proxy와 HTTPS는 적용하지 않았습니다.

[배포 구성과 트러블슈팅 보기↗️](./docs/DEPLOYMENT.md)

<br>

## 📚 Documentation

| Document | Description |
| --- | --- |
| [API Specification](./docs/API_SPEC.md) | REST API Endpoint, 권한, Request·Response와 Error Code |
| [Architecture](./docs/ARCHITECTURE.md) | 서버 구조, 인증과 Multi-tenancy 설계 |
| [Attendance Flow](./docs/ATTENDANCE_FLOW.md) | NFC 체크인 검증 및 상태 결정 흐름 |
| [Realtime Flow](./docs/REALTIME_FLOW.md) | Transaction 이후 WebSocket Event 전달 |
| [Testing](./docs/TESTING.md) | 테스트 계층과 k6 측정 결과 |
| [Troubleshooting](./docs/TROUBLESHOOTING.md) | 동시 체크인 문제의 분석과 해결 |
| [Deployment](./docs/DEPLOYMENT.md) | Docker 및 AWS 배포 구성 |
