import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api/v1";
const AUTH_STORAGE_KEY = "fitback.session";

const sampleAnalysis = {
  leadTemperature: "WARM",
  temperatureBasis: "운동 목적은 분명하지만 가격과 일정에 대한 확인이 더 필요합니다.",
  summary:
    "고객은 어깨 결림과 체력 저하를 해결하고 싶어합니다. 초보자 불안이 있어 부담 없는 시작 구성이 적합합니다.",
  nextBestAction: "부담 없이 시작 가능한 체험 PT와 비혼잡 시간대를 먼저 안내하세요.",
  persuasionPoints: ["초보자 맞춤 루틴", "통증 완화 중심", "유연한 스케줄"],
  signals: [
    { signalType: "INTEREST", signalValue: "MEDIUM", confidence: "MEDIUM" },
    { signalType: "OBJECTION", signalValue: "PRICE", confidence: "MEDIUM" }
  ]
};

const demoCustomers = [
  {
    id: "demo-1",
    name: "김민지",
    phoneNum: "***-****-1024",
    leadTemperature: "WARM",
    priorityScore: 74,
    status: "UNREGISTERED",
    nextBestAction: "체험 PT와 비혼잡 시간대 안내",
    primaryReason: "PRICE_CONCERN",
    aiInsight: sampleAnalysis,
    signals: sampleAnalysis.signals,
    consultations: [
      {
        id: "consult-1",
        summary: sampleAnalysis.summary,
        rawText:
          "32세 여성 회원. 어깨 결림과 체력 증진이 목적이며, 요가 경험이 조금 있음."
      }
    ]
  },
  {
    id: "demo-2",
    name: "박준호",
    phoneNum: "***-****-7788",
    leadTemperature: "HOT",
    priorityScore: 91,
    status: "UNREGISTERED",
    nextBestAction: "오늘 안에 등록 혜택과 첫 수업 가능 시간을 제안",
    primaryReason: "NEEDS_CONFIRMATION",
    aiInsight: {
      ...sampleAnalysis,
      leadTemperature: "HOT",
      temperatureBasis: "즉시 시작 의사가 있고 방문 일정까지 확인했습니다."
    },
    signals: [{ signalType: "NEXT_ACTION", signalValue: "BOOKING", confidence: "HIGH" }],
    consultations: []
  }
];

const demoFollowUps = [
  { id: "follow-1", customerId: "demo-1", status: "PENDING" },
  { id: "follow-2", customerId: "demo-2", status: "PENDING" }
];

function readStoredSession() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY));
  } catch {
    return null;
  }
}

async function api(path, options = {}, token = null) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers || {})
      }
    });
  } catch {
    throw new Error("백엔드 서버에 연결할 수 없습니다.");
  }

  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { message: text };
    }
  }
  if (!response.ok) {
    throw new Error(body?.message || body?.error || `요청 실패 (${response.status})`);
  }
  if (body && typeof body === "object" && "success" in body && "data" in body) {
    return body.data;
  }
  return body;
}

