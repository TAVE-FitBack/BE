import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "/api/v1";
const AUTH_STORAGE_KEY = "fitback.session";
const VIEW_TITLES = {
  dashboard: "대시보드",
  consultation: "신규 상담 등록",
  customers: "고객 AI 인사이트",
  followups: "후속관리 큐",
  events: "이벤트 관리",
  store: "매장 관리"
};

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
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers || {})
      },
      ...options
    });
  } catch {
    throw new Error("백엔드 서버에 연결할 수 없습니다.");
  }
  if (!response.ok) {
    const rawMessage = await response.text();
    let message = rawMessage;
    try {
      const parsed = JSON.parse(rawMessage);
      message = parsed.message || parsed.error || parsed.code || rawMessage;
    } catch {
      message = rawMessage;
    }
    throw new Error(message || `요청에 실패했습니다. (${response.status})`);
  }
  if (response.status === 204) return null;
  const body = await response.json();
  if (body && typeof body === "object" && "success" in body && "data" in body) {
    return body.data;
  }
  return body;
}

function App() {
  const [session, setSession] = useState(readStoredSession);
  const [activeView, setActiveView] = useState("consultation");
  const [customers, setCustomers] = useState([]);
  const [services, setServices] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [form, setForm] = useState({
    name: "",
    phoneNum: "",
    serviceIds: [],
    inflowPath: "",
    rawText: ""
  });
  const [analysis, setAnalysis] = useState(null);
  const [duplicate, setDuplicate] = useState(null);
  const [status, setStatus] = useState("로그인 후 실제 데이터를 불러옵니다.");
  const [isBusy, setIsBusy] = useState(false);
  const [followUps, setFollowUps] = useState([]);
  const [dashboard, setDashboard] = useState(null);
  const [priorityCustomers, setPriorityCustomers] = useState([]);
  const [storeProfile, setStoreProfile] = useState(null);
  const [events, setEvents] = useState([]);
  const [messageModal, setMessageModal] = useState(null);

  const token = session?.accessToken || null;
  const selectedCustomer = useMemo(
    () => customers.find((customer) => customer.id === selectedId) || customers[0] || null,
    [customers, selectedId]
  );

  const loadCustomers = async (preferredId = null) => {
    const result = await api("/customers", {}, token);
    const content = result.content || [];
    setCustomers(content.map(normalizeCustomer));
    setSelectedId(preferredId || content[0]?.id || null);
    setStatus(content.length ? "고객 목록을 불러왔습니다." : "등록된 고객이 없습니다.");
  };

  const loadFollowUps = async () => {
    const result = await api("/follow-ups", {}, token);
    setFollowUps(result || []);
  };

  const loadDashboard = async () => {
    const [summary, priority] = await Promise.all([
      api("/dashboard/summary", {}, token),
      api("/dashboard/priority-customers", {}, token)
    ]);
    setDashboard(summary);
    setPriorityCustomers(priority || []);
  };

  const loadStoreProfile = async () => {
    const result = await api("/store", {}, token);
    setStoreProfile(result && Object.keys(result).length ? result : null);
  };

  const loadEvents = async () => {
    const result = await api("/store/events", {}, token);
    setEvents(result || []);
  };

  useEffect(() => {
    if (!token) return;
    loadCustomers()
      .catch(() => setStatus("고객 목록을 불러오지 못했습니다."));
    api("/store/services", {}, token)
      .then((result) => setServices(result || []))
      .catch(() => setServices([]));
    loadFollowUps().catch(() => setFollowUps([]));
    loadDashboard().catch(() => { setDashboard(null); setPriorityCustomers([]); });
    loadStoreProfile().catch(() => setStoreProfile(null));
    loadEvents().catch(() => setEvents([]));
  }, [token]);

  const authenticate = (nextSession) => {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
    setStatus("로그인되었습니다.");
  };

  const logout = () => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setSession(null);
    setActiveView("consultation");
    setCustomers([]);
    setServices([]);
    setSelectedId(null);
    setAnalysis(null);
    setFollowUps([]);
    setDashboard(null);
    setPriorityCustomers([]);
    setStoreProfile(null);
    setEvents([]);
    setMessageModal(null);
  };

  const login = async (credentials) => {
    const result = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify(credentials)
    });
    authenticate({
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
      user: result.user || { email: credentials.email }
    });
  };

  const register = async (credentials) => {
    await api("/auth/register", {
      method: "POST",
      body: JSON.stringify({
        email: credentials.email,
        password: credentials.password,
        passwordConfirm: credentials.passwordConfirm,
        nickname: credentials.nickname,
        agreeTerms: credentials.agreeTerms,
        agreeMarketing: false
      })
    });
    await login(credentials);
  };

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
      const result = await api(`/consultations/check-duplicate?phoneNum=${encodeURIComponent(form.phoneNum)}&name=${encodeURIComponent(form.name)}`, {}, token);
      setDuplicate(result);
      setStatus(result.isDuplicate ? "기존 고객이 확인되었습니다." : "신규 상담 등록이 가능합니다.");
    } catch {
      setDuplicate(null);
      setStatus("중복 확인 요청에 실패했습니다.");
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
      }, token);
      setAnalysis(result);
      setStatus("AI 미리보기가 완료되었습니다.");
    } catch {
      setStatus("AI 미리보기 요청에 실패했습니다.");
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
      }, token);
      if (result.customer) {
        const saved = normalizeCustomer(result.customer);
        setCustomers((prev) => [saved, ...prev]);
        setSelectedId(saved.id);
      } else {
        await loadCustomers(result.customerId);
      }
      setActiveView("customers");
      setStatus("상담 기록과 AI 인사이트를 저장했습니다.");
      loadFollowUps().catch(() => {});
      loadDashboard().catch(() => {});
    } catch {
      setStatus("상담 저장 요청에 실패했습니다.");
    } finally {
      setIsBusy(false);
    }
  };

  const saveStoreProfile = async (data) => {
    const result = await api("/store", {
      method: storeProfile ? "PUT" : "POST",
      body: JSON.stringify(data)
    }, token);
    setStoreProfile(result);
    setStatus("매장 정보를 저장했습니다.");
  };

  const createService = async (name) => {
    const result = await api("/store/services", {
      method: "POST",
      body: JSON.stringify({ name })
    }, token);
    setServices((prev) => [...prev, result]);
    setStatus("서비스를 추가했습니다.");
  };

  const createEvent = async (data) => {
    const result = await api("/store/events", {
      method: "POST",
      body: JSON.stringify(data)
    }, token);
    setEvents((prev) => [result, ...prev]);
    setStatus("이벤트를 생성했습니다.");
  };

  const openMessageModal = async (followUp) => {
    const customer = customers.find((item) => item.id === followUp.customerId);
    setMessageModal({ followUpId: followUp.id, customerName: customer?.name || "고객", messages: [], isBusy: true });
    try {
      const generated = await api(`/follow-ups/${followUp.id}/messages/generate`, { method: "POST" }, token);
      setMessageModal((prev) => prev && { ...prev, messages: generated || [], isBusy: false });
    } catch {
      setMessageModal((prev) => prev && { ...prev, isBusy: false });
      setStatus("메시지 초안 생성에 실패했습니다.");
    }
  };

  const closeMessageModal = () => setMessageModal(null);

  const copyMessage = async (messageId) => {
    const updated = await api(`/messages/${messageId}/copy`, { method: "PATCH" }, token);
    setMessageModal((prev) => prev && {
      ...prev,
      messages: prev.messages.map((message) => (message.id === messageId ? updated : message))
    });
  };

  const sendMessage = async (messageId) => {
    const updated = await api(`/messages/${messageId}/send`, { method: "POST" }, token);
    setMessageModal((prev) => prev && {
      ...prev,
      messages: prev.messages.map((message) => (message.id === messageId ? updated : message))
    });
    setStatus("메시지 발송을 요청했습니다.");
  };

  if (!session) {
    return (
      <AuthScreen
        onLogin={login}
        onRegister={register}
      />
    );
  }

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
          <button className={activeView === "dashboard" ? "active" : ""} onClick={() => setActiveView("dashboard")}>대시보드</button>
          <button className={activeView === "consultation" ? "active" : ""} onClick={() => setActiveView("consultation")}>상담 등록</button>
          <button className={activeView === "customers" ? "active" : ""} onClick={() => setActiveView("customers")}>고객 인사이트</button>
          <button className={activeView === "followups" ? "active" : ""} onClick={() => setActiveView("followups")}>후속관리</button>
          <button className={activeView === "events" ? "active" : ""} onClick={() => setActiveView("events")}>이벤트</button>
          <button className={activeView === "store" ? "active" : ""} onClick={() => setActiveView("store")}>매장 관리</button>
        </nav>
        <div className="status-card">
          <span className="dot" />
          <p>{session.user?.email || "Fitback 사용자"} · {status}</p>
        </div>
        <button className="ghost-button logout-button" onClick={logout}>로그아웃</button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Consultation Intelligence</p>
            <h1>{VIEW_TITLES[activeView] || "Fitback"}</h1>
          </div>
          <div className="topbar-metrics">
            <Metric label="HOT" value={customers.filter((item) => item.leadTemperature === "HOT").length} />
            <Metric label="대기" value={customers.filter((item) => !item.leadTemperature).length} />
            <Metric label="고객" value={customers.length} />
          </div>
        </header>

        {activeView === "dashboard" && (
          <DashboardView dashboard={dashboard} priorityCustomers={priorityCustomers} />
        )}
        {activeView === "consultation" && (
          <ConsultationView
            form={form}
            services={services}
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
        {activeView === "followups" && (
          <FollowupView customers={customers} followUps={followUps} onGenerateMessages={openMessageModal} />
        )}
        {activeView === "events" && (
          <EventsView events={events} services={services} onCreateEvent={createEvent} />
        )}
        {activeView === "store" && (
          <StoreView storeProfile={storeProfile} services={services} onSaveProfile={saveStoreProfile} onCreateService={createService} />
        )}
      </section>
      {messageModal && (
        <MessageModal
          modal={messageModal}
          onClose={closeMessageModal}
          onCopy={copyMessage}
          onSend={sendMessage}
        />
      )}
    </main>
  );
}

