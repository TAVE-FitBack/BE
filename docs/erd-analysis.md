# ERD 검토 및 보완 문서

## 문서 목적

`BE/docs/erd`에 있는 ERDCloud DDL 초안을 검토하고, 현재 프로젝트 생성에 바로 참고할 수 있도록 테이블 역할, 관계, 누락된 제약, MVP 우선순위, 수정 권장 사항을 정리한다.

이 문서는 원본 ERD를 삭제하거나 대체하지 않는다. 원본 DDL에서 확정적으로 확인되는 내용과, 컬럼명/`drawio-analysis.md` 흐름을 근거로 한 보완 제안을 분리해서 기록한다.

## 결론 요약

현재 ERD는 도메인 범위는 넓게 잘 잡혀 있으나, 아직 완성본으로 쓰기에는 다음 문제가 있다.

1. FK가 대부분 누락되어 있다.
2. PostgreSQL 타입을 쓰면서 MySQL식 백틱 문법이 섞여 있다.
3. `user` 같은 예약어/충돌 가능 테이블명이 있다.
4. 일부 JSONB 기본값 타입이 맞지 않는다.
5. 상담 등록 MVP와 후속 관리/이벤트/메시지 기능이 한 ERD에 섞여 있어 구현 단계 분리가 필요하다.
6. 고객 중복 확인과 고객-서비스 관계의 유니크 정책이 명확하지 않다.

권장 방향:

- DB는 PostgreSQL 기준으로 정리한다.
- MVP 1차는 `store`, `user`, `service`, `customer`, `interest_service`, `consultation`, `consultation_signal`, `customer_ai_insight`, `non_conversion_reason` 중심으로 구현한다.
- `follow_up`, `message_template`, `event`, `schedule`, `task_checklist`, `contact_result`는 2차 기능으로 분리한다.

## 원본 ERD 테이블 목록

| 테이블 | 역할 |
| --- | --- |
| `store` | 매장/조직 |
| `user` | 매장 직원/사용자 |
| `service` | 매장에서 제공하는 상담/상품/서비스 |
| `customer` | 상담 대상 고객 |
| `interest_service` | 고객이 관심 있는 서비스 매핑 |
| `consultation` | 고객 상담 기록 |
| `consultation_signal` | 상담에서 추출된 신호/시그널 |
| `customer_ai_insight` | 고객 단위 AI 분석 결과 |
| `non_conversion_reason` | 미등록/미전환 사유 |
| `follow_up` | 후속 연락/관리 액션 |
| `follow_up_ai_insight` | follow-up 단위 AI 인사이트 |
| `contact_result` | 연락 결과 기록 |
| `message_template` | AI/사용자 메시지 초안 및 발송 상태 |
| `event` | 매장 이벤트/프로모션 |
| `event_target` | 이벤트 대상 고객 |
| `schedule` | 일정 |
| `task_checklist` | 할 일/체크리스트 |

## 도메인별 구조

### 1. 매장/사용자/서비스

#### `store`

매장 또는 조직의 최상위 기준이다.

주요 컬럼:

- `id`
- `name`
- `store_type`
- `created_at`
- `updated_at`

#### `user`

매장에 소속된 직원 계정이다.

주요 컬럼:

- `id`
- `store_id`
- `name`
- `email`
- `role`
- `password`
- `created_at`
- `updated_at`

보완 필요:

- PostgreSQL에서는 `user`가 충돌 가능성이 있으므로 `app_user`, `staff`, `store_user` 중 하나로 변경하는 것을 권장한다.
- `email`은 전역 유니크 또는 매장별 유니크 정책을 정해야 한다.

#### `service`

매장에서 제공하는 상담 상품/서비스다.

주요 컬럼:

- `id`
- `store_id`
- `name`
- `description`
- `price`
- `is_active`
- `created_at`
- `updated_at`

보완 필요:

