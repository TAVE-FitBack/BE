# draw.io 기반 상담/고객 분석 흐름 문서

## 문서 목적

`drawio.png`에 정리된 상담 등록 및 고객 분석 흐름을 백엔드/프론트엔드 프로젝트 생성에 참고할 수 있도록 문서화한다.

이 문서는 이미지에서 확인 가능한 내용을 기준으로 작성했으며, 일부 세부 구현은 추후 API 명세/ERD/Figma와 대조가 필요하다.

## 전체 참여 컴포넌트

- 사용자
- FrontEnd
- BE (Spring)
- Message Queue
- BE (FastAPI)

## 핵심 도메인

- `consultation`
- `customer`
- `follow_up`

## 전체 흐름 요약

0. 화면 진입 전 분기가 별도로 필요하다. 현재 시퀀스는 신규 상담을 선택한 경우에만 1단계 상담 등록 화면으로 진입하는 흐름이다.
1. 사용자는 상담 등록 화면에서 기본 정보와 Quick Memo를 입력한다.
2. 프론트엔드는 고객명/연락처 중복 여부를 Spring API로 확인한다.
3. 사용자는 Quick Memo를 입력하고 AI 분석 및 요약을 반복 실행할 수 있다.
4. AI 분석 미리보기는 저장하지 않는 stateless 요청으로 처리한다.
5. 사용자가 만족하면 기록 완료 및 저장을 수행한다.
6. Spring 서버는 상담 기록과 AI 정제 결과를 DB에 저장한다.
7. 저장 직후 고객 관리 목록으로 이동하고, 백그라운드에서 고객 인사이트 분석을 비동기로 실행한다.
8. 프론트엔드는 고객 목록에서 분석 완료 여부를 폴링한다.
9. 분석 완료 시 고객 온도, 미등록 사유, 우선순위 등의 갱신 결과를 목록에 반영한다.
10. 사용자가 고객을 클릭하면 고객 상세 분석 화면에서 고객 정보와 AI 분석 결과를 조회한다.

## 1. 상담 등록: 기본 정보 및 Quick Memo 입력

### 화면 목적

신규 상담 등록을 위한 1차 입력 화면이다. 기존 고객의 재상담이 아니라 신규 상담 등록 시나리오를 기준으로 한다.

### 현재 설계 메모

- 해당 페이지는 신규 상담 등록으로 초기화된다.
- 기존 고객이면 이 화면으로 진입하지 않는 설계로 보이나, 재상담인 경우의 진입/처리 방식은 아직 모호하다.
- 고객 등록과 상담 기록 저장이 하나의 트랜잭션으로 묶이는 구조다.
- 저장 후 상담 목록이 아니라 고객 상세/목록 쪽으로 흐름이 이어진다.
- 이미지 메모 기준으로, 기본 정보 저장과 상담 기록 저장을 분리하면 사용자가 상담 도중 페이지를 나갔을 때 고객만 등록되고 상담은 등록되지 않는 상태가 생길 수 있다.
- 이 상태에서 다시 상담을 진행하면 중복 확인 단계에서 이미 등록된 고객으로 걸릴 수 있으므로, 신규 상담/재상담/임시 저장 실패 상태의 분기 설계가 필요하다.

### 사용자 입력

- 고객명
- 연락처
- 방문 목적 또는 관심 상품
- Quick Memo

### 중복 확인 흐름

1. 사용자가 고객명/연락처를 입력한다.
2. 사용자가 `중복 확인` 버튼을 클릭한다.
3. FrontEnd가 Spring 서버에 중복 조회를 요청한다.

```http
GET /api/consultations/check-duplicate
```

요청 기준:

- Phone Number 조회

응답 분기:

- `200 OK`: 방문 정보/관심 상품/Quick Memo 입력 영역 활성화
- `409 ERROR`: 에러 문구 표시, 나머지 입력란 비활성화 유지

## 2. 상담 등록: AI 분석 및 요약 미리보기

### 화면 목적

상담 내용을 DB에 저장하기 전, 사용자가 Quick Memo 기반 AI 정제/요약 결과를 미리 확인하고 수정할 수 있게 한다.

### 주요 원칙

- 저장 전 분석은 stateless 호출이다.
- DB에 분석 결과를 저장하지 않는다.
- 사용자는 Quick Memo를 수정한 뒤 분석을 반복 실행할 수 있다.
- 사용자가 결과에 만족하면 `기록 완료 및 저장` 단계로 이동한다.

### 요청 흐름

1. 사용자가 `AI 분석 및 요약 실행`을 클릭한다.
2. FrontEnd가 Spring 서버에 분석 미리보기를 요청한다.

```http
POST /api/consultations/analyze-preview
```

Spring 요청 데이터:

- Raw Text
- 기본 정보
- DB 미저장

3. Spring 서버가 FastAPI 서버에 분석을 위임한다.

```http
POST /ai/v1/consultations/analyze
```

