# 🏗️ Architecture

## 🧭 전체 구성

```text
Android Student App ─┐
                     ├─ REST API ─ Spring Boot ─ MySQL
React Admin Web ─────┤                 │
                     └─ STOMP ─────────┴─ Redis
                                          ├─ Cache
                                          └─ Distributed Lock
```

Spring Boot 서버가 인증과 비즈니스 규칙을 판단하며, MySQL은 영속 데이터, Redis는 조회 캐시와 출석 체크 분산 락을 담당합니다. 관리자 Web은 REST API로 데이터를 관리하고 STOMP 구독으로 출석 이벤트를 실시간 수신합니다.

## 🗂️ 서버 구조

도메인별 Controller–Service–Repository 구조를 사용합니다.

```text
com.attendance
├── domain
│   ├── attendance
│   ├── session
│   ├── nfc
│   ├── user
│   ├── notification
│   ├── statistics
│   └── organization
└── global
    ├── config
    ├── exception
    ├── response
    └── security
```

- Controller: 요청 검증, 인증 사용자 전달, 응답 변환
- Service: 출석 상태 전이, 조직 소속 검증 등 비즈니스 규칙
- Repository: JPA 기반 조회와 영속화
- Global: 인증 필터, 공통 예외와 응답 형식, 애플리케이션 설정

## 🔐 인증과 인가

Spring Security를 Stateless로 구성하고 Access Token과 Refresh Token을 분리했습니다.

- Access Token: 1시간
- Refresh Token: 7일, 서버에 저장해 로그아웃 시 제거
- 역할: `ADMIN`, `STUDENT`
- 관리자: 기존 계정 로그인 또는 조직 코드 기반 Google·Kakao/이메일 인증 가입
- 학생: 관리자가 생성한 계정으로 로그인

관리 API는 역할뿐 아니라 요청 데이터의 조직 소속까지 함께 검증합니다.

## 🏢 멀티테넌시

하나의 스키마를 공유하고 주요 데이터에 `organizationId`를 저장하는 방식을 선택했습니다.

- 목록 조회는 인증 사용자의 조직 ID로 필터링
- ID 기반 조회·수정·삭제도 소속 조직을 함께 검증
- 다른 조직의 리소스는 존재 여부가 노출되지 않도록 404 처리
- 통계 쿼리와 Redis 캐시 키도 조직별로 분리
- 출석 기록처럼 조직 ID가 직접 없는 데이터는 세션 소속을 통해 간접 검증

규모와 프로젝트 범위에 비해 별도 스키마·DB 방식은 운영 복잡성이 크다고 판단해 공유 스키마 방식을 사용했습니다.

## 🔄 상태 모델

출석 세션은 `SCHEDULED → ACTIVE → COMPLETED`의 정상 흐름을 가지며 취소 상태를 별도로 둡니다. 출석 기록은 `WAITING`, `PRESENT`, `LATE`, `ABSENT`로 관리합니다.

허용된 전이는 도메인 로직에서 제한하고, 클라이언트가 임의의 상태를 확정하지 못하게 했습니다.

## ⚡ 캐시와 일관성

- 세션 출석 현황: TTL 5초, 출석 변경 시 명시적 무효화
- 전체·대시보드 통계: TTL 1분
- 캐시 대상 DTO는 Redis JSON 역직렬화를 고려해 구성
- 테스트에서는 캐시를 비활성화해 DB 비즈니스 흐름과 Redis 가용성을 분리

## ⚠️ 현재 한계

- 인메모리 SimpleBroker를 사용하므로 서버 수평 확장 시 외부 메시지 브로커가 필요합니다.
- JWT 인증 필터가 요청마다 사용자 정보를 DB에서 조회합니다.
- Refresh Token Rotation은 적용하지 않았습니다.
- 예약 알림을 지정 시각에 자동으로 발송하는 스케줄러는 아직 구현하지 않았습니다. (즉시 발송은 Firebase Admin SDK로 실제 전송됩니다.)
- Nginx와 HTTPS는 아직 적용하지 않았습니다.