- 매장 내 서비스명 중복 허용 여부를 정해야 한다.
- 권장 유니크 후보: `(store_id, name)`

### 2. 고객/관심 서비스

#### `customer`

상담 대상 고객의 기본 정보와 상태를 저장한다.

주요 컬럼:

- `id`
- `store_id`
- `service_id`
- `name`
- `gender`
- `phone_num`
- `inflow_path`
- `status`
- `first_consult_at`
- `latest_consult_at`
- `created_at`
- `updated_at`

검토 의견:

- `service_id`가 `NOT NULL`로 들어가 있으나, 별도 `interest_service` 테이블도 존재한다.
- 고객이 여러 서비스에 관심을 가질 수 있다면 `customer.service_id`는 제거하거나 대표 관심 서비스 역할로 명확히 이름을 바꿔야 한다.
- 상담 등록 흐름에서는 고객명/연락처 중복 확인이 핵심이므로, 매장 내 연락처 중복 정책이 필요하다.

권장 제약:

- `store_id -> store.id`
- `service_id -> service.id` 유지 시 FK 필요
- `phone_num`이 nullable이면 partial unique index 검토
- 권장 중복 기준: `(store_id, phone_num)` where `phone_num is not null`

#### `interest_service`

고객과 관심 서비스의 N:M 매핑 테이블이다.

주요 컬럼:

- `id`
- `customer_id`
- `service_id`
- `created_at`
- `updated_at`

권장 제약:

- `customer_id -> customer.id`
- `service_id -> service.id`
- `(customer_id, service_id)` unique

### 3. 상담 등록/분석 MVP

#### `consultation`

고객 상담 기록의 중심 테이블이다.

주요 컬럼:

- `id`
- `customer_id`
- `user_id`
- `session_no`
- `stage`
- `summary`
- `source_type`
- `raw_text`
- `visit_purpose`
- `experience_note`
- `positive_signal`
- `extra_note`
- `ai_parsed_at`
- `created_at`
- `updated_at`

drawio 흐름과 연결:

- 상담 저장 시 `customer`, `interest_service`, `consultation`, `consultation_signal`이 하나의 트랜잭션으로 저장된다.
- 저장 전 AI 분석 미리보기는 DB에 저장하지 않는다.
- 최종 저장 시 `raw_text`, `summary`, `visit_purpose`, `experience_note`, `positive_signal`, `extra_note`, `consultation_signal` 등이 반영될 수 있다.

보완 필요:

- `stage` 값 정의 필요
- `source_type` 값 정의 필요
- `session_no` 산정 기준 필요
- 신규 상담/재상담 분기 시 기존 고객에 새 상담을 붙이는 정책 필요

권장 제약:

- `customer_id -> customer.id`
- `user_id -> app_user.id` 또는 변경된 사용자 테이블명
- `(customer_id, session_no)` unique 검토

#### `consultation_signal`

상담 텍스트에서 추출한 구매/등록 가능성, 긍정/부정 신호 등을 저장한다.

주요 컬럼:

- `id`
- `consultation_id`
- `signal_type`
- `signal_value`
- `confidence`
- `evidence_text`
- `created_at`

drawio 연결:

- 상담 저장 트랜잭션에서 insert 대상이다.
- 고객 상세 화면에서 `signals`로 표시된다.

보완 필요:

- `signal_type`, `signal_value`, `confidence`의 값 범위를 정의해야 한다.
- 동일 상담에서 같은 signal 중복 저장 허용 여부를 정해야 한다.

권장 제약:

- `consultation_id -> consultation.id`
- `(consultation_id, signal_type, signal_value)` unique 검토

#### `customer_ai_insight`

고객 단위 AI 분석 결과를 저장한다.

주요 컬럼:

- `customer_id`
- `lead_temperature`
- `temperature_basis`
- `priority_score`
- `analyzed_at`
- `created_at`
- `updated_at`

drawio 연결:

