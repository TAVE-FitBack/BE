import React, { useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api/v1";

const sampleCustomers = [
  {
    id: "sample-1",
    name: "김서연",
    phoneNum: "***-****-8821",
    status: "UNREGISTERED",
    leadTemperature: "HOT",
    priorityScore: 90,
    primaryReason: "PRICE",
    nextBestAction: "오늘 18시 전 체험권 혜택과 PT 첫 회 안내 메시지 발송",
    aiInsight: {
      leadTemperature: "HOT",
      temperatureBasis: "즉시 시작 의사와 시간 제약이 함께 확인됨",
      priorityScore: 90,
      analysisStatus: "COMPLETED"
    },
    signals: [
      { signalType: "BUYING_INTENT", signalValue: "바로 등록 가능", confidence: 0.91 },
      { signalType: "TIME_CONSTRAINT", signalValue: "퇴근 후 7시 선호", confidence: 0.82 }
    ],
    consultations: [
      { id: "consult-1", summary: "체중 감량 목적. 시설 청결과 가격을 비교 중.", rawText: "운동은 처음이고 퇴근 후 7시 가능. 가격만 맞으면 바로 등록하고 싶음." }
    ]
  },
  {
    id: "sample-2",
    name: "박민준",
    phoneNum: "***-****-1029",
    status: "PENDING",
    leadTemperature: "WARM",
    priorityScore: 70,
    primaryReason: "SCHEDULE",
    nextBestAction: "주말 수업 가능 시간표를 먼저 제안",
    aiInsight: {
      leadTemperature: "WARM",
      temperatureBasis: "관심은 높지만 일정 제약이 큼",
      priorityScore: 70,
      analysisStatus: "COMPLETED"
    },
    signals: [
      { signalType: "INTEREST", signalValue: "그룹 PT 문의", confidence: 0.76 }
    ],
    consultations: [
      { id: "consult-2", summary: "그룹 PT 관심. 주중 시간이 어려움.", rawText: "주말에 가능한 수업이 있으면 보고 싶음." }
    ]
  }
];

const serviceOptions = ["PT 1:1", "그룹 PT", "필라테스", "헬스 이용권"];

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options
  });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.status === 204 ? null : response.json();
}

function fallbackAnalysis(rawText) {
  const hot = /바로|등록|오늘|결제|시작/.test(rawText);
  return {
    saved: false,
    stateless: true,
    summary: rawText ? rawText.slice(0, 72) : "상담 메모를 입력하면 AI가 핵심 내용을 정리합니다.",
    leadTemperature: hot ? "HOT" : "WARM",
    temperature: hot ? "HOT" : "WARM",
    temperatureBasis: hot ? "즉시 행동 의향 키워드가 포함됨" : "관심은 있으나 추가 설득 정보가 필요함",
    nextBestAction: hot ? "오늘 안에 체험권 혜택을 제안하고 결제 링크를 발송" : "비교 중인 조건을 확인하고 시간표와 가격표를 먼저 전달",
    persuasionPoints: ["목표 달성 기간을 짧게 제시", "첫 방문 시 체성분 분석 제공", "관심 서비스와 연결된 프로모션 안내"],
    missingQuestions: ["희망 방문 시간", "예산 범위", "최근 운동 경험"],
    reasons: [
      { reasonType: hot ? "PRICE" : "SCHEDULE", reasonRole: "PRIMARY", reasonBasis: "상담 메모에서 우선 검토 조건으로 확인" }
    ],
    signals: [
      { signalType: "INTENT", signalValue: hot ? "즉시 등록 가능성" : "관심 단계", confidence: hot ? 0.9 : 0.68 },
      { signalType: "FOLLOW_UP", signalValue: "추가 안내 필요", confidence: 0.74 }
    ]
  };
}