function AuthScreen({ onLogin, onRegister }) {
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    nickname: "",
    agreeTerms: false
  });
  const [message, setMessage] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  const update = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async (event) => {
    event.preventDefault();
    setIsBusy(true);
    setMessage("");
    try {
      if (mode === "login") {
        await onLogin(form);
      } else {
        await onRegister(form);
      }
    } catch (error) {
      setMessage(error.message || (mode === "login" ? "로그인에 실패했습니다. 계정 정보를 확인하세요." : "계정 생성에 실패했습니다."));
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <main className="auth-shell">
      <section className="auth-visual">
        <div className="brand auth-brand">
          <span className="brand-mark">F</span>
          <div>
            <strong>Fitback</strong>
            <small>AI 상담 관리</small>
          </div>
        </div>
        <div className="auth-copy">
          <p className="eyebrow">Consultation Intelligence</p>
          <h1>Fitback</h1>
          <p>상담 메모를 고객 인사이트와 후속관리 큐로 연결합니다.</p>
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="auth-tabs" role="tablist" aria-label="인증 방식">
            <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>로그인</button>
            <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>계정 생성</button>
          </div>
          <form onSubmit={submit}>
            <div>
              <p className="eyebrow">{mode === "login" ? "Welcome Back" : "Create Account"}</p>
              <h2>{mode === "login" ? "로그인" : "관리자 계정 생성"}</h2>
            </div>
            {mode === "register" && (
              <label>
                닉네임
                <input value={form.nickname} onChange={(event) => update("nickname", event.target.value)} autoComplete="nickname" required />
              </label>
            )}
            <label>
              이메일
              <input type="email" value={form.email} onChange={(event) => update("email", event.target.value)} autoComplete="email" required />
            </label>
            <label>
              비밀번호
              <input type="password" value={form.password} onChange={(event) => update("password", event.target.value)} autoComplete={mode === "login" ? "current-password" : "new-password"} required />
            </label>
            {mode === "register" && (
              <label>
                비밀번호 확인
                <input type="password" value={form.passwordConfirm} onChange={(event) => update("passwordConfirm", event.target.value)} autoComplete="new-password" required />
              </label>
            )}
            {mode === "register" && (
              <label className="checkbox-field">
                <input type="checkbox" checked={form.agreeTerms} onChange={(event) => update("agreeTerms", event.target.checked)} required />
                이용약관에 동의합니다
              </label>
            )}
            {message && <div className="notice warning">{message}</div>}
            <div className="auth-actions">
              <button className="primary-button" type="submit" disabled={isBusy}>
                {mode === "login" ? "로그인" : "계정 생성 후 시작"}
              </button>
            </div>
          </form>
        </div>
      </section>
    </main>
  );
}