function App() {
  const [session, setSession] = useState(readStoredSession);
  const [view, setView] = useState("consultation");
  const [status, setStatus] = useState("Figma 기반 화면 준비 완료");
  const [customers, setCustomers] = useState(demoCustomers);
  const [followUps, setFollowUps] = useState(demoFollowUps);
  const [services, setServices] = useState([
    { id: "pt", name: "PT 1:1" },
    { id: "group", name: "그룹 PT" },
    { id: "pilates", name: "필라테스" }
  ]);
  const [selectedId, setSelectedId] = useState("demo-1");
  const [messageModal, setMessageModal] = useState(null);

  const token = session?.accessToken;
  const selectedCustomer = useMemo(
    () => customers.find((customer) => customer.id === selectedId) || customers[0],
    [customers, selectedId]
  );

  useEffect(() => {
    if (!token || session?.demo) return;
    refreshData(token).catch((error) => setStatus(error.message));
  }, [token, session?.demo]);

  async function refreshData(activeToken = token) {
    const [customerResult, serviceResult, followUpResult] = await Promise.allSettled([
      api("/customers", {}, activeToken),
      api("/store/services", {}, activeToken),
      api("/follow-ups", {}, activeToken)
    ]);

    if (customerResult.status === "fulfilled") {
      const content = customerResult.value?.content || [];
      if (content.length) {
        const normalized = content.map(normalizeCustomer);
        setCustomers(normalized);
        setSelectedId(normalized[0].id);
      }
    }
    if (serviceResult.status === "fulfilled" && serviceResult.value?.length) {
      setServices(serviceResult.value);
    }
    if (followUpResult.status === "fulfilled" && followUpResult.value?.length) {
      setFollowUps(followUpResult.value);
    }
    setStatus("API 데이터 동기화 완료");
  }

  function authenticate(nextSession) {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
    setStatus("로그인 완료");
  }

  function useDemoSession() {
    authenticate({
      accessToken: "demo-token",
      user: { email: "demo@fitback.ai", nickname: "관리자" },
      demo: true
    });
  }

  async function login(credentials) {
    const result = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify(credentials)
    });
    authenticate({
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      user: result.user || { email: credentials.email }
    });
  }

  async function register(credentials) {
    await api("/auth/register", {
      method: "POST",
      body: JSON.stringify({
        email: credentials.email,
        password: credentials.password,
        passwordConfirm: credentials.passwordConfirm,
        nickname: credentials.nickname || credentials.email.split("@")[0],
        agreeTerms: credentials.agreeTerms,
        agreeMarketing: false
      })
    });
    setStatus("계정 생성 완료. 이메일 인증 후 로그인하세요.");
  }

  function logout() {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setSession(null);
    setView("consultation");
    setStatus("로그아웃 완료");
  }

  async function analyzeConsultation(form) {
    if (session?.demo) {
      setStatus("데모 AI 분석 완료");
      return sampleAnalysis;
    }
    const result = await api(
      "/consultations/analyze-preview",
      { method: "POST", body: JSON.stringify(form) },
      token
    );
    setStatus("AI 분석 완료");
    return result;
  }

  async function saveConsultation(form, analysis) {
    if (session?.demo) {
      const id = `demo-${Date.now()}`;
      const customer = normalizeCustomer({
        id,
        name: form.name || "신규 고객",
        phoneNum: maskPhone(form.phoneNum),
        leadTemperature: analysis.leadTemperature || analysis.temperature,
        priorityScore: analysis.leadTemperature === "HOT" ? 90 : 72,
        nextBestAction: analysis.nextBestAction,
        primaryReason: analysis.reasons?.[0]?.reasonType || "NEEDS_CONFIRMATION",
        aiInsight: analysis,
        signals: analysis.signals,
        consultations: [{ id: `${id}-c`, summary: analysis.summary, rawText: form.rawText }]
      });
      setCustomers((prev) => [customer, ...prev]);
      setFollowUps((prev) => [{ id: `${id}-f`, customerId: id, status: "PENDING" }, ...prev]);
      setSelectedId(id);
      setView("customers");
      setStatus("데모 상담 저장 완료");
      return;
    }

    const result = await api(
      "/consultations",
      { method: "POST", body: JSON.stringify({ ...form, aiResult: analysis }) },
      token
    );
    if (result?.customer) {
      const customer = normalizeCustomer(result.customer);
      setCustomers((prev) => [customer, ...prev]);
      setSelectedId(customer.id);
    } else {
      await refreshData();
    }
    setView("customers");
    setStatus("상담과 AI 분석 저장 완료");
  }

  async function generateMessages(followUp) {
    const customer = customers.find((item) => item.id === followUp.customerId);
    setMessageModal({ customer, messages: [], isBusy: true });
    if (session?.demo) {
      setTimeout(() => {
        setMessageModal({
          customer,
          isBusy: false,
          messages: buildDemoMessages(customer)
        });
      }, 250);
      return;
    }
    try {
      const messages = await api(`/follow-ups/${followUp.id}/messages/generate`, { method: "POST" }, token);
      setMessageModal({ customer, messages, isBusy: false });
      setStatus("AI 메시지 초안 생성 완료");
    } catch (error) {
      setMessageModal({ customer, messages: buildDemoMessages(customer), isBusy: false });
      setStatus(`${error.message} 데모 메시지를 표시합니다.`);
    }
  }

  if (!session) {
    return <AuthScreen onLogin={login} onRegister={register} onDemo={useDemoSession} />;
  }

  return (
    <main className="app-shell">
      <Sidebar view={view} setView={setView} session={session} status={status} onLogout={logout} />
      <section className="workspace">
        <Topbar view={view} customers={customers} />
        {view === "dashboard" && <Dashboard customers={customers} followUps={followUps} />}
        {view === "consultation" && (
          <Consultation services={services} onAnalyze={analyzeConsultation} onSave={saveConsultation} />
        )}
        {view === "customers" && (
          <Customers customers={customers} selectedCustomer={selectedCustomer} setSelectedId={setSelectedId} />
        )}
        {view === "followups" && (
          <FollowUps customers={customers} followUps={followUps} onGenerateMessages={generateMessages} />
        )}
        {view === "reports" && <Reports customers={customers} />}
      </section>
      {messageModal && <MessageModal modal={messageModal} onClose={() => setMessageModal(null)} />}
    </main>
  );
}

