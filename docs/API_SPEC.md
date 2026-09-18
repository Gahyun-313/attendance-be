# 출석하자 API 명세서

이 문서는 `attendance-be` 서버 코드 기준으로 정리한 REST API 명세입니다.

## 기본 정보

- Base URL: 배포 환경에 맞게 설정
- Content-Type: `application/json`
- 인증 방식: JWT Bearer Token
- 인증 헤더: `Authorization: Bearer {accessToken}`
- 날짜/시간 형식: ISO-8601 문자열 예) `2026-09-18T09:00:00`

## 공통 응답 형식

### 성공 응답

```json
{
  "success": true,
  "data": {},
  "message": "요청 처리 메시지"
}
```

데이터가 없는 성공 응답은 `data`가 `null`입니다.

### 에러 응답

```json
{
  "code": "C002",
  "message": "입력값이 올바르지 않습니다",
  "status": 400,
  "timestamp": "2026-09-18T09:00:00",
  "error": [
    {
      "field": "username",
      "value": "",
      "reason": "아이디는 필수입니다"
    }
  ]
}
```

## 인증/인가 규칙

- `POST /api/auth/login`, `POST /api/auth/refresh`는 인증 없이 호출합니다.
- `POST /api/auth/oauth/**`, `POST /api/auth/join/email/**`, `POST /api/auth/password-reset/**`는 인증 없이 호출합니다.
- 그 외 API는 기본적으로 JWT 인증이 필요합니다.
- `ADMIN 전용` API는 관리자 권한이 필요합니다.
- `STUDENT 전용` API는 학생 권한이 필요합니다.

## 페이지네이션

Spring Pageable을 사용하는 목록 API는 다음 쿼리 파라미터를 사용할 수 있습니다.

| 파라미터 | 설명 | 예시 |
| --- | --- | --- |
| `page` | 페이지 번호, 0부터 시작 | `0` |
| `size` | 페이지 크기 | `20` |
| `sort` | 정렬 기준 | `createdAt,desc` |

## Enum

| 이름 | 값 |
| --- | --- |
| `UserRole` | `STUDENT`, `ADMIN` |
| `AttendanceStatus` | `WAITING`, `PRESENT`, `LATE`, `ABSENT` |
| `SessionStatus` | `SCHEDULED`, `ACTIVE`, `COMPLETED`, `CANCELED` |
| `NfcTagStatus` | `ACTIVE`, `INACTIVE`, `LOST`, `DAMAGED` |
| `NotificationStatus` | `SCHEDULED`, `SENT`, `FAILED`, `CANCELED` |

## 인증 API

### 로그인

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/login` |
| 인증 | 불필요 |

Request

```json
{
  "username": "20260001",
  "password": "password123"
}
```

Response `data`

```json
{
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "username": "20260001",
    "name": "홍길동",
    "role": "STUDENT",
    "passwordChanged": false
  }
}
```

### 토큰 갱신

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/refresh` |
| 인증 | 불필요 |

Request

```json
{
  "refreshToken": "refresh-token"
}
```

Response `data`

```json
{
  "accessToken": "new-access-token",
  "refreshToken": null,
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": null
}
```

### 로그아웃

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/logout` |
| 인증 | 필요 |

Response: 데이터 없음

### 소셜 로그인

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/oauth/{provider}` |
| 인증 | 불필요 |
| Path | `provider`: `google` 또는 `kakao` |

Request

```json
{
  "token": "provider-token",
  "organizationCode": "ORG001"
}
```

기존 연동 계정이면 `organizationCode` 없이 로그인할 수 있고, 신규 가입이면 단체 코드가 필요합니다.

### 이메일 조인 인증 코드 요청

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/join/email/request` |
| 인증 | 불필요 |

Request

```json
{
  "organizationCode": "ORG001",
  "email": "admin@example.com"
}
```

### 이메일 조인 인증 및 계정 생성

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/join/email/verify` |
| 인증 | 불필요 |

Request

```json
{
  "email": "admin@example.com",
  "code": "123456",
  "password": "password123",
  "name": "관리자"
}
```

Response `data`: 로그인 응답과 동일

### 비밀번호 재설정 코드 요청

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/password-reset/request` |
| 인증 | 불필요 |

Request

```json
{
  "email": "user@example.com"
}
```

### 비밀번호 재설정

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/auth/password-reset/verify` |
| 인증 | 불필요 |

Request

```json
{
  "email": "user@example.com",
  "code": "123456",
  "newPassword": "newPassword123"
}
```

