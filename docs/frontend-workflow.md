# Fitback 프론트엔드 워크플로우 (tmpFE)

> 소스: `tmpFE/src/main.jsx` (단일 파일 SPA, React + Vite). 모든 API는 `API_BASE = /api/v1`을 프록시하여 백엔드(`FitbackApiController`, `AuthController`)를 호출한다.

## 1. 전체 흐름

```
[AuthScreen] --로그인/회원가입 성공--> [App Shell]
                                         ├─ Sidebar Nav (6개 화면 전환, SPA 상태값만 변경 — 라우팅 없음)
                                         └─ Workspace
                                             ├─ DashboardView
                                             ├─ ConsultationView ──(저장 성공, 자동 전환)──> CustomerView
                                             ├─ CustomerView
                                             ├─ FollowupView ──(메시지 초안 생성)──> MessageModal (오버레이)
                                             ├─ EventsView
                                             └─ StoreView
```

세션은 `localStorage["fitback.session"]`에 `{accessToken, refreshToken, user}`로 저장되며, 토큰 보유 여부로 AuthScreen ↔ App Shell을 분기한다. 토큰이 생기면 `useEffect`가 고객/서비스/후속관리/대시보드/매장/이벤트 데이터를 한 번에 프리로드한다.

## 2. 화면별 역할 및 호출 API

### 2.0 AuthScreen (로그인 진입점)
- **역할**: 로그인/계정 생성 탭 전환 폼. 세션이 없을 때만 렌더링.
- **API**
  - `POST /api/v1/auth/login` — `{email, password}` → `{accessToken, refreshToken, user}`
  - `POST /api/v1/auth/register` — `{email, password, passwordConfirm, nickname, agreeTerms, agreeMarketing}` → 성공 후 자동으로 로그인 호출

### 2.1 DashboardView — "대시보드"
- **역할**: 오늘의 운영 현황 요약 + 우선 관리 고객 미리보기.
- **API**
  - `GET /api/v1/dashboard/summary` → 오늘 상담 수, 월간 등록 전환율, 대기/지연 후속관리 수
  - `GET /api/v1/dashboard/priority-customers` → 우선순위 고객 목록(점수 포함)

### 2.2 ConsultationView — "상담 등록" (기본 진입 화면)
- **역할**: 신규 상담 메모 입력 → 중복 확인 → AI 미리보기 → 저장까지의 3단계 등록 플로우.
- **API**
  - `GET /api/v1/consultations/check-duplicate?phoneNum=&name=` — 중복 고객 여부 확인
  - `POST /api/v1/consultations/analyze-preview` — `{name, phoneNum, serviceIds, inflowPath, rawText}` → AI 분석 결과(저장하지 않는 미리보기, `stateless:true`)
  - `POST /api/v1/consultations` — `{...form, aiResult: analysis}` → 고객/상담 레코드 실제 생성, 성공 시 "고객 인사이트" 화면으로 자동 전환
  - 보조: `GET /api/v1/store/services` (서비스 선택 칩 목록, 앱 시작 시 1회 로드)

### 2.3 CustomerView — "고객 인사이트"
- **역할**: 좌측 고객 목록 + 우측 선택 고객의 AI 인사이트 상세(온도, 미등록 사유, 추천 행동, 신호, 상담 타임라인).
- **API**: 별도 호출 없음 — App이 미리 로드한 `customers` state를 그대로 사용 (`GET /api/v1/customers`는 App 레벨에서 로드).
- 비고: `normalizeCustomer()`가 `aiInsight.leadTemperature/priorityScore/nextBestAction` 등을 평탄화해서 화면에 공급.

### 2.4 FollowupView — "후속관리"
- **역할**: `DONE`이 아닌 후속관리 대상을 우선순위 점수 내림차순으로 정렬해 "오늘 연락할 고객" 큐로 표시. 항목별로 메시지 초안 생성 트리거.
- **API**: 표시 자체는 App이 미리 로드한 `followUps`(`GET /api/v1/follow-ups`)와 `customers`를 조인해서 사용.
- **메시지 초안 생성** → `openMessageModal`에서:
  - `POST /api/v1/follow-ups/{id}/messages/generate` → 초안 메시지 목록 생성, `MessageModal` 오픈

### 2.5 EventsView — "이벤트 관리"
- **역할**: 특정 서비스를 대상으로 하는 이벤트 생성 폼 + 생성된 이벤트 히스토리.
- **API**
  - `GET /api/v1/store/events` (App 레벨 프리로드)
  - `POST /api/v1/store/events` — `{name, serviceId}` → 이벤트 생성

### 2.6 StoreView — "매장 관리"
- **역할**: 매장 프로필(이름/유형) 등록·수정 + 제공 서비스 목록 조회/추가.
- **API**
  - `GET /api/v1/store` (App 레벨 프리로드, 비어있으면 `null`)
  - `POST /api/v1/store` (최초 등록) / `PUT /api/v1/store` (이미 있으면 수정) — `{name, storeType}`
  - `GET /api/v1/store/services` (App 레벨 프리로드)
  - `POST /api/v1/store/services` — `{name}` → 서비스 추가 (백엔드는 신규 매장 첫 조회 시 기본 서비스 4종을 자동 시딩)