- 상담 저장 후 Message Queue 기반 비동기 분석 결과가 반영된다.
- 고객 목록 폴링은 `lead_temperature is null` 여부를 기준으로 분석 완료 여부를 판단한다.
- 고객 상세 화면에서 고객 온도와 분석 근거를 보여준다.

보완 필요:

- `lead_temperature` 값 범위 정의 필요
- 분석 실패/지연 상태를 표현할 컬럼이 없다.
- 폴링 UX를 안정화하려면 `analysis_status`, `analysis_error`, `requested_at` 같은 컬럼을 검토할 수 있다.

권장 제약:

- `customer_id -> customer.id`
- `priority_score` 범위 체크: 0~100 또는 서비스 정책 기준

#### `non_conversion_reason`

AI가 추출한 미등록/미전환 사유를 저장한다.

주요 컬럼:

- `id`
- `customer_id`
- `consultation_id`
- `reason_type`
- `role`
- `reason_basis`
- `confidence`
- `created_at`
- `updated_at`

drawio 연결:

- 고객 인사이트 분석 결과로 insert된다.
- 고객 목록/상세에서 미등록 사유 또는 우선순위 근거로 사용될 수 있다.

보완 필요:

- `role`의 의미가 `PRIMARY`, `SECONDARY`라면 값 정의가 필요하다.
- 고객별 최신 사유만 보여줄지, 상담별 이력을 모두 보관할지 정책이 필요하다.

권장 제약:

- `customer_id -> customer.id`
- `consultation_id -> consultation.id`

### 4. Follow-up/연락 관리

#### `follow_up`

상담 이후 추천 연락일과 후속 관리 상태를 저장한다.

주요 컬럼:

- `id`
- `consultation_id`
- `recommend_contact_date`
- `status`
- `snoozed_until`
- `memo`
- `created_at`
- `updated_at`

drawio 연결:

- 현재 drawio 문서에서는 follow-up 영역이 별도 도메인으로만 표시되고, MVP 상세 흐름에서는 제외된 것으로 보인다.

권장 제약:

- `consultation_id -> consultation.id`

#### `follow_up_ai_insight`

follow-up 단위 AI 제안/주의점을 저장한다.

주요 컬럼:

- `follow_up_id`
- `persuasion_point`
- `caution_note`
- `action_basis`
- `analyzed_at`
- `created_at`
- `updated_at`

원본 ERD에서 실제 FK가 선언된 테이블 중 하나다.

문제:

- `persuasion_point JSONB NOT NULL DEFAULT ''`는 PostgreSQL에서 타입상 부적절하다.
- `action_basis TEXT NOT NULL DEFAULT '{}'`는 JSON처럼 쓰려는 의도라면 `JSONB`로 바꾸는 편이 낫다.

권장:

- `persuasion_point JSONB NOT NULL DEFAULT '{}'::jsonb`
- `action_basis JSONB NOT NULL DEFAULT '{}'::jsonb`

#### `contact_result`

고객 연락 시도 결과를 저장한다.

주요 컬럼:

- `id`
- `customer_id`
- `follow_up_id`
- `result_status`
- `result_date`
- `memo`
- `created_at`
- `updated_at`

권장 제약:

- `customer_id -> customer.id`
- `follow_up_id -> follow_up.id`

#### `message_template`

메시지 초안, 버전, 톤, 발송 상태를 저장한다.

주요 컬럼:

- `id`
- `follow_up_id`
- `event_target_id`
- `customer_id`
- `message_id`
- `content`
- `version_type`
- `tone_preset`
- `delivery_status`
- `scheduled_at`
- `generated_at`
- `sent_at`
- `delivered_at`
- `updated_at`

drawio 연결:

- 현재 고객 상세 화면에서는 메시지 초안 기능이 제외된 것으로 문서화되어 있다.
- 따라서 MVP에서는 후순위로 두는 것이 안전하다.

권장 제약:

