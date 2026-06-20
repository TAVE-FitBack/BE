# Fitback React Frontend

React/Vite 기반 Fitback 운영 화면입니다. Figma GUI의 다크 패널, 라임 포커스, 조밀한 운영형 흐름을 기준으로 로그인, 상담 기록, AI 분석, 고객 인사이트, 후속 관리를 구현합니다.

## 실행

```bash
npm install
npm run dev
```

백엔드가 `http://localhost:8080`에서 실행 중이면 `VITE_API_BASE_URL=http://localhost:8080/api/v1` 환경 변수를 지정해서 API를 연결합니다. 로그인 또는 계정 생성 이후 실제 API 응답만 화면에 반영됩니다.
