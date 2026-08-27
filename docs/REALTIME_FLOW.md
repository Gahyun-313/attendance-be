# ⚡ Realtime Attendance Flow

## 🎯 목적

학생의 체크인 결과를 관리자가 새로고침하지 않아도 확인할 수 있도록 STOMP WebSocket을 사용합니다.

## 🔄 이벤트 흐름

```text
AttendanceService
  ↓ ApplicationEvent 발행
DB Transaction Commit
  ↓ @TransactionalEventListener(AFTER_COMMIT)
최신 출석 기록 + 대시보드 집계 생성
  ↓
/topic/attendance/{sessionId}
  ↓
React 관리자 화면 갱신
```

이벤트는 트랜잭션 커밋 이후에만 전달합니다. DB 반영에 실패한 데이터를 관리자가 먼저 보거나, 롤백된 결과가 화면에 남는 문제를 방지하기 위한 선택입니다.

## 🔐 연결과 구독 인가

- WebSocket 연결 시 JWT를 전달해 사용자를 식별합니다.
- `StompChannelInterceptor`에서 `SUBSCRIBE` 명령을 확인합니다.
- 출석 현황 구독은 ADMIN 역할만 허용합니다.
- Destination은 세션 ID 단위로 분리합니다.

## 📦 전달 데이터

관리자 화면이 별도 API를 즉시 재호출하지 않아도 갱신할 수 있도록 다음 정보를 함께 전달합니다.

- 방금 처리된 출석 기록
- 해당 세션의 최신 출석 집계

## ⚠️ 현재 한계와 확장 방향

현재 Spring의 인메모리 SimpleBroker를 사용합니다. 단일 서버에서는 충분하지만 서버를 여러 대로 확장하면 연결과 메시지가 인스턴스별로 나뉩니다. 수평 확장이 필요해질 경우 RabbitMQ STOMP Broker Relay 또는 Redis Pub/Sub 같은 외부 브로커를 검토해야 합니다.