function App() {
  const [activeView, setActiveView] = useState("consultation");
  const [customers, setCustomers] = useState(sampleCustomers);
  const [selectedId, setSelectedId] = useState(sampleCustomers[0].id);
  const [form, setForm] = useState({
    name: "이하늘",
    phoneNum: "010-7788-9911",
    serviceIds: ["PT 1:1"],
    inflowPath: "네이버 검색",
    rawText: "퇴근 후 7시에 운동하고 싶고 가격이 맞으면 바로 시작 가능. 다이어트 목적이고 혼자 운동하는 방법을 잘 모름."
  });
  const [analysis, setAnalysis] = useState(fallbackAnalysis(form.rawText));
  const [duplicate, setDuplicate] = useState(null);
  const [status, setStatus] = useState("샘플 데이터로 시작했습니다.");
  const [isBusy, setIsBusy] = useState(false);

  const selectedCustomer = useMemo(
    () => customers.find((customer) => customer.id === selectedId) || customers[0],
    [customers, selectedId]
  );

  const updateForm = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  const toggleService = (service) => {
    setForm((prev) => ({
      ...prev,
      serviceIds: prev.serviceIds.includes(service)
        ? prev.serviceIds.filter((item) => item !== service)
        : [...prev.serviceIds, service]
    }));
  };

  const checkDuplicate = async () => {
    setIsBusy(true);
    try {
      const result = await api(`/consultations/check-duplicate?phoneNum=${encodeURIComponent(form.phoneNum)}&name=${encodeURIComponent(form.name)}`);
      setDuplicate(result);
      setStatus(result.isDuplicate ? "기존 고객이 확인되었습니다." : "신규 상담 등록이 가능합니다.");
    } catch {
      const found = customers.find((customer) => customer.phoneNum.endsWith(form.phoneNum.slice(-4)));
      const result = { isDuplicate: Boolean(found), customer: found, canProceed: !found };
      setDuplicate(result);
      setStatus(found ? "샘플 데이터에서 기존 고객을 찾았습니다." : "샘플 기준 신규 고객입니다.");
    } finally {
      setIsBusy(false);
    }
  };

  const runPreview = async () => {
    setIsBusy(true);
    try {
      const result = await api("/consultations/analyze-preview", {
        method: "POST",
        body: JSON.stringify({ ...form, rawText: form.rawText })
      });
      setAnalysis(result);
      setStatus("AI 미리보기가 완료되었습니다.");
    } catch {
      setAnalysis(fallbackAnalysis(form.rawText));
      setStatus("백엔드 연결 전이라 로컬 AI 샘플 분석을 표시합니다.");
    } finally {
      setIsBusy(false);
    }
  };

  const saveConsultation = async () => {
    setIsBusy(true);
    try {
      const result = await api("/consultations", {
        method: "POST",
        body: JSON.stringify({ ...form, aiResult: analysis })
      });
      const saved = result.customer || {
        id: result.customerId,
        name: form.name,
        phoneNum: maskPhone(form.phoneNum),
        status: "UNREGISTERED",
        leadTemperature: analysis.leadTemperature || analysis.temperature,
        priorityScore: analysis.leadTemperature === "HOT" ? 90 : 70,
        primaryReason: analysis.reasons?.[0]?.reasonType,
        nextBestAction: analysis.nextBestAction,
        aiInsight: analysis,
        signals: analysis.signals || [],
        consultations: [result.consultation]
      };
      setCustomers((prev) => [normalizeCustomer(saved), ...prev]);
      setSelectedId(saved.id || result.customerId);
      setActiveView("customers");
      setStatus("상담 기록과 AI 인사이트를 저장했습니다.");
    } catch {
      const saved = normalizeCustomer({
        id: `local-${Date.now()}`,
        name: form.name,
        phoneNum: maskPhone(form.phoneNum),
        status: "UNREGISTERED",
        leadTemperature: analysis.leadTemperature || analysis.temperature,
        priorityScore: analysis.leadTemperature === "HOT" ? 90 : 70,
        primaryReason: analysis.reasons?.[0]?.reasonType,
        nextBestAction: analysis.nextBestAction,
        aiInsight: analysis,
        signals: analysis.signals || [],
        consultations: [{ id: `consult-${Date.now()}`, summary: analysis.summary, rawText: form.rawText }]
      });
      setCustomers((prev) => [saved, ...prev]);
      setSelectedId(saved.id);
      setActiveView("customers");
      setStatus("백엔드 연결 전이라 로컬 목록에 저장했습니다.");
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">F</span>
          <div>
            <strong>Fitback</strong>
            <small>AI 상담 관리</small>
          </div>
        </div>
        <nav className="nav-stack" aria-label="주요 화면">
          <button className={activeView === "consultation" ? "active" : ""} onClick={() => setActiveView("consultation")}>상담 등록</button>
          <button className={activeView === "customers" ? "active" : ""} onClick={() => setActiveView("customers")}>고객 인사이트</button>
          <button className={activeView === "followups" ? "active" : ""} onClick={() => setActiveView("followups")}>후속관리</button>
        </nav>
        <div className="status-card">
          <span className="dot" />
          <p>{status}</p>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Consultation Intelligence</p>
            <h1>{activeView === "consultation" ? "신규 상담 등록" : activeView === "customers" ? "고객 AI 인사이트" : "후속관리 큐"}</h1>
          </div>
          <div className="topbar-metrics">
            <Metric label="HOT" value={customers.filter((item) => item.leadTemperature === "HOT").length} />
            <Metric label="대기" value={customers.filter((item) => !item.leadTemperature).length} />
            <Metric label="고객" value={customers.length} />
          </div>
        </header>

        {activeView === "consultation" && (
          <ConsultationView
            form={form}
            analysis={analysis}
            duplicate={duplicate}
            isBusy={isBusy}
            updateForm={updateForm}
            toggleService={toggleService}
            checkDuplicate={checkDuplicate}
            runPreview={runPreview}
            saveConsultation={saveConsultation}
          />
        )}
        {activeView === "customers" && (
          <CustomerView customers={customers} selectedCustomer={selectedCustomer} setSelectedId={setSelectedId} />
        )}
        {activeView === "followups" && <FollowupView customers={customers} />}
      </section>
    </main>
  );
}

function ConsultationView({ form, analysis, duplicate, isBusy, updateForm, toggleService, checkDuplicate, runPreview, saveConsultation }) {
  return (
    <div className="consultation-grid">
      <section className="panel form-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Step 01</p>
            <h2>기본 정보</h2>
          </div>
          <button className="ghost-button" onClick={checkDuplicate} disabled={isBusy}>중복 확인</button>
        </div>
        <div className="field-row">
          <label>
            고객명
            <input value={form.name} onChange={(event) => updateForm("name", event.target.value)} />
          </label>
          <label>
            연락처
            <input value={form.phoneNum} onChange={(event) => updateForm("phoneNum", event.target.value)} />
          </label>
        </div>
        <label>
          유입 경로
          <input value={form.inflowPath} onChange={(event) => updateForm("inflowPath", event.target.value)} />
        </label>
        <div className="service-list" aria-label="관심 서비스">
          {serviceOptions.map((service) => (
            <button key={service} className={form.serviceIds.includes(service) ? "selected" : ""} onClick={() => toggleService(service)}>
              {service}
            </button>
          ))}
        </div>
        {duplicate && (
          <div className={duplicate.isDuplicate ? "notice warning" : "notice success"}>
            {duplicate.isDuplicate ? "이미 등록된 연락처입니다. 기존 고객 상세에서 상담을 추가하세요." : "신규 상담으로 진행할 수 있습니다."}
          </div>
        )}
        <label className="memo-field">
          Quick Memo
          <textarea value={form.rawText} onChange={(event) => updateForm("rawText", event.target.value)} />
        </label>
        <div className="action-row">
          <button className="secondary-button" onClick={runPreview} disabled={isBusy}>AI 미리보기</button>
          <button className="primary-button" onClick={saveConsultation} disabled={isBusy}>기록 완료 및 저장</button>
        </div>
      </section>

      <section className="panel ai-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Step 02</p>
            <h2>AI 분석 결과</h2>
          </div>
          <TemperatureBadge value={analysis.leadTemperature || analysis.temperature} />
        </div>
        <div className="summary-box">{analysis.summary}</div>
        <div className="insight-grid">
          <Insight label="판단 근거" value={analysis.temperatureBasis} />
          <Insight label="Next Best Action" value={analysis.nextBestAction} />
        </div>
        <div className="chip-section">
          <h3>설득 포인트</h3>
          <div className="chips">
            {(analysis.persuasionPoints || []).map((item) => <span key={item}>{item}</span>)}
          </div>
        </div>
        <div className="signal-list">
          <h3>상담 신호</h3>
          {(analysis.signals || []).map((signal, index) => (
            <div className="signal-item" key={`${signal.signalType}-${index}`}>
              <strong>{signal.signalType}</strong>
              <span>{signal.signalValue}</span>
              <small>{Math.round((signal.confidence || 0.7) * 100)}%</small>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function CustomerView({ customers, selectedCustomer, setSelectedId }) {
  return (
    <div className="customer-layout">
      <section className="panel list-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Customer</p>
            <h2>관리 목록</h2>
          </div>
        </div>
        <div className="customer-list">
          {customers.map((customer) => (
            <button key={customer.id} className={selectedCustomer.id === customer.id ? "customer-row active" : "customer-row"} onClick={() => setSelectedId(customer.id)}>
              <span>
                <strong>{customer.name}</strong>
                <small>{customer.phoneNum}</small>
              </span>
              <TemperatureBadge value={customer.leadTemperature} compact />
            </button>
          ))}
        </div>
      </section>
      <section className="panel detail-panel">
        <div className="detail-hero">
          <div>
            <p className="eyebrow">Analysis Detail</p>
            <h2>{selectedCustomer.name}</h2>
            <span>{selectedCustomer.phoneNum}</span>
          </div>
          <div className="score-ring">{selectedCustomer.priorityScore || 50}</div>
        </div>
        <div className="insight-grid">
          <Insight label="고객 온도" value={selectedCustomer.aiInsight?.temperatureBasis || "분석 대기 중"} />
          <Insight label="미등록 사유" value={selectedCustomer.primaryReason || "아직 없음"} />
          <Insight label="추천 행동" value={selectedCustomer.nextBestAction || "상담 후속 액션을 생성하세요."} />
        </div>
        <div className="signal-list">
          <h3>Signals</h3>
          {(selectedCustomer.signals || []).map((signal, index) => (
            <div className="signal-item" key={`${signal.signalType}-${index}`}>
              <strong>{signal.signalType}</strong>
              <span>{signal.signalValue}</span>
              <small>{Math.round((signal.confidence || 0.7) * 100)}%</small>
            </div>
          ))}
        </div>
        <div className="timeline">
          <h3>상담 기록</h3>
          {(selectedCustomer.consultations || []).map((consultation) => (
            <article key={consultation.id}>
              <strong>{consultation.summary || "상담 요약 없음"}</strong>
              <p>{consultation.rawText}</p>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}

function FollowupView({ customers }) {
  const targets = customers
    .filter((customer) => customer.nextBestAction)
    .sort((left, right) => (right.priorityScore || 0) - (left.priorityScore || 0));

  return (
    <section className="panel followup-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Follow-up</p>
          <h2>오늘 연락할 고객</h2>
        </div>
      </div>
      <div className="followup-grid">
        {targets.map((customer) => (
          <article key={customer.id} className="followup-item">
            <div>
              <strong>{customer.name}</strong>
              <TemperatureBadge value={customer.leadTemperature} compact />
            </div>
            <p>{customer.nextBestAction}</p>
            <button className="ghost-button">메시지 초안 생성</button>
          </article>
        ))}
      </div>
    </section>
  );
}

function Metric({ label, value }) {
  return (
    <div className="metric">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function Insight({ label, value }) {
  return (
    <div className="insight">
      <span>{label}</span>
      <strong>{value || "확인 필요"}</strong>
    </div>
  );
}

function TemperatureBadge({ value, compact = false }) {
  const normalized = value || "PENDING";
  return <span className={`temp-badge ${normalized.toLowerCase()} ${compact ? "compact" : ""}`}>{normalized}</span>;
}

function maskPhone(phone) {
  if (!phone || phone.length < 4) return phone;
  return `***-****-${phone.slice(-4)}`;
}

function normalizeCustomer(customer) {
  const analysis = customer.aiInsight || {};
  return {
    ...customer,
    leadTemperature: customer.leadTemperature || analysis.leadTemperature || analysis.temperature,
    priorityScore: customer.priorityScore || analysis.priorityScore || (analysis.leadTemperature === "HOT" ? 90 : 70),
    nextBestAction: customer.nextBestAction || analysis.nextBestAction,
    primaryReason: customer.primaryReason || analysis.reasons?.[0]?.reasonType,
    signals: customer.signals || analysis.signals || [],
    consultations: customer.consultations || []
  };
}

createRoot(document.getElementById("root")).render(<App />);