function ConsultationView({ form, services, analysis, duplicate, isBusy, updateForm, toggleService, checkDuplicate, runPreview, saveConsultation }) {
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
          {services.length === 0 && <span className="inline-empty">등록된 서비스가 없습니다.</span>}
          {services.map((service) => {
            const serviceId = String(service.id || service.name);
            return (
            <button key={serviceId} className={form.serviceIds.includes(serviceId) ? "selected" : ""} onClick={() => toggleService(serviceId)}>
              {service.name || serviceId}
            </button>
            );
          })}
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
          <TemperatureBadge value={analysis?.leadTemperature || analysis?.temperature} />
        </div>
        {analysis ? (
          <>
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
                  <small>{Math.round((Number(signal.confidence) || 0.7) * 100)}%</small>
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="empty-state">
            <strong>AI 분석 결과가 없습니다.</strong>
            <span>상담 메모를 입력한 뒤 미리보기를 실행하세요.</span>
          </div>
        )}
      </section>
    </div>
  );
}

function CustomerView({ customers, selectedCustomer, setSelectedId }) {
  if (!selectedCustomer) {
    return (
      <section className="panel detail-panel">
        <div className="empty-state">
          <strong>등록된 고객이 없습니다.</strong>
          <span>상담 등록에서 첫 고객을 저장하면 인사이트 목록이 생성됩니다.</span>
        </div>
      </section>
    );
  }

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

function FollowupView({ customers, followUps, onGenerateMessages }) {
  const targets = followUps
    .filter((followUp) => followUp.status !== "DONE")
    .map((followUp) => ({ followUp, customer: customers.find((item) => item.id === followUp.customerId) }))
    .sort((left, right) => (right.customer?.priorityScore || 0) - (left.customer?.priorityScore || 0));

  return (
    <section className="panel followup-panel">
      <div className="panel-head">
        <div>
          <p className="eyebrow">Follow-up</p>
          <h2>오늘 연락할 고객</h2>
        </div>
      </div>
      <div className="followup-grid">
        {targets.length === 0 && (
          <div className="empty-state">
            <strong>후속관리 대상이 없습니다.</strong>
            <span>상담 저장 후 추천 행동이 생성되면 이곳에 표시됩니다.</span>
          </div>
        )}
        {targets.map(({ followUp, customer }) => (
          <article key={followUp.id} className="followup-item">
            <div>
              <strong>{customer?.name || "고객"}</strong>
              <TemperatureBadge value={customer?.leadTemperature} compact />
            </div>
            <p>{customer?.nextBestAction || followUp.persuasionPoints || "추천 행동이 아직 없습니다."}</p>
            <button className="ghost-button" onClick={() => onGenerateMessages(followUp)}>메시지 초안 생성</button>
          </article>
        ))}
      </div>
    </section>
  );
}

function DashboardView({ dashboard, priorityCustomers }) {
  return (
    <div className="dashboard-grid">
      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Overview</p>
            <h2>오늘의 운영 현황</h2>
          </div>
        </div>
        <div className="topbar-metrics dashboard-metrics">
          <Metric label="오늘 상담" value={dashboard?.todayConsultCount ?? 0} />
          <Metric label="등록 전환율" value={`${dashboard?.monthlyRegistrationRate ?? 0}%`} />
          <Metric label="대기 후속관리" value={dashboard?.pendingFollowUpCount ?? 0} />
          <Metric label="지연 후속관리" value={dashboard?.overdueFollowUpCount ?? 0} />
        </div>
      </section>
      <section className="panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Priority</p>
            <h2>우선 관리 고객</h2>
          </div>
        </div>
        {priorityCustomers.length === 0 ? (
          <div className="empty-state">
            <strong>우선 관리할 고객이 없습니다.</strong>
            <span>상담이 등록되면 우선순위가 높은 고객이 이곳에 표시됩니다.</span>
          </div>
        ) : (
          <div className="customer-list">
            {priorityCustomers.map((customer) => (
              <div key={customer.id} className="customer-row">
                <span>
                  <strong>{customer.name}</strong>
                  <small>{customer.phoneNum}</small>
                </span>
                <span>{customer.priorityScore ?? 50}</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function EventsView({ events, services, onCreateEvent }) {
  const [form, setForm] = useState({ name: "", serviceId: "" });
  const [isBusy, setIsBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (!form.name || !form.serviceId) return;
    setIsBusy(true);
    try {
      await onCreateEvent(form);
      setForm({ name: "", serviceId: "" });
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <div className="consultation-grid">
      <section className="panel form-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">New</p>
            <h2>이벤트 생성</h2>
          </div>
        </div>
        <form onSubmit={submit}>
          <label>
            이벤트명
            <input value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} required />
          </label>
          <label>
            대상 서비스
            <select value={form.serviceId} onChange={(event) => setForm((prev) => ({ ...prev, serviceId: event.target.value }))} required>
              <option value="">서비스 선택</option>
              {services.map((service) => (
                <option key={service.id} value={service.id}>{service.name}</option>
              ))}
            </select>
          </label>
          <div className="action-row">
            <button className="primary-button" type="submit" disabled={isBusy}>이벤트 생성</button>
          </div>
        </form>
      </section>
      <section className="panel ai-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">History</p>
            <h2>생성된 이벤트</h2>
          </div>
        </div>
        {events.length === 0 ? (
          <div className="empty-state">
            <strong>생성된 이벤트가 없습니다.</strong>
            <span>왼쪽에서 첫 이벤트를 만들어보세요.</span>
          </div>
        ) : (
          <div className="signal-list">
            {events.map((event) => (
              <div className="signal-item" key={event.id}>
                <strong>{event.name}</strong>
                <span>대상 {event.targetCount ?? 0}명</span>
                <small>{event.createdAt ? new Date(event.createdAt).toLocaleDateString() : ""}</small>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function StoreView({ storeProfile, services, onSaveProfile, onCreateService }) {
  const [profileForm, setProfileForm] = useState({ name: storeProfile?.name || "", storeType: storeProfile?.storeType || "GYM" });
  const [serviceName, setServiceName] = useState("");
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => {
    setProfileForm({ name: storeProfile?.name || "", storeType: storeProfile?.storeType || "GYM" });
  }, [storeProfile]);

  const submitProfile = async (event) => {
    event.preventDefault();
    setIsBusy(true);
    try {
      await onSaveProfile(profileForm);
    } finally {
      setIsBusy(false);
    }
  };

  const submitService = async (event) => {
    event.preventDefault();
    if (!serviceName.trim()) return;
    setIsBusy(true);
    try {
      await onCreateService(serviceName.trim());
      setServiceName("");
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <div className="consultation-grid">
      <section className="panel form-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Profile</p>
            <h2>매장 정보</h2>
          </div>
        </div>
        <form onSubmit={submitProfile}>
          <label>
            매장명
            <input value={profileForm.name} onChange={(event) => setProfileForm((prev) => ({ ...prev, name: event.target.value }))} required />
          </label>
          <label>
            매장 유형
            <select value={profileForm.storeType} onChange={(event) => setProfileForm((prev) => ({ ...prev, storeType: event.target.value }))}>
              <option value="GYM">GYM</option>
              <option value="OTHER">OTHER</option>
            </select>
          </label>
          <div className="action-row">
            <button className="primary-button" type="submit" disabled={isBusy}>저장</button>
          </div>
        </form>
      </section>
      <section className="panel ai-panel">
        <div className="panel-head">
          <div>
            <p className="eyebrow">Services</p>
            <h2>제공 서비스</h2>
          </div>
        </div>
        <div className="chip-section">
          <div className="chips">
            {services.length === 0 && <span className="inline-empty">등록된 서비스가 없습니다.</span>}
            {services.map((service) => <span key={service.id || service.name}>{service.name}</span>)}
          </div>
        </div>
        <form onSubmit={submitService} className="field-row">
          <label>
            새 서비스명
            <input value={serviceName} onChange={(event) => setServiceName(event.target.value)} />
          </label>
          <button className="secondary-button" type="submit" disabled={isBusy} style={{ marginTop: 28 }}>추가</button>
        </form>
      </section>
    </div>
  );
}

function MessageModal({ modal, onClose, onCopy, onSend }) {
  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal-card" onClick={(event) => event.stopPropagation()}>
        <div className="panel-head">
          <div>
            <p className="eyebrow">Message Drafts</p>
            <h2>{modal.customerName}님께 보낼 메시지</h2>
          </div>
          <button className="ghost-button" onClick={onClose}>닫기</button>
        </div>
        {modal.isBusy ? (
          <div className="empty-state">
            <strong>메시지 초안을 생성하는 중입니다...</strong>
          </div>
        ) : modal.messages.length === 0 ? (
          <div className="empty-state">
            <strong>생성된 메시지 초안이 없습니다.</strong>
          </div>
        ) : (
          <div className="signal-list">
            {modal.messages.map((message) => (
              <div className="signal-item message-item" key={message.id}>
                <strong>{message.versionType} · {message.tonePreset}</strong>
                <span>{message.content}</span>
                <div className="action-row">
                  <button className="ghost-button" onClick={() => onCopy(message.id)}>복사 표시</button>
                  <button className="secondary-button" onClick={() => onSend(message.id)}>발송</button>
                  <small>{message.deliveryStatus}</small>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
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