- `follow_up_id -> follow_up.id`
- `event_target_id -> event_target.id`
- `customer_id -> customer.id`

### 5. 이벤트/스케줄/업무 관리

#### `event`

매장 이벤트/프로모션 정보를 저장한다.

주요 컬럼:

- `id`
- `store_id`
- `service_id`
- `title`
- `event_type`
- `description`
- `discount_rate`
- `img_url`
- `start_date`
- `end_date`
- `status`
- `created_at`
- `updated_at`

권장 제약:

- `store_id -> store.id`
- `service_id -> service.id`
- `start_date <= end_date` check
- `discount_rate` 범위 check

#### `event_target`

이벤트 추천/발송 대상 고객을 저장한다.

주요 컬럼:

- `id`
- `customer_id`
- `event_id`
- `status`
- `created_at`

권장 제약:

- `customer_id -> customer.id`
- `event_id -> event.id`
- `(customer_id, event_id)` unique

#### `schedule`

상담/고객 관련 일정이다.

주요 컬럼:

- `id`
- `store_id`
- `customer_id`
- `consultation_id`
- `title`
- `schedule_type`
- `start_at`
- `end_at`
- `memo`
- `created_by`
- `created_at`
- `updated_at`

권장 제약:

- `store_id -> store.id`
- `customer_id -> customer.id`
- `consultation_id -> consultation.id`
- `created_by -> app_user.id`
- `start_at < end_at` check

#### `task_checklist`

일정 또는 고객과 연결되는 체크리스트다.

주요 컬럼:

- `id`
- `schedule_id`
- `customer_id`
- `title`
- `task_type`
- `due_date`
- `is_done`
- `done_at`
- `created_at`
- `updated_at`

권장 제약:

- `schedule_id -> schedule.id`
- `customer_id -> customer.id`
- `is_done = true`일 때 `done_at` 처리 정책 필요

## 보완된 관계 목록

원본 ERD에 명시된 FK:

| From | To | 상태 |
| --- | --- | --- |
| `follow_up_ai_insight.follow_up_id` | `follow_up.id` | 원본 명시 |
| `customer_ai_insight.customer_id` | `customer.id` | 원본 명시 |

컬럼명과 도메인 흐름상 추가해야 할 FK:

| From | To | 이유 |
| --- | --- | --- |
| `user.store_id` | `store.id` | 직원은 매장 소속 |
| `service.store_id` | `store.id` | 서비스는 매장 소속 |
| `customer.store_id` | `store.id` | 고객은 매장 단위 관리 |
| `customer.service_id` | `service.id` | 현재 ERD상 대표 서비스로 보임 |
| `interest_service.customer_id` | `customer.id` | 고객 관심 서비스 매핑 |
| `interest_service.service_id` | `service.id` | 고객 관심 서비스 매핑 |
| `consultation.customer_id` | `customer.id` | 상담은 고객 소속 |
| `consultation.user_id` | `user.id` | 상담 작성 직원 |
| `consultation_signal.consultation_id` | `consultation.id` | 상담 분석 신호 |
| `non_conversion_reason.customer_id` | `customer.id` | 고객별 미전환 사유 |
| `non_conversion_reason.consultation_id` | `consultation.id` | 상담별 미전환 사유 |
| `follow_up.consultation_id` | `consultation.id` | 상담 후속 관리 |
| `contact_result.customer_id` | `customer.id` | 고객 연락 결과 |
| `contact_result.follow_up_id` | `follow_up.id` | 후속 연락 결과 |
| `message_template.follow_up_id` | `follow_up.id` | 후속 메시지 |
| `message_template.event_target_id` | `event_target.id` | 이벤트 대상 메시지 |
| `message_template.customer_id` | `customer.id` | 고객별 메시지 |
| `event.store_id` | `store.id` | 매장 이벤트 |
| `event.service_id` | `service.id` | 서비스 연계 이벤트 |
| `event_target.customer_id` | `customer.id` | 이벤트 대상 고객 |
| `event_target.event_id` | `event.id` | 이벤트 대상 매핑 |
| `schedule.store_id` | `store.id` | 매장 일정 |
| `schedule.customer_id` | `customer.id` | 고객 일정 |
| `schedule.consultation_id` | `consultation.id` | 상담 일정 |
| `schedule.created_by` | `user.id` | 일정 생성자 |
| `task_checklist.schedule_id` | `schedule.id` | 일정 체크리스트 |
| `task_checklist.customer_id` | `customer.id` | 고객 체크리스트 |

