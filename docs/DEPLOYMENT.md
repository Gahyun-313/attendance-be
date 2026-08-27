# 🚀 Deployment

## 💻 로컬 구성

```text
Docker Compose
├── Spring Boot App
├── MySQL
└── Redis
```

- 멀티스테이지 Dockerfile로 Gradle 빌드와 JRE 실행 이미지를 분리했습니다.
- MySQL과 Redis health check를 통과한 뒤 애플리케이션이 시작되도록 구성했습니다.
- MySQL 데이터는 Named Volume에 보존하고 최초 실행 시 스키마를 초기화합니다.
- 실제 환경값은 `.env`에 두고 저장소에는 `.env.example`만 제공합니다.

## ☁️ AWS 구성

```text
Client
  ↓ HTTP :8080
AWS EC2
├── Spring Boot Container
└── Redis Container
      ↓ private security-group connection
AWS RDS for MySQL
```

운영 환경에서는 MySQL을 RDS로 분리하고 EC2에는 애플리케이션과 Redis를 실행했습니다. RDS는 외부에 공개하지 않고 EC2 보안 그룹에서만 접근하도록 구성했습니다.

## ⚙️ 운영 설정

- DB URL·계정, Redis 주소, JWT Secret은 환경변수로 주입
- `ddl-auto: validate`로 애플리케이션이 운영 스키마를 임의 변경하지 않도록 설정
- Docker 이미지 기동 후 로그인 API와 관리자 Web의 실제 연동 검증
- 민감정보와 실제 서버 주소는 Git 추적 대상에서 제외

## 🔧 배포 과정에서 해결한 문제

1. Linux 환경에서 Gradle Wrapper 실행 권한이 없어 Docker 빌드가 실패했습니다. Dockerfile에서 권한을 명시했습니다.
2. 짧은 JWT Secret으로 HS256 초기화가 실패했습니다. 32바이트 이상의 랜덤 값을 환경변수로 교체했습니다.
3. EC2에서 RDS 연결이 되도록 인스턴스 IP가 아니라 보안 그룹 간 인바운드 규칙을 구성했습니다.
4. 소형 인스턴스에서 빌드 중 메모리 압박이 발생해 배포 단계의 리소스 사용을 점검했습니다.

## 📌 현재 범위

EC2와 RDS 배포 및 API 연동까지 완료했습니다. Nginx 리버스 프록시, 도메인, HTTPS는 아직 적용하지 않았습니다.