## 사용자 API

### 학생 계정 생성

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/users` |
| 권한 | ADMIN |

Request

```json
{
  "username": "20260001",
  "password": "password123",
  "email": "student@example.com",
  "name": "홍길동",
  "groupName": "A반",
  "note": "관리자 메모"
}
```

Response `data`: `UserResponse`

### 사용자 목록 조회

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/users` |
| 권한 | ADMIN |
| Query | `groupName`, `keyword`, `page`, `size`, `sort` |

Response `data`: `Page<UserResponse>`

### 사용자 그룹명 목록 조회

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/users/groups` |
| 권한 | ADMIN |

Response `data`: `string[]`

### 사용자 대시보드 조회

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/users/dashboard` |
| 권한 | ADMIN |

Response `data`

```json
{
  "totalUsers": 100,
  "activateUsers": 95,
  "averageAttendanceRate": 92.5,
  "newUsersThisMonth": 10
}
```

### 사용자 상세 조회

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/users/{userId}` |
| 권한 | ADMIN |

Response `data`: `UserResponse`

### 사용자 정보 수정

| 항목 | 내용 |
| --- | --- |
| Method | `PUT` |
| URL | `/api/users/{userId}` |
| 권한 | ADMIN |

Request

```json
{
  "email": "student@example.com",
  "name": "홍길동",
  "groupName": "A반",
  "note": "수정 메모"
}
```

### 사용자 비활성화

| 항목 | 내용 |
| --- | --- |
| Method | `DELETE` |
| URL | `/api/users/{userId}` |
| 권한 | ADMIN |

### 사용자 재활성화

| 항목 | 내용 |
| --- | --- |
| Method | `POST` |
| URL | `/api/users/{userId}/activate` |
| 권한 | ADMIN |

### 내 정보 조회

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/users/me` |
| 인증 | 필요 |

Response `data`: `UserResponse`

### 내 비밀번호 변경

| 항목 | 내용 |
| --- | --- |
| Method | `PATCH` |
| URL | `/api/users/me/password` |
| 인증 | 필요 |

Request

```json
{
  "currentPassword": "password123",
  "newPassword": "newPassword123"
}
```

### UserResponse

```json
{
  "id": 1,
  "username": "20260001",
  "email": "student@example.com",
  "name": "홍길동",
  "role": "STUDENT",
  "groupName": "A반",
  "note": "관리자 메모",
  "passwordChanged": false,
  "active": true,
  "firstAttendanceAt": "2026-09-18T09:00:00",
  "createdAt": "2026-09-18T09:00:00",
  "updatedAt": "2026-09-18T09:00:00"
}
```

## 그룹 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| 그룹 생성 | `POST` | `/api/groups` | ADMIN |
| 그룹 목록 조회 | `GET` | `/api/groups` | ADMIN |
| 그룹 수정 | `PUT` | `/api/groups/{groupId}` | ADMIN |
| 그룹 삭제 | `DELETE` | `/api/groups/{groupId}` | ADMIN |

Request

```json
{
  "name": "A반",
  "description": "A반 설명"
}
```

Response `data`

```json
{
  "id": 1,
  "name": "A반",
  "description": "A반 설명",
  "createdAt": "2026-09-18T09:00:00",
  "updatedAt": "2026-09-18T09:00:00"
}
```

## 출석 세션 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| 세션 생성 | `POST` | `/api/sessions` | ADMIN |
| 세션 목록 조회 | `GET` | `/api/sessions` | 인증 필요 |
| 세션 상세 조회 | `GET` | `/api/sessions/{sessionId}` | 인증 필요 |
| 세션 수정 | `PUT` | `/api/sessions/{sessionId}` | ADMIN |
| 세션 삭제 | `DELETE` | `/api/sessions/{sessionId}` | ADMIN |
| 세션 시작 | `POST` | `/api/sessions/{sessionId}/start` | ADMIN |
| 세션 종료 | `POST` | `/api/sessions/{sessionId}/close` | ADMIN |
| 세션 취소 | `POST` | `/api/sessions/{sessionId}/cancel` | ADMIN |
| 활성 세션 목록 조회 | `GET` | `/api/sessions/active` | 인증 필요 |

목록 조회 Query: `status`, `keyword`, `page`, `size`, `sort`

Request