## MVP 기준 권장 스키마 범위

`drawio-analysis.md`의 상담 등록/고객 분석 흐름을 먼저 구현한다면 다음 테이블을 1차 범위로 둔다.

### 1차 필수

- `store`
- `user` 또는 `app_user`
- `service`
- `customer`
- `interest_service`
- `consultation`
- `consultation_signal`
- `customer_ai_insight`
- `non_conversion_reason`

### 2차 또는 보류

- `follow_up`
- `follow_up_ai_insight`
- `contact_result`
- `message_template`
- `event`
- `event_target`
- `schedule`
- `task_checklist`

보류 이유:

- drawio에서 follow-up 영역은 표시되지만 MVP 상세 흐름에서는 제외되어 있다.
- 고객 상세 화면에서 메시지 초안 기능은 현재 제외로 정리되어 있다.
- 활동 타임라인은 추후 `activity_log` 같은 별도 테이블 검토가 필요하다고 되어 있다.

## 주요 설계 결정 필요 사항

### 1. `customer.service_id`와 `interest_service` 중복

현재 ERD에는 고객 단일 서비스 FK와 관심 서비스 매핑 테이블이 동시에 있다.

선택지:

- 단일 관심 서비스만 필요하면 `interest_service` 제거
- 다중 관심 서비스가 필요하면 `customer.service_id` 제거
- 대표 서비스와 다중 관심 서비스가 모두 필요하면 `customer.primary_service_id`로 이름 변경

권장:

- Figma/기능 흐름상 다중 관심 서비스 선택이 있으므로 `interest_service`를 유지하고, `customer.service_id`는 제거하거나 `primary_service_id`로 명확히 변경한다.

### 2. 신규 상담과 재상담 처리

drawio에서는 신규 상담 선택 시나리오가 명확하고, 재상담은 모호하다.

권장:

- 고객 중복 확인은 `customer` 존재 여부를 반환한다.
- 신규 고객이면 `customer + consultation`을 한 트랜잭션으로 생성한다.
- 기존 고객이면 신규 `consultation`만 생성하는 별도 흐름을 둔다.
- 고객만 생성되고 상담이 없는 중간 상태를 만들지 않는다.

### 3. AI 분석 상태

현재 `customer_ai_insight.lead_temperature is null`을 기준으로 폴링하는 구조다.

권장 추가 컬럼:

- `analysis_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`
- `requested_at TIMESTAMPTZ NULL`
- `analysis_error TEXT NULL`