function AuthScreen({ onLogin, onRegister, onDemo }) {
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    nickname: "",
    agreeTerms: false
  });
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      if (mode === "login") {
        await onLogin({ email: form.email, password: form.password });
      } else {
        await onRegister(form);
        setMode("verify");
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-visual">
        <div className="auth-logo">
          <span />
          <strong>Fitback</strong>
        </div>
        <p>Fitback와 함께 데이터 기반 맞춤형 관리를 시작하세요.</p>
      </section>
      <section className="auth-panel">
        <div className="auth-box">
          <h1>{mode === "login" ? "로그인" : mode === "register" ? "계정 생성" : "이메일 인증"}</h1>
          <p className="muted">Fitback와 함께하는 스마트한 고객 관리의 시작</p>

          {mode === "verify" ? (
            <div className="verify-card">
              <label>
                이메일 주소
                <input value={form.email} readOnly />
              </label>
              <label>
                인증번호
                <input placeholder="인증번호 6자리 입력" />
              </label>
              <p className="timer">03:23 안에 인증을 완료해주세요.</p>
              <button className="primary-button" onClick={() => setMode("login")}>인증 확인</button>
              <button className="outline-button" onClick={() => setMode("login")}>로그인으로 돌아가기</button>
            </div>
          ) : (
            <form onSubmit={submit}>
              <label>
                이메일 주소
                <input
                  type="email"
                  placeholder="example@kinetic.ai"
                  value={form.email}
                  onChange={(event) => setForm({ ...form, email: event.target.value })}
                  required
                />
              </label>
              <label>
                비밀번호
                <input
                  type="password"
                  placeholder="••••••••"
                  value={form.password}
                  onChange={(event) => setForm({ ...form, password: event.target.value })}
                  required
                />
              </label>
              {mode === "register" && (
                <>
                  <label>
                    비밀번호 확인
                    <input
                      type="password"
                      placeholder="••••••••"
                      value={form.passwordConfirm}
                      onChange={(event) => setForm({ ...form, passwordConfirm: event.target.value })}
                      required
                    />
                  </label>
                  <label className="terms">
                    <input
                      type="checkbox"
                      checked={form.agreeTerms}
                      onChange={(event) => setForm({ ...form, agreeTerms: event.target.checked })}
                      required
                    />
                    서비스 이용약관 및 개인정보 처리방침에 동의합니다.
                  </label>
                </>
              )}
              {message && <p className="form-message">{message}</p>}
              <button className="primary-button" disabled={busy}>
                {mode === "login" ? "로그인" : "계정 생성하기"}
              </button>
            </form>
          )}

          <div className="divider"><span>또는</span></div>
          <button className="google-button" onClick={onDemo}>
            <span>G</span>
            데모로 확인하기
          </button>
          <p className="auth-switch">
            {mode === "login" ? "계정이 없으신가요?" : "이미 계정이 있으신가요?"}
            <button onClick={() => setMode(mode === "login" ? "register" : "login")}>
              {mode === "login" ? "계정 생성" : "로그인"}
            </button>
          </p>
        </div>
      </section>
    </main>
  );
}