FastAPI 처리:

- LLM 분석
- AI 정제
- 요약 생성
- 누락 정보 추출

4. FastAPI는 분석 결과 JSON을 반환한다.
5. Spring은 `200 OK`와 함께 분석 결과를 그대로 FrontEnd에 전달한다.
6. FrontEnd는 다음 정보를 사용자에게 표시한다.

- AI 정제/요약
- 상담 핵심 요약
- 누락 정보 체크

### 고려 사항

- 타임아웃 처리가 필요하다.
- 반복 호출에 따른 비용/속도 문제가 있을 수 있다.
- `기록 완료 및 저장` 시점에는 사용자가 수정한 언어/다시 중복된 정보를 포함해 저장될 가능성이 있다.
- 새로 고려하는 분석 결과 리뷰 UX는 FrontEnd 로컬 저장 등으로 해결할 수 있을지 검토가 필요하다.

## 3. 상담 기록 완료 및 저장

### 화면 목적

최종 Raw Text와 AI 정제 결과를 DB에 저장한다.

### 요청 흐름

1. 사용자가 `기록 완료 및 저장` 버튼을 클릭한다.
2. FrontEnd가 Spring 서버에 상담 저장을 요청한다.

```http
POST /api/consultations
```

요청 데이터:

- 최종 Raw Text
- 최종 AI 정제 결과
- 고객 기본 정보
- 상담 관련 선택 정보

3. Spring 서버는 하나의 트랜잭션으로 DB에 반영한다.

### DB 반영 내용

이미지 기준으로 다음 테이블에 insert가 발생한다.

- `customer`
- `interest_service`
- `consultation`
- `consultation_signal`

메모:

- 기능 명세서에 따르면 AI 전환 가능성을 읽어야 한다.
- 정확히 어떤 데이터인지 명세 확인이 필요하다.

### 저장 후 화면 전환

- 상담 기록 입력 화면에서 상담 내역 조회 화면 또는 고객 관리 목록 화면으로 이동한다.
- 이미지상 주요 흐름은 상담 저장 후 고객 관리 목록 화면으로 이동하는 것으로 보인다.

## 4. 저장 후 고객 인사이트 비동기 분석

### 목적

상담 저장 직후 고객별 인사이트를 비동기로 생성하고, 목록 화면에서 분석 완료 상태를 반영한다.

### 이벤트 발행

Spring 서버는 상담 저장 후 다음 데이터를 FrontEnd에 반환한다.

- `consultationId`
- `customerId`
- `201 Created`

동시에 Message Queue로 최초 분석 요청 이벤트를 비동기 발행한다.

이벤트 주요 데이터:

- `customerId`

### FastAPI 비동기 처리

Message Queue를 통해 FastAPI가 분석 작업을 소비한다.

```http
POST /ai/v1/customers/{customerId}/insight
```

FastAPI 처리:

- 고객 분석
- 상담 히스토리 기반 인사이트 생성
- 미등록 사유 분석
- 고객 온도 산정
- 다음 행동 제안 생성

분석 결과는 JSON으로 반환된다.

### DB 반영 내용

Spring 서버는 분석 결과를 바탕으로 다음 테이블을 반영한다.

- `customer_ai_insight` upsert
- `non_conversion_reason` insert

## 5. 고객 관리 목록 조회 및 폴링

### 화면 목적

상담 저장 이후 사용자가 고객 관리 목록에서 고객 상태와 AI 분석 결과 반영 여부를 확인한다.

### 최초 목록 조회

```http
GET /api/customers
```

응답:

- 고객 목록 반환
- `leadTemperature: null`인 항목 포함

### 폴링 대상 조회

프론트엔드는 5초 간격으로 `leadTemperature`가 `null`인 고객만 조회한다.

```http
GET /api/customers?ids={polling 대상}
```

분석 미완료 응답:

- 여전히 `leadTemperature: null`

분석 완료 응답:

- `leadTemperature: "WARM"` 등 채워진 값

### 폴링 종료 조건

1. 정상 종료: 대상 전원 분석 완료 시 다음 주기를 기다리지 않고 즉시 종료한다.
2. 화면 이탈: 다른 페이지 이동, 앱 종료 시 폴링을 중단한다.
3. 타임아웃: 약 2분 경과 시 중단하고 해당 행에 `분석 지연` 안내로 전환한다.

### 목록 반영

분석 완료 후 목록에는 다음 정보가 즉시 갱신되어 표시된다.

- 고객 온도
- 미등록 사유
- 우선순위

## 6. 고객 상세 분석 화면 조회

### 화면 목적

목록에서 고객을 선택하면 고객 상세 분석 화면으로 이동하여 고객 정보와 AI 분석 결과를 함께 보여준다.

### 요청 흐름

1. 사용자가 고객 목록에서 특정 고객을 클릭한다.
2. FrontEnd가 Spring 서버에 고객 상세를 요청한다.

```http
GET /api/customers/{customerId}
```