가능 상태:

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`
- `TIMEOUT`

### 4. Enum/코드값 정의

다음 컬럼들은 값 범위를 문서화하거나 enum/check constraint로 관리해야 한다.

- `user.role`
- `customer.gender`
- `customer.inflow_path`
- `customer.status`
- `consultation.stage`
- `consultation.source_type`
- `consultation_signal.signal_type`
- `consultation_signal.signal_value`
- `consultation_signal.confidence`
- `customer_ai_insight.lead_temperature`
- `non_conversion_reason.reason_type`
- `non_conversion_reason.role`
- `non_conversion_reason.confidence`
- `follow_up.status`
- `contact_result.result_status`
- `message_template.version_type`
- `message_template.tone_preset`
- `message_template.delivery_status`
- `event.event_type`
- `event.status`
- `event_target.status`
- `schedule.schedule_type`
- `task_checklist.task_type`

## DDL 수정 권장 사항

### PostgreSQL 기준 문법 정리

원본 DDL은 PostgreSQL 타입을 사용하므로, PostgreSQL 기준으로 정리하는 것을 권장한다.

- 백틱 제거: `` `customer` `` -> `customer`
- 예약어성 테이블명 변경: `user` -> `app_user`
- JSONB 기본값 수정
- UUID 기본값 정책 결정
- 누락 FK 추가
- 유니크/인덱스 추가

### JSONB 기본값 수정

현재:

```sql
persuasion_point JSONB NOT NULL DEFAULT ''
```

권장:

```sql
persuasion_point JSONB NOT NULL DEFAULT '{}'::jsonb
```

현재:

```sql
action_basis TEXT NOT NULL DEFAULT '{}'
```

권장:

```sql
action_basis JSONB NOT NULL DEFAULT '{}'::jsonb
```

### 인덱스 후보

고객 중복 확인:

```sql
CREATE UNIQUE INDEX ux_customer_store_phone
ON customer (store_id, phone_num)
WHERE phone_num IS NOT NULL;
```

고객 목록 조회:

```sql
CREATE INDEX ix_customer_store_updated
ON customer (store_id, updated_at DESC);
```

분석 대기 폴링:

```sql
CREATE INDEX ix_customer_ai_insight_temperature
ON customer_ai_insight (lead_temperature);
```

상담 이력 조회:

```sql
CREATE INDEX ix_consultation_customer_created
ON consultation (customer_id, created_at DESC);
```

미전환 사유 조회:

```sql
CREATE INDEX ix_non_conversion_reason_customer_created
ON non_conversion_reason (customer_id, created_at DESC);
```

## 권장 Entity 구현 순서

1. `store`
2. `app_user`
3. `service`
4. `customer`
5. `interest_service`
6. `consultation`
7. `consultation_signal`
8. `customer_ai_insight`
9. `non_conversion_reason`
10. follow-up/event/message/schedule 계열은 MVP 이후 추가

## 프로젝트 생성 시 바로 반영할 API 관점 매핑

| API | 주요 테이블 |
| --- | --- |
| `GET /api/consultations/check-duplicate` | `customer` |
| `POST /api/consultations/analyze-preview` | DB 미저장, FastAPI 위임 |
| `POST /api/consultations` | `customer`, `interest_service`, `consultation`, `consultation_signal` |
| `POST /ai/v1/customers/{customerId}/insight` | 분석 결과 생성 후 Spring 반영 |
| `GET /api/customers` | `customer`, `customer_ai_insight`, `non_conversion_reason` |
| `GET /api/customers?ids={ids}` | `customer`, `customer_ai_insight` |
| `GET /api/customers/{customerId}` | `customer`, `consultation`, `consultation_signal`, `customer_ai_insight`, `non_conversion_reason` |

## 최종 권장안

현재 ERD는 전체 서비스 방향을 잡기에는 충분하지만, 그대로 마이그레이션에 쓰기에는 위험하다.

우선은 상담 등록과 고객 분석 MVP에 필요한 테이블만 PostgreSQL 기준으로 정리하고, FK/유니크/인덱스를 먼저 확정하는 것이 좋다. 이후 follow-up, 메시지, 이벤트, 일정 기능을 단계적으로 붙이면 초기 구현과 검증 부담을 줄일 수 있다.

특히 다음 5가지는 구현 전에 확정해야 한다.

1. `user` 테이블명을 `app_user` 등으로 변경할지
2. `customer.service_id`를 유지할지, `interest_service`만 사용할지
3. 고객 중복 기준을 `(store_id, phone_num)`로 둘지
4. AI 분석 완료 여부를 `lead_temperature is null`로만 볼지, 별도 상태 컬럼을 둘지
5. follow-up/message/event/schedule 기능을 MVP에서 제외할지