function Sidebar({ view, setView, session, status, onLogout }) {
  const items = [
    ["dashboard", "홈"],
    ["consultation", "상담고객관리"],
    ["customers", "고객 인사이트"],
    ["followups", "후속 연락 관리"],
    ["reports", "분석리포트"]
  ];
  return (
    <aside className="sidebar">
      <div className="side-logo">
        <span />
        <strong>Fitback</strong>
      </div>
      <nav>
        {items.map(([id, label]) => (
          <button key={id} className={view === id ? "active" : ""} onClick={() => setView(id)}>
            {label}
          </button>
        ))}
      </nav>
      <div className="account-card">
        <strong>{session.user?.nickname || "관리자"}</strong>
        <small>{session.user?.email || "demo@fitback.ai"}</small>
        <p>{status}</p>
      </div>
      <button className="outline-button" onClick={onLogout}>로그아웃</button>
    </aside>
  );
}

function Topbar({ view, customers }) {
  const titleMap = {
    dashboard: "홈",
    consultation: "신규 상담관리",
    customers: "고객 AI 인사이트",
    followups: "후속 연락 관리",
    reports: "분석리포트"
  };
  return (
    <header className="topbar">
      <h2>{titleMap[view]}</h2>
      <div className="topbar-actions">
        <Metric label="미등록" value={customers.filter((item) => item.status !== "REGISTERED").length} />
        <Metric label="HOT" value={customers.filter((item) => item.leadTemperature === "HOT").length} />
        <Metric label="총 고객" value={customers.length} />
      </div>
    </header>
  );
}

function Dashboard({ customers, followUps }) {
  return (
    <div className="dashboard-grid">
      <section className="panel wide">
        <h3>오늘의 운영 현황</h3>
        <div className="metric-grid">
          <Metric label="상담" value={customers.length} />
          <Metric label="후속 대기" value={followUps.filter((item) => item.status !== "DONE").length} />
          <Metric label="전환율" value="36%" />
          <Metric label="AI 분석" value={customers.filter((item) => item.aiInsight).length} />
        </div>
      </section>
      <section className="panel">
        <h3>우선 관리 고객</h3>
        <div className="stack">
          {customers
            .slice()
            .sort((a, b) => (b.priorityScore || 0) - (a.priorityScore || 0))
            .map((customer) => (
              <CustomerLine key={customer.id} customer={customer} />
            ))}
        </div>
      </section>
      <section className="panel">
        <h3>AI 상담 요약</h3>
        <p className="body-copy">
          가격 부담과 초보자 불안이 반복적으로 관찰됩니다. 바로 할인 안내를 하기보다 부담 없이 시작 가능한 구성과
          첫 방문 일정을 먼저 제안하는 것이 좋습니다.
        </p>
      </section>
    </div>
  );
}

