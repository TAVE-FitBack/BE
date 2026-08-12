# Fitback

피트니스 센터에서 상담 후 미등록된 잠재 고객을 AI가 분석하고 후속 관리를 지원하는 서비스
<br><br>
<img width="39%" src="https://github.com/user-attachments/assets/a9ebc053-67d9-4f55-8291-6647557f12b1" />
<br><br>

## ✨ Key Features

| Domain | Description |
|---|---|
| **Auth** | 이메일 인증 기반 회원가입 · 로그인 · 토큰 관리 |
| **Store** | 매장 · 서비스 · 유입경로 · 이벤트 설정 관리 |
| **Customer & Consultation** | 고객 상태 관리 · 상담 등록 · AI 분석 수행 |
| **Inquiry** | 문의 등록 · 상담 전환 · AI 중간 점검 |
| **Follow-up** | 후속관리 보드 · 연락 회차 · 답장 여부 관리 |
| **Schedule & Checklist** | 스케줄 · 체크리스트 관리 |
| **Message Template** | AI 기반 후속 메시지 생성 · 발송 처리 |
| **Analysis Report** | 상담 · 후속관리 리포트 조회 |

<br>

## 🔮 Tech Stack

#### Backend
<div>
  <img src="https://img.shields.io/badge/Java 21-007396?style=flat-square&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/Spring Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white">
  <img src="https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white">
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-FF4438?style=flat-square&logo=redis&logoColor=white">
  <img src="https://img.shields.io/badge/Spring Data JPA-6DB33F?style=flat-square&logo=spring&logoColor=white">
</div>

#### AI
<div>
  <img src="https://img.shields.io/badge/FastAPI-009688?style=flat-square&logo=fastapi&logoColor=white">
  <img src="https://img.shields.io/badge/OpenAI GPT--4o mini-412991?style=flat-square&logo=openai&logoColor=white">
  <img src="https://img.shields.io/badge/Neo4j AuraDB-4581C3?style=flat-square&logo=neo4j&logoColor=white">
</div>

#### Infrastructure
<div>
  <img src="https://img.shields.io/badge/AWS EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white">
  <img src="https://img.shields.io/badge/AWS RDS-527FFF?style=flat-square&logo=amazonrds&logoColor=white">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white">
  <img src="https://img.shields.io/badge/Nginx-009639?style=flat-square&logo=nginx&logoColor=white">
  <img src="https://img.shields.io/badge/GitHub Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white">
  <img src="https://img.shields.io/badge/Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black">
</div>

<br>

## 🔨 System Architecture

<img width="60%" alt="FitBack_SW" src="https://github.com/user-attachments/assets/f59fd315-9975-4018-95e8-3a13de1c40ba" />


## 📜 Github Workflow
> main 브랜치에 push되면 GitHub Actions가 자동 빌드 → Docker Hub 푸시 → EC2 배포

### 📝 Commit Convention

| Type | 의미 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅, 로직 변경 없는 경우 |
| `refactor` | 코드 리팩토링 |
| `chore` | 설정, 의존성, 기타 작업 |
| `ci` | CI/CD 설정 변경 |

### 🌿 Branch Strategy

- main → 배포 브랜치
- feat/#N → 기능 개발 브랜치 (이슈 번호 기반)

### 🔀 PR Convention

- PR 제목: `feat(#이슈번호): 내용`
- `feat/#N` → `main` 방향으로 PR
- Merge 방식: Merge Commit
