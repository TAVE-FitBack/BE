# Fitback React Frontend

React/Vite 기반 Fitback 운영 화면입니다. Figma GUI의 다크 패널, 라임 포커스, 조밀한 운영형 흐름을 기준으로 로그인, 상담 기록, AI 분석, 고객 인사이트, 후속 관리를 구현합니다.

## 실행

```bash
npm install
npm run dev
```

개발 서버는 `/api` 요청을 `http://localhost:8080` 백엔드로 프록시합니다. 별도 백엔드를 연결하려면 `VITE_API_BASE_URL=http://localhost:8080/api/v1` 환경 변수를 지정합니다. 로그인 또는 계정 생성 이후 실제 API 응답만 화면에 반영됩니다.

회원가입과 로그인은 Spring Boot 백엔드가 실행 중이어야 동작합니다. 로컬 백엔드는 `JWT_SECRET`과 PostgreSQL(`localhost:5432/fitback`) 연결이 필요합니다. 자세한 실행 절차는 `../LOCAL_DEVELOPMENT.md`를 확인하세요.