function Consultation({ services, onAnalyze, onSave }) {
  const [form, setForm] = useState({
    name: "김민지",
    phoneNum: "01012341024",
    inflowPath: "인스타그램",
    serviceIds: ["pt"],
    rawText:
      "32세 여성 회원님. 최근 업무 스트레스로 어깨 결림이 심하다고 하심. 주 2-3회 운동하고 싶고 체력 증진과 통증 완화가 목적. 가격은 부담스럽지만 체험 수업은 관심 있음."
  });
  const [analysis, setAnalysis] = useState(sampleAnalysis);
  const [busy, setBusy] = useState(false);

  async function runAnalyze() {
    setBusy(true);
    try {
      setAnalysis(await onAnalyze(form));
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    setBusy(true);
    try {
      await onSave(form, analysis || sampleAnalysis);
    } finally {
      setBusy(false);
    }
  }

  function toggleService(serviceId) {
    setForm((prev) => ({
      ...prev,
      serviceIds: prev.serviceIds.includes(serviceId)
        ? prev.serviceIds.filter((id) => id !== serviceId)
        : [...prev.serviceIds, serviceId]
    }));
  }

  return (
    <div className="content-grid">
      <section className="panel form-panel">
        <div className="section-head">
          <h3>메모 초안</h3>
          <button className="outline-button" onClick={runAnalyze} disabled={busy}>AI 분석하기</button>
        </div>
        <textarea value={form.rawText} onChange={(event) => setForm({ ...form, rawText: event.target.value })} />
        <div className="field-grid">
          <label>
            고객명
            <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          </label>
          <label>
            연락처
            <input value={form.phoneNum} onChange={(event) => setForm({ ...form, phoneNum: event.target.value })} />
          </label>
          <label>
            유입 경로
            <input value={form.inflowPath} onChange={(event) => setForm({ ...form, inflowPath: event.target.value })} />
          </label>
        </div>
        <div className="chip-row">
          {services.map((service) => (
            <button
              key={service.id || service.name}
              className={form.serviceIds.includes(String(service.id || service.name)) ? "selected" : ""}
              onClick={() => toggleService(String(service.id || service.name))}
            >
              {service.name}
            </button>
          ))}
        </div>
        <button className="primary-button" onClick={save} disabled={busy}>상담 등록하기</button>
      </section>
      <AiSummaryCard analysis={analysis} />
      <NextActionCard analysis={analysis} />
    </div>
  );
}

function Customers({ customers, selectedCustomer, setSelectedId }) {
  return (
    <div className="customer-grid">
      <section className="panel">
        <h3>고객 목록</h3>
        <div className="stack">
          {customers.map((customer) => (
            <button
              key={customer.id}
              className={`customer-button ${selectedCustomer?.id === customer.id ? "active" : ""}`}
              onClick={() => setSelectedId(customer.id)}
            >
              <CustomerLine customer={customer} />
            </button>
          ))}
        </div>
      </section>
      <section className="panel wide">
        <div className="section-head">
          <div>
            <h3>{selectedCustomer?.name || "고객"} 기본 정보</h3>
            <p className="muted">{selectedCustomer?.phoneNum}</p>
          </div>
          <Temperature value={selectedCustomer?.leadTemperature} />
        </div>
        <AiSummaryCard analysis={selectedCustomer?.aiInsight || sampleAnalysis} embedded />
        <div className="detail-list">
          <Info label="미전환 사유" value={selectedCustomer?.primaryReason || "NEEDS_CONFIRMATION"} />
          <Info label="우선순위 점수" value={selectedCustomer?.priorityScore || 70} />
          <Info label="다음 액션" value={selectedCustomer?.nextBestAction || sampleAnalysis.nextBestAction} />
        </div>
      </section>
    </div>
  );
}

function FollowUps({ customers, followUps, onGenerateMessages }) {
  return (
    <section className="panel wide">
      <h3>오늘 연락할 고객</h3>
      <div className="follow-grid">
        {followUps.map((followUp) => {
          const customer = customers.find((item) => item.id === followUp.customerId) || customers[0];
          return (
            <article key={followUp.id} className="follow-card">
              <CustomerLine customer={customer} />
              <p>{customer?.nextBestAction || sampleAnalysis.nextBestAction}</p>
              <button className="primary-button" onClick={() => onGenerateMessages(followUp)}>메시지 생성</button>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function Reports({ customers }) {
  const warmCount = customers.filter((item) => item.leadTemperature === "WARM").length;
  const hotCount = customers.filter((item) => item.leadTemperature === "HOT").length;
  return (
    <div className="dashboard-grid">
      <section className="panel">
        <h3>온도 분포</h3>
        <div className="bar-list">
          <Bar label="HOT" value={hotCount} total={customers.length} />
          <Bar label="WARM" value={warmCount} total={customers.length} />
          <Bar label="COLD" value={customers.length - hotCount - warmCount} total={customers.length} />
        </div>
      </section>
      <section className="panel">
        <h3>주요 미전환 사유</h3>
        <div className="chip-row static">
          <span>가격 민감도</span>
          <span>일정 불일치</span>
          <span>초보자 불안</span>
        </div>
      </section>
    </div>
  );
}

function AiSummaryCard({ analysis, embedded = false }) {
  return (
    <section className={`panel ai-card ${embedded ? "embedded" : ""}`}>
      <div className="section-head">
        <h3>AI 등록 가능성 분석</h3>
        <Temperature value={analysis?.leadTemperature || analysis?.temperature} />
      </div>
      <div className="temperature-word">{analysis?.leadTemperature || analysis?.temperature || "WARM"}</div>
      <Info label="AI 상담 요약" value={analysis?.summary || sampleAnalysis.summary} />
      <Info label="판단 근거" value={analysis?.temperatureBasis || sampleAnalysis.temperatureBasis} />
      <div className="chip-row static">
        {(analysis?.persuasionPoints || sampleAnalysis.persuasionPoints).map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>
    </section>
  );
}

function NextActionCard({ analysis }) {
  return (
    <section className="panel action-card">
      <h3>다음 최적 액션</h3>
      <p>{analysis?.nextBestAction || sampleAnalysis.nextBestAction}</p>
      <Info label="설득 포인트" value={(analysis?.persuasionPoints || sampleAnalysis.persuasionPoints).join(", ")} />
    </section>
  );
}

function MessageModal({ modal, onClose }) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <section className="modal panel" onClick={(event) => event.stopPropagation()}>
        <div className="section-head">
          <h3>{modal.customer?.name || "고객"} 메시지 초안</h3>
          <button className="outline-button" onClick={onClose}>닫기</button>
        </div>
        {modal.isBusy ? (
          <p className="body-copy">AI가 후속 메시지를 생성하는 중입니다.</p>
        ) : (
          <div className="stack">
            {modal.messages.map((message) => (
              <article className="message-card" key={message.id || message.versionType}>
                <strong>{message.versionType} · {message.tonePreset}</strong>
                <p>{message.content}</p>
                <button className="outline-button" onClick={() => navigator.clipboard?.writeText(message.content)}>
                  복사하기
                </button>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
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

function Info({ label, value }) {
  return (
    <div className="info-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function CustomerLine({ customer }) {
  return (
    <div className="customer-line">
      <div>
        <strong>{customer?.name}</strong>
        <small>{customer?.phoneNum}</small>
      </div>
      <Temperature value={customer?.leadTemperature} />
    </div>
  );
}

function Temperature({ value }) {
  const normalized = value || "PENDING";
  return <span className={`temp ${normalized.toLowerCase()}`}>{normalized}</span>;
}

function Bar({ label, value, total }) {
  const width = total ? Math.round((value / total) * 100) : 0;
  return (
    <div className="bar-row">
      <span>{label}</span>
      <div><i style={{ width: `${width}%` }} /></div>
      <strong>{value}</strong>
    </div>
  );
}

function normalizeCustomer(customer) {
  const insight = customer.aiInsight || {};
  return {
    ...customer,
    leadTemperature: customer.leadTemperature || insight.leadTemperature || insight.temperature || "PENDING",
    priorityScore: customer.priorityScore || insight.priorityScore || 60,
    nextBestAction: customer.nextBestAction || insight.nextBestAction,
    primaryReason: customer.primaryReason || customer.nonConversionReasons?.[0]?.reasonType,
    signals: customer.signals || insight.signals || [],
    consultations: customer.consultations || []
  };
}

function maskPhone(phone) {
  if (!phone || phone.length < 4) return phone || "";
  return `***-****-${phone.slice(-4)}`;
}

function buildDemoMessages(customer) {
  const name = customer?.name || "고객";
  return [
    {
      versionType: "SHORT",
      tonePreset: "FRIENDLY",
      content: `${name}님, 지난 상담 내용 기준으로 부담 없이 시작 가능한 체험 수업 시간을 안내드릴게요.`
    },
    {
      versionType: "STANDARD",
      tonePreset: "PROFESSIONAL",
      content: `${name}님께 맞는 통증 완화 중심 루틴과 비혼잡 시간대를 정리했습니다. 편하신 시간에 다시 상담 도와드리겠습니다.`
    },
    {
      versionType: "DETAILED",
      tonePreset: "CARING",
      content: `${name}님이 말씀해주신 어깨 결림과 체력 증진 목표를 기준으로, 처음에는 부담 적은 구성부터 시작하실 수 있게 안내드리겠습니다.`
    }
  ];
}

createRoot(document.getElementById("root")).render(<App />);