3. Spring 서버는 다음 데이터를 join 조회하여 반환한다.

- `customer`
- `customer_ai_insight`
- `non_conversion_reason`
- `consultation_signal`

응답 표시:

- 고객 정보
- 고객 온도
- signals
- 미등록 사유
- Next Best Action
- 메시지 초안 제외

## 7. follow_up 영역

이미지에서는 `follow_up` 도메인이 별도 구간으로 표시되어 있으나, 현재 상세 흐름은 배제된 것으로 보인다.

현재 메모:

- 활동 타임라인은 현재 백엔드 쪽에서 활동 타임라인 데이터를 생성하지 않는 것으로 정리되어 있다.
- 추후 activity log 같은 테이블 생성 후 적용하는 방향이 언급되어 있다.

## API 후보 목록

| 구분 | Method | Path | 역할 |
| --- | --- | --- | --- |
| Spring | GET | `/api/consultations/check-duplicate` | 연락처 기반 고객 중복 확인 |
| Spring | POST | `/api/consultations/analyze-preview` | 저장 전 상담 AI 분석 미리보기 |
| FastAPI | POST | `/ai/v1/consultations/analyze` | 상담 텍스트 LLM 분석/요약/누락 정보 추출 |
| Spring | POST | `/api/consultations` | 최종 상담 기록 및 AI 정제 결과 저장 |
| Spring | GET | `/api/customers` | 고객 목록 조회 |
| Spring | GET | `/api/customers?ids={ids}` | 분석 대기 고객 폴링 조회 |
| FastAPI | POST | `/ai/v1/customers/{customerId}/insight` | 고객 단위 AI 인사이트 분석 |
| Spring | GET | `/api/customers/{customerId}` | 고객 상세 분석 조회 |

## 데이터 저장 후보

### 상담 저장 트랜잭션

- `customer`
- `interest_service`
- `consultation`
- `consultation_signal`

### 고객 인사이트 분석 반영

- `customer_ai_insight`
- `non_conversion_reason`

### 추후 검토

- `activity_log` 또는 유사한 follow-up/timeline 테이블

## 프론트 화면 후보

### 상담 등록 화면

- 기본 정보 입력
- 중복 확인
- Quick Memo 입력
- AI 분석 및 요약 실행
- 누락 정보 체크
- 상담 핵심 요약
- 기록 완료 및 저장

### 고객 관리 목록 화면

- 고객 목록
- 고객 온도
- 미등록 사유
- 우선순위
- 분석 진행/지연 상태
- 폴링 기반 실시간 갱신

### 고객 상세 분석 화면

- 고객 기본 정보
- AI 분석 결과
- 고객 온도
- signals
- 미등록 사유
- Next Best Action
- 메시지 초안 기능은 현재 제외

## 구현 시 우선 확인 필요 사항

1. 화면 진입 전 신규 상담/재상담/기존 고객 상담의 분기 기준
   - 현재 시퀀스는 신규 상담 선택 시나리오만 명확하다.
2. 기본 정보 저장과 상담 기록 저장을 분리할지, 하나의 트랜잭션으로 묶을지 결정
   - 분리 시 고객만 생성되고 상담은 생성되지 않는 중간 상태를 처리해야 한다.
3. `GET /api/customers`의 `ids` 쿼리 파라미터 지원 여부
   - 다건 조회 스펙 추가 필요 가능성 있음
4. 폴링 중 같은 화면에서 다른 직원이 같은 고객을 수정했을 때의 반영 방식
5. FastAPI 평균 분석 시간이 2분 타임아웃 기준에 적합한지 확인
6. AI 전환 가능성 데이터가 어떤 필드/테이블로 저장되어야 하는지 확인
7. `consultation_signal`과 고객 상세의 `signals` 표시 기준 확인
8. 상담 저장 후 최종 이동지가 고객 관리 목록인지, 상담 내역 조회 화면인지 확정 필요
9. follow-up 활동 타임라인은 MVP에서 제외인지, 별도 테이블을 생성할지 결정 필요

## 프로젝트 생성 시 권장 구현 순서

1. ERD 기준으로 `customer`, `consultation`, `consultation_signal`, `customer_ai_insight`, `non_conversion_reason` 모델을 확정한다.
2. 상담 중복 확인 API를 구현한다.
3. 상담 AI 분석 미리보기 API를 구현하고 FastAPI 연동을 붙인다.
4. 상담 저장 트랜잭션을 구현한다.
5. 저장 후 Message Queue 이벤트 발행 구조를 구현한다.
6. FastAPI 고객 인사이트 분석 API를 구현한다.
7. 분석 결과를 Spring 쪽 DB 반영 로직과 연결한다.
8. 고객 목록 조회 및 `ids` 기반 폴링 조회를 구현한다.
9. 고객 상세 조회 API를 구현한다.
10. Figma 화면 기준으로 상담 등록, 고객 목록, 고객 상세 화면을 구현한다.