```json
{
  "title": "1교시 출석",
  "description": "강의실 입실 출석",
  "groupName": "A반",
  "sessionDate": "2026-09-18",
  "startTime": "2026-09-18T09:00:00",
  "endTime": "2026-09-18T10:00:00",
  "lateThresholdMinutes": 10,
  "location": "101호",
  "nfcTagId": 1,
  "note": "비고"
}
```

Response `data`

```json
{
  "id": 1,
  "title": "1교시 출석",
  "description": "강의실 입실 출석",
  "groupName": "A반",
  "sessionDate": "2026-09-18",
  "startTime": "2026-09-18T09:00:00",
  "endTime": "2026-09-18T10:00:00",
  "lateThresholdMinutes": 10,
  "location": "101호",
  "status": "SCHEDULED",
  "nfcTag": {
    "id": 1,
    "uid": "04AABBCCDD",
    "name": "101호 태그",
    "location": "101호"
  },
  "note": "비고",
  "createdBy": 1,
  "createdAt": "2026-09-18T09:00:00",
  "updatedAt": "2026-09-18T09:00:00"
}
```

## 출석 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| 출석 체크인 | `POST` | `/api/attendances/check-in` | STUDENT |
| 내 출석 기록 조회 | `GET` | `/api/attendances/me` | STUDENT |
| 세션별 출석 현황 조회 | `GET` | `/api/attendances/sessions/{sessionId}` | ADMIN |
| 출석 상태 수정 | `PUT` | `/api/attendances/{attendanceId}/status` | ADMIN |
| 출석 기록 삭제 | `DELETE` | `/api/attendances/{attendanceId}` | ADMIN |
| 세션별 출석 대시보드 조회 | `GET` | `/api/attendances/sessions/{sessionId}/dashboard` | ADMIN |
| 최근 체크인 기록 조회 | `GET` | `/api/attendances/recent` | ADMIN |

최근 체크인 기록 Query: `limit`, 기본값 `10`, 범위 `1~50`

체크인 Request

```json
{
  "nfcTagUid": "04AABBCCDD"
}
```

출석 상태 수정 Request

```json
{
  "status": "PRESENT",
  "modifyReason": "관리자 수동 수정"
}
```

AttendanceResponse

```json
{
  "id": 1,
  "userId": 1,
  "userName": "홍길동",
  "studentId": "20260001",
  "groupName": "A반",
  "sessionId": 1,
  "status": "PRESENT",
  "checkInTime": "2026-09-18T09:05:00",
  "nfcTagUid": "04AABBCCDD",
  "nfcLocation": "101호",
  "modifiedBy": null,
  "modifyReason": null,
  "note": null,
  "createdAt": "2026-09-18T09:05:00",
  "updatedAt": "2026-09-18T09:05:00"
}
```

AttendanceDashboardResponse

```json
{
  "sessionId": 1,
  "targetCount": 30,
  "totalRecords": 30,
  "present": 25,
  "late": 3,
  "absent": 2,
  "waiting": 0,
  "attendanceRate": 93.3
}
```

## NFC 태그 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| NFC 태그 등록 | `POST` | `/api/nfc-tags` | ADMIN |
| NFC 태그 목록 조회 | `GET` | `/api/nfc-tags` | 인증 필요 |
| NFC 태그 상세 조회 | `GET` | `/api/nfc-tags/{tagId}` | 인증 필요 |
| UID로 NFC 태그 조회 | `GET` | `/api/nfc-tags/uid/{uid}` | 인증 필요 |
| NFC 태그 수정 | `PUT` | `/api/nfc-tags/{tagId}` | ADMIN |
| NFC 태그 활성화 | `POST` | `/api/nfc-tags/{tagId}/activate` | ADMIN |
| NFC 태그 비활성화 | `POST` | `/api/nfc-tags/{tagId}/deactivate` | ADMIN |
| NFC 태그 삭제 | `DELETE` | `/api/nfc-tags/{tagId}` | ADMIN |
| NFC 태그 검증 | `POST` | `/api/nfc-tags/validate?uid={uid}` | 인증 필요 |

목록 조회 Query: `status`, `page`, `size`, `sort`

등록 Request

```json
{
  "uid": "04AABBCCDD",
  "name": "101호 태그",
  "description": "101호 입구",
  "location": "101호"
}
```

수정 Request

```json
{
  "name": "101호 태그",
  "description": "수정된 설명",
  "location": "101호"
}
```

Response `data`

```json
{
  "id": 1,
  "uid": "04AABBCCDD",
  "name": "101호 태그",
  "description": "101호 입구",
  "location": "101호",
  "status": "ACTIVE",
  "lastUsedAt": null,
  "createdAt": "2026-09-18T09:00:00",
  "updatedAt": "2026-09-18T09:00:00"
}
```

