# Fitback React Frontend

React/Vite 기반의 Fitback 운영 화면입니다. Figma GUI의 다크 패널, 라임 포커스, 조밀한 운영툴 톤을 기준으로 상담 등록, AI 미리보기, 고객 인사이트, 후속관리 흐름을 구현했습니다.

## 실행

```bash
npm install
npm run dev
```

백엔드가 `http://localhost:8080`에서 실행 중이면 Vite 프록시 대신 `VITE_API_BASE_URL=http://localhost:8080/api/v1` 환경 변수를 지정해서 API를 연결할 수 있습니다. 백엔드가 꺼져 있어도 로컬 샘플 분석으로 화면 흐름을 확인할 수 있습니다.
