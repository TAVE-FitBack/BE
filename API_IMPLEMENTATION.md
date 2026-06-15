# Fitback 백엔드 API 구현 설명

## 개요

이 백엔드는 `api_spec (1).html`에 정의된 55개 HTTP API를 구현합니다.
인증, 테넌트 격리, 매장 관리, 고객 관리, AI 상담 분석, 후속 관리 메시지,
이벤트, 이용권 등록, 대시보드 및 리포트 기능을 제공합니다.

기본 API 경로:

```text
/api/v1
```

## 아키텍처

현재 구현은 다음과 같은 계층형 DDD 기반 구조를 사용합니다.

```text
core/
  application/
    port/             애플리케이션이 소유하는 저장소 및 AI 인터페이스
  infrastructure/    테넌트 저장소 및 FastAPI/LLM 어댑터
  presentation/      REST API 어댑터
global/
  config/            Spring Security 설정
  error/             공통 API 예외 처리
  security/          JWT, 테넌트 식별, 웹훅 보안
```

애플리케이션 서비스는 구체적인 인프라 구현이 아닌 포트 인터페이스에 의존합니다.
테넌트 소유 데이터는 서명된 JWT의 `storeId` 클레임을 기준으로 격리됩니다.

Identity, CRM, Engagement, Campaign, Conversion, Insight 등 명시적인 바운디드
컨텍스트 패키지로 세분화하는 작업은 GitHub 이슈 `#6`에서 관리합니다.

## 구현된 도메인

- 인증: 회원가입, 로그인, Refresh Token 회전, 로그아웃, 비밀번호 재설정
- 매장: 매장 정보, 서비스, 설정
- 고객: 생성, 상세 조회, 수정, 검색 및 필터, 페이지네이션, Soft Delete
- 상담: 상담 기록, AI 분석, 관심 서비스, 미전환 사유
- 후속 관리: 상태 변경, 미루기, 수정, 연락 결과
- 메시지: AI 초안 생성, 수정, 복사, 발송, 서명된 발송 결과 콜백
- 이벤트: 이벤트 생성, 대상 고객 자동 선정, 대상 발송 상태
- 이용권: 등록 이력 및 상태 변경
- 대시보드 및 리포트

## 보안

- 모든 비즈니스 API는 서명된 Bearer JWT를 요구합니다.
- JWT Access Token은 `storeId` 테넌트 클레임을 포함합니다.
- 저장소 작업은 인증된 테넌트 범위로 제한됩니다.
- Refresh Token은 만료 시간을 가지며, 사용 시 회전되고 재사용이 거부됩니다.
- 메시지 발송 결과 콜백은 `X-Webhook-Signature` 헤더를 요구합니다.
- `JWT_SECRET`이 설정되지 않은 경우 애플리케이션 시작에 실패합니다.

## AI 연동

AI 기능은 `AiAnalysisPort` 인터페이스를 통해 호출됩니다.

`API_KEY_CODE`와 `AI_BASE_URL`이 설정된 경우 다음 FastAPI 엔드포인트를
호출합니다.

```text
POST {AI_BASE_URL}/analyze
POST {AI_BASE_URL}/messages/generate
```

AI 제공자 호출 실패 시 `502 Bad Gateway`를 반환합니다. 결정론적 로컬
Fallback은 기본적으로 비활성화되어 있으며 테스트 또는 로컬 개발 환경에서만
명시적으로 활성화할 수 있습니다.

## 환경변수

`.env.example`을 참고하여 런타임 환경변수를 설정합니다.

```dotenv
API_KEY_CODE=
AI_BASE_URL=http://localhost:8000
JWT_SECRET=replace-with-at-least-32-byte-secret
WEBHOOK_SECRET=replace-with-provider-webhook-secret
```

`application.yaml`에서 사용하는 데이터베이스 환경변수:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/fitback
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

## 실행 방법

Windows:

```powershell
.\gradlew.bat bootRun
```

기본 런타임 설정에서는 PostgreSQL과 `JWT_SECRET`이 필요합니다.

## 테스트 및 빌드

전체 검증 명령어:

```powershell
.\gradlew.bat clean build --console=plain
```

테스트에서는 다음 항목을 검증합니다.

- 명세에 정의된 모든 HTTP Method 및 경로 매핑
- 핵심 고객 관리 흐름
- JWT 인증 및 테넌트 데이터 격리
- Refresh Token 회전 및 재사용 거부
- AI 정상 처리 및 실패 경계
- 관심 서비스 및 미전환 사유 교체 불변식
- 고객 등록 전환 및 이용권 생성
- 고객 필터링 및 Soft Delete
- 외부 제공자 메시지 ID와 서명된 발송 결과 콜백 처리

## 현재 영속성 어댑터

현재 API는 `TenantDataRepository`의 인메모리 구현을 사용합니다.
PostgreSQL/JPA 어댑터와 마이그레이션 도입은 GitHub 이슈 `#5`에서 관리합니다.
해당 전환 과정에서도 애플리케이션 유스케이스 계약은 변경하지 않는 것을
목표로 합니다.

## 관련 이슈

- `#3`: API 명세 구현
- `#4`: JWT 테넌트 격리 및 웹훅 보안
- `#5`: PostgreSQL 영속성 및 마이그레이션
- `#6`: 명시적인 바운디드 컨텍스트 분리