검증 Response `data`: `true` 또는 `false`

## 알림 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| 알림 생성 | `POST` | `/api/notifications` | ADMIN |
| 알림 목록 조회 | `GET` | `/api/notifications` | 인증 필요 |
| 알림 취소 | `DELETE` | `/api/notifications/{notificationId}` | ADMIN |

목록 조회 Query: `status`, `page`, `size`, `sort`

Request

```json
{
  "title": "출석 안내",
  "content": "출석 체크를 진행해주세요.",
  "targetGroup": "A반",
  "scheduledAt": "2026-09-18T09:00:00"
}
```

Response `data`

```json
{
  "id": 1,
  "title": "출석 안내",
  "content": "출석 체크를 진행해주세요.",
  "targetGroup": "A반",
  "status": "SCHEDULED",
  "scheduledAt": "2026-09-18T09:00:00",
  "sentAt": null,
  "targetCount": 30,
  "createdBy": 1,
  "createdAt": "2026-09-18T09:00:00"
}
```

## 통계 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| 전체 통계 조회 | `GET` | `/api/statistics/overall` | ADMIN |
| 대시보드 통계 조회 | `GET` | `/api/statistics/dashboard` | ADMIN |
| 출석률 랭킹 조회 | `GET` | `/api/statistics/ranking` | ADMIN |
| 특정 사용자 통계 조회 | `GET` | `/api/statistics/users/{userId}` | ADMIN |
| 내 통계 조회 | `GET` | `/api/statistics/me` | 인증 필요 |

랭킹 조회 Query: `limit`, 기본값 `5`, 범위 `1~50`

OverallStatisticsResponse

```json
{
  "totalStudents": 100,
  "totalSessions": 20,
  "totalCompletedSessions": 18,
  "totalPresent": 1500,
  "totalLate": 100,
  "totalAbsent": 200,
  "totalWaiting": 0,
  "overallAttendanceRate": 88.9
}
```

DashboardStatisticsResponse

```json
{
  "todaySessionCount": 3,
  "activeSessionCount": 1,
  "recentAttendanceRate": 91.2,
  "groupAttendanceRates": [
    {
      "groupName": "A반",
      "targetCount": 30,
      "presentCount": 25,
      "lateCount": 3,
      "absentCount": 2,
      "attendanceRate": 93.3
    }
  ],
  "todayAttendanceRate": 90.0,
  "todayPresentCount": 25,
  "todayLateCount": 2,
  "todayAbsentCount": 1,
  "todayWaitingCount": 2,
  "hourlyCheckInTrend": [
    {
      "hour": 9,
      "count": 25
    }
  ]
}
```

AttendanceRankingResponse

```json
{
  "topRanking": [
    {
      "userId": 1,
      "name": "홍길동",
      "groupName": "A반",
      "attendanceRate": 100.0,
      "totalRecords": 10
    }
  ],
  "bottomRanking": []
}
```

UserStatisticsResponse

```json
{
  "userId": 1,
  "name": "홍길동",
  "groupName": "A반",
  "totalRecords": 10,
  "presentCount": 8,
  "lateCount": 1,
  "absentCount": 1,
  "attendanceRate": 90.0,
  "recentRecords": []
}
```

## FCM 토큰 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| FCM 토큰 등록 | `POST` | `/api/fcm/tokens` | 인증 필요 |
| FCM 토큰 삭제 | `DELETE` | `/api/fcm/tokens` | 인증 필요 |

등록 Request

```json
{
  "token": "fcm-token",
  "deviceType": "AOS"
}
```

삭제 Request

```json
{
  "token": "fcm-token"
}
```

Response `data`

```json
{
  "id": 1,
  "token": "fcm-token",
  "deviceType": "AOS",
  "createdAt": "2026-09-18T09:00:00",
  "updatedAt": "2026-09-18T09:00:00"
}
```

## 단체 API

| 기능 | Method | URL | 권한 |
| --- | --- | --- | --- |
| 내 단체 정보 조회 | `GET` | `/api/organizations/me` | ADMIN |
| 내 단체 정보 수정 | `PUT` | `/api/organizations/me` | ADMIN |

수정 Request

```json
{
  "name": "출석하자 교육원",
  "autoAbsentEnabled": true,
  "nfcLocationValidationEnabled": true,
  "defaultAttendanceGraceMinutes": 10,
  "defaultLateThresholdMinutes": 10
}
```