### 2.7 MessageModal (오버레이, FollowupView에서 트리거)
- **역할**: 후속관리 대상 고객에게 보낼 메시지 초안을 보고 복사 표시/발송하는 모달.
- **API**
  - `PATCH /api/v1/messages/{id}/copy` — 복사 표시(`deliveryStatus=COPIED`)
  - `POST /api/v1/messages/{id}/send` — 발송 요청(202 Accepted)

## 3. App 레벨 전역 데이터 로딩 (로그인 직후 1회)

| 호출 | 채우는 state | 사용 화면 |
|---|---|---|
| `GET /api/v1/customers` | `customers` | CustomerView, ConsultationView(중복확인 후), FollowupView(조인) |
| `GET /api/v1/store/services` | `services` | ConsultationView, EventsView, StoreView |
| `GET /api/v1/follow-ups` | `followUps` | FollowupView |
| `GET /api/v1/dashboard/summary` + `GET /api/v1/dashboard/priority-customers` | `dashboard`, `priorityCustomers` | DashboardView |
| `GET /api/v1/store` | `storeProfile` | StoreView |
| `GET /api/v1/store/events` | `events` | EventsView |

상담 저장(`saveConsultation`) 성공 시 `followUps`와 `dashboard`를 다시 불러와 후속관리/대시보드 수치를 갱신한다.

## 4. 공통 사이드바 메트릭 (상단 topbar)
- HOT 고객 수, 온도 미분류(대기) 고객 수, 전체 고객 수 — 모두 이미 로드된 `customers` state에서 클라이언트 측 집계 (별도 API 없음).

## 5. 인증/에러 처리 규약
- 모든 API 호출은 `Authorization: Bearer {accessToken}` 헤더 사용 (`api()` 헬퍼).
- 응답이 `{success, code, message, data}` 형태(ApiResponse 래퍼)면 자동으로 `data`만 추출.
- 실패 시 백엔드 메시지(`message`/`error`/`code`)를 그대로 노출하거나, 네트워크 자체 실패 시 "백엔드 서버에 연결할 수 없습니다." 표시.

## 6. 백엔드 엔드포인트 전체 목록 (참고용, FitbackApiController + AuthController)

| 영역 | 메서드 & 경로 | 설명 |
|---|---|---|
| 인증 | POST /api/v1/auth/register | 회원가입 |
| 인증 | GET /api/v1/auth/verify-email | 이메일 인증 |
| 인증 | POST /api/v1/auth/login | 로그인 |
| 인증 | POST /api/v1/auth/refresh | 토큰 재발급 |
| 인증 | POST /api/v1/auth/logout | 로그아웃 |
| 매장 | GET/POST/PUT /api/v1/store | 매장 프로필 조회/생성/수정 |
| 매장 | GET/POST/PUT /api/v1/store/services, /services/{id} | 서비스 목록/생성/수정 |
| 매장 | GET/PUT /api/v1/store/settings | 매장 설정 |
| 고객 | GET/POST /api/v1/customers, GET/PUT/DELETE /{id} | 고객 목록/생성/조회/수정/삭제 |
| 고객 | GET/POST /api/v1/customers/{id}/consultations | 고객별 상담 이력 |
| 고객 | GET/PUT /api/v1/customers/{id}/interest-services | 관심 서비스 |
| 고객 | GET/POST /api/v1/customers/{id}/contact-results | 연락 결과 |
| 고객 | GET/POST /api/v1/customers/{id}/enrollments, PATCH /enrollments/{id}/status | 등록(수강) 처리 |
| 상담 | GET /api/v1/consultations/check-duplicate | 중복 확인 |
| 상담 | POST /api/v1/consultations/analyze-preview | AI 미리보기 (stateless) |
| 상담 | POST /api/v1/consultations | 상담 기록 생성(저장) |
| 상담 | GET/PUT /api/v1/consultations/{id}, POST /{id}/analyze | 상담 조회/수정/재분석 |
| 상담 | GET/PUT /api/v1/consultations/{id}/reasons | 미등록 사유 |
| 후속관리 | GET /api/v1/follow-ups, GET /customers/{id}/follow-ups | 후속관리 목록 |
| 후속관리 | PATCH /follow-ups/{id}/status, /snooze, PUT /{id} | 상태 변경/스누즈/수정 |
| 메시지 | POST /follow-ups/{id}/messages/generate, GET /messages | 메시지 초안 생성/조회 |
| 메시지 | PUT /messages/{id}, PATCH /{id}/copy, POST /{id}/send | 메시지 수정/복사표시/발송 |
| 메시지 | POST /messages/delivery-callback | 발송 결과 웹훅 콜백 |
| 이벤트 | GET/POST /api/v1/store/events, GET /events/{id}/targets | 이벤트 생성/조회/대상 |
| 이벤트 | POST /event-targets/{id}/send, PATCH /{id}/status | 대상 발송/상태 변경 |
| 대시보드 | GET /api/v1/dashboard/summary, /priority-customers | 운영 현황/우선 고객 |
| 리포트 | GET /api/v1/reports/conversion, /non-conversion-reasons, /follow-up-funnel, /consultation | 통계 리포트 4종 |

---
*최초 작성: ultragoal G005/G006 작업(AI fallback 수정, 인증 통합, Postgres 영속화, 신규 프론트 화면 추가) 직후 코드 기준으로 정리. Figma MCP 연결 후 이 문서 내용을 기반으로 워크플로우 다이어그램/페이지별 카드를 Figma에 생성할 예정.*