Response `data`

```json
{
  "id": 1,
  "name": "출석하자 교육원",
  "code": "ORG001",
  "active": true,
  "autoAbsentEnabled": true,
  "nfcLocationValidationEnabled": true,
  "defaultAttendanceGraceMinutes": 10,
  "defaultLateThresholdMinutes": 10,
  "createdAt": "2026-09-18T09:00:00",
  "updatedAt": "2026-09-18T09:00:00"
}
```

## WebSocket 참고

- WebSocket 경로: `/ws/**`
- 브라우저 WebSocket은 `Authorization` 헤더를 보내기 어렵기 때문에 서버 설정에서는 `/ws/**`를 허용하고, 별도 핸드셰이크 인터셉터에서 쿼리 파라미터 토큰을 검증합니다.
- 출석 체크인 이벤트 페이로드는 `AttendanceCheckInPush` 구조를 사용합니다.

```json
{
  "sessionId": 1,
  "record": {},
  "dashboard": {}
}
```

## 주요 에러 코드

| 코드 | HTTP 상태 | 메시지 |
| --- | --- | --- |
| `C001` | 500 | 내부 서버 오류가 발생했습니다 |
| `C002` | 400 | 입력값이 올바르지 않습니다 |
| `C003` | 405 | 허용되지 않은 HTTP 메서드입니다 |
| `C004` | 403 | 접근 권한이 없습니다 |
| `C005` | 404 | 요청한 리소스를 찾을 수 없습니다 |
| `A001` | 401 | 인증이 필요합니다 |
| `A002` | 401 | 유효하지 않은 토큰입니다 |
| `A003` | 401 | 만료된 토큰입니다 |
| `A004` | 401 | Refresh Token을 찾을 수 없습니다 |
| `A005` | 401 | 아이디 또는 비밀번호가 일치하지 않습니다 |
| `U001` | 404 | 사용자를 찾을 수 없습니다 |
| `U002` | 409 | 이미 존재하는 아이디입니다 |
| `U003` | 409 | 이미 존재하는 이메일입니다 |
| `U004` | 400 | 비활성화된 사용자입니다 |
| `U005` | 400 | 현재 비밀번호가 일치하지 않습니다 |
| `N001` | 404 | NFC 태그를 찾을 수 없습니다 |
| `N002` | 409 | 이미 등록된 NFC UID입니다 |
| `N003` | 400 | 비활성화된 NFC 태그입니다 |
| `S001` | 404 | 출석 세션을 찾을 수 없습니다 |
| `S002` | 400 | 활성화된 세션이 아닙니다 |
| `S003` | 400 | 이미 종료된 세션입니다 |
| `S004` | 409 | 해당 NFC 태그가 이미 다른 활성 세션에서 사용 중입니다 |
| `S005` | 400 | 세션이 시작된 이후에는 대상 그룹을 변경할 수 없습니다 |
| `AT001` | 404 | 출석 기록을 찾을 수 없습니다 |
| `AT002` | 409 | 이미 출석 처리되었습니다 |
| `AT003` | 400 | 출석 가능 시간이 아닙니다 |
| `AT004` | 409 | 이미 처리 중인 체크인 요청입니다. 잠시 후 다시 시도해주세요 |
| `AT005` | 400 | 체크인 위치가 세션 위치와 일치하지 않습니다 |
| `F001` | 404 | FCM 토큰을 찾을 수 없습니다 |
| `NT001` | 404 | 알림을 찾을 수 없습니다 |
| `NT002` | 400 | 이미 처리된 알림은 취소할 수 없습니다 |
| `O001` | 400 | 유효하지 않은 단체 코드입니다 |
| `O002` | 400 | 신규 가입 시 단체 코드가 필요합니다 |
| `O003` | 400 | 지원하지 않는 소셜 로그인 제공자입니다 |
| `O004` | 401 | 소셜 로그인 인증에 실패했습니다 |
| `O005` | 400 | 인증 코드가 만료되었거나 요청되지 않았습니다 |
| `O006` | 400 | 인증 코드가 일치하지 않습니다 |
| `O007` | 400 | 소셜 로그인 전용 계정은 비밀번호 재설정 대상이 아닙니다 |
| `0008` | 404 | 단체 정보를 찾을 수 없습니다. |
| `G001` | 404 | 그룹을 찾을 수 없습니다 |
| `G002` | 409 | 이미 존재하는 그룹명입니다 |
