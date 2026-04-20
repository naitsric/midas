/**
 * Sidebar UI de MIDAS inyectado en WhatsApp Web.
 * Maneja los estados: NO_AUTH, IDLE, ANALYZING, RESULT, APPLICATION, SUBMITTED, ERROR
 */

// eslint-disable-next-line no-var
var MIDAS = window.MIDAS || {};

(function () {
  const STATES = {
    NO_AUTH: "no-auth",
    IDLE: "idle",
    ANALYZING: "analyzing",
    RESULT: "result",
    APPLICATION: "application",
    SUBMITTED: "submitted",
    ERROR: "error",
  };

  const PRODUCT_LABELS = {
    mortgage: "Crédito Hipotecario",
    auto_loan: "Crédito Vehicular",
    refinance: "Refinanciación",
    insurance: "Seguro",
  };

  const ENTITY_LABELS = {
    amount: "Monto",
    term: "Plazo",
    location: "Ubicación",
    employment: "Empleo",
    income: "Ingreso",
    product: "Producto",
  };

  let sidebarEl = null;
  let onAnalyze = null;
  let onGenerateApplication = null;
  let currentConversationId = null;
  let currentApp = null;

  function escapeHtml(value) {
    if (value == null) return "";
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function injectFonts() {
    if (document.getElementById("midas-fonts")) return;
    const preconnect1 = document.createElement("link");
    preconnect1.rel = "preconnect";
    preconnect1.href = "https://fonts.googleapis.com";
    document.head.appendChild(preconnect1);

    const preconnect2 = document.createElement("link");
    preconnect2.rel = "preconnect";
    preconnect2.href = "https://fonts.gstatic.com";
    preconnect2.crossOrigin = "anonymous";
    document.head.appendChild(preconnect2);

    const fonts = document.createElement("link");
    fonts.id = "midas-fonts";
    fonts.rel = "stylesheet";
    fonts.href =
      "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap";
    document.head.appendChild(fonts);
  }

  function init({ onAnalyzeClick, onGenerateClick }) {
    onAnalyze = onAnalyzeClick;
    onGenerateApplication = onGenerateClick;

    if (document.getElementById("midas-sidebar")) return;

    injectFonts();

    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = chrome.runtime.getURL("src/styles/sidebar.css");
    document.head.appendChild(link);

    sidebarEl = document.createElement("div");
    sidebarEl.id = "midas-sidebar";
    sidebarEl.innerHTML = buildHTML();
    document.body.appendChild(sidebarEl);

    bindButton("#midas-analyze-btn", handleAnalyzeClick);
    bindButton("#midas-generate-btn", handleGenerateClick);
    bindButton("#midas-retry-btn", handleAnalyzeClick);
    bindButton("#midas-reanalyze-btn", handleAnalyzeClick);
    bindButton("#midas-toggle", handleToggle);
    bindButton("#midas-back-btn", () => setState(STATES.RESULT));
    bindButton("#midas-send-btn", handleSendClick);
    bindButton("#midas-close-submitted-btn", () => {
      setState(STATES.IDLE);
      currentApp = null;
    });
  }

  function bindButton(selector, handler) {
    sidebarEl.querySelector(selector)?.addEventListener("click", handler);
  }

  function monogramSvg(size) {
    return `
      <svg viewBox="0 0 32 32" fill="none" width="${size}" height="${size}">
        <path d="M4 26 L4 6 L12 18 L16 12 L20 18 L28 6 L28 26"
              stroke="currentColor" stroke-width="2.5"
              stroke-linecap="round" stroke-linejoin="round" fill="none"/>
        <circle cx="16" cy="12" r="2" fill="currentColor"/>
      </svg>
    `;
  }

  function sectionHeader(num, title) {
    return `
      <div class="midas-section-header">
        <div class="midas-section-label">
          <span class="midas-section-num">${num} /</span> ${escapeHtml(title)}
        </div>
        <div class="midas-section-rule"></div>
      </div>
    `;
  }

  function buildHTML() {
    return `
      <header class="midas-header">
        <div class="midas-monogram" aria-hidden="true">${monogramSvg(17)}</div>
        <div class="midas-header-text">
          <div class="midas-header-title">Midas</div>
          <div class="midas-header-subtitle">Conversation Intelligence</div>
        </div>
        <button class="midas-toggle-btn" id="midas-toggle" title="Minimizar" aria-label="Minimizar">−</button>
      </header>

      <div class="midas-content">

        <div class="midas-state midas-state--no-auth" data-state="${STATES.NO_AUTH}">
          <div class="midas-empty">
            <div class="midas-empty-icon" aria-hidden="true">${monogramSvg(22)}</div>
            <div class="midas-empty-title">No estás conectado</div>
            <p class="midas-empty-text">
              Haz clic en el icono de MIDAS en la barra de extensiones para configurar tu API key.
            </p>
          </div>
        </div>

        <div class="midas-state midas-state--idle" data-state="${STATES.IDLE}">
          <div class="midas-chat-card">
            <div class="midas-chat-avatar" id="midas-chat-avatar">—</div>
            <div class="midas-chat-info">
              <div class="midas-chat-name" id="midas-chat-name">Abre un chat</div>
              <div class="midas-chat-meta" id="midas-chat-meta">esperando conversación</div>
            </div>
            <span class="midas-chat-dot" aria-hidden="true"></span>
          </div>

          <p class="midas-hint">
            Presiona analizar para detectar intención financiera, extraer datos clave y preparar una solicitud.
          </p>
          <button class="midas-btn midas-btn--primary midas-btn--lg" id="midas-analyze-btn">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            ANALIZAR CONVERSACIÓN
          </button>
        </div>

        <div class="midas-state midas-state--analyzing" data-state="${STATES.ANALYZING}">
          <div class="midas-spinner-wrap">
            <div class="midas-spinner"></div>
            <div class="midas-spinner-text">ANALIZANDO CONVERSACIÓN</div>
          </div>
        </div>

        <div class="midas-state midas-state--result" data-state="${STATES.RESULT}">
          <div class="midas-chat-card midas-chat-card--compact">
            <div class="midas-chat-avatar" id="midas-chat-avatar-result">—</div>
            <div class="midas-chat-info">
              <div class="midas-chat-name" id="midas-chat-name-result">Chat</div>
              <div class="midas-chat-meta" id="midas-chat-meta-result">análisis completo</div>
            </div>
            <span class="midas-chat-dot" aria-hidden="true"></span>
          </div>
          <div id="midas-result-card"></div>
          <button class="midas-btn midas-btn--primary midas-btn--lg midas-btn--send hidden" id="midas-generate-btn">
            GENERAR SOLICITUD
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <button class="midas-btn midas-btn--ghost" id="midas-reanalyze-btn">ANALIZAR DE NUEVO</button>
        </div>

        <div class="midas-state midas-state--application" data-state="${STATES.APPLICATION}">
          <div id="midas-app-content"></div>
        </div>

        <div class="midas-state midas-state--submitted" data-state="${STATES.SUBMITTED}">
          <div id="midas-submitted-content"></div>
        </div>

        <div class="midas-state midas-state--error" data-state="${STATES.ERROR}">
          <div class="midas-empty">
            <div class="midas-empty-icon midas-empty-icon--error" aria-hidden="true">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/>
                <path d="M12 7v6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <circle cx="12" cy="16.5" r="1.1" fill="currentColor"/>
              </svg>
            </div>
            <div class="midas-empty-title">Algo salió mal</div>
            <p class="midas-empty-text" id="midas-error-text"></p>
            <button class="midas-btn midas-btn--primary midas-btn--lg" id="midas-retry-btn">
              REINTENTAR
            </button>
          </div>
        </div>

      </div>
    `;
  }

  function setState(state) {
    if (!sidebarEl) return;
    sidebarEl.querySelectorAll(".midas-state").forEach((el) => {
      el.classList.toggle("active", el.dataset.state === state);
    });
  }

  function initials(name) {
    if (!name) return "—";
    const parts = String(name).trim().split(/\s+/).slice(0, 2);
    return parts.map((p) => p[0]?.toUpperCase() || "").join("") || "—";
  }

  function setChatInfo(clientName, messagesCount) {
    if (!sidebarEl) return;
    const name = clientName || null;
    const avatar = name ? initials(name) : "—";
    const displayName = name || "Abre un chat";
    const countLabel =
      typeof messagesCount === "number"
        ? `${messagesCount} msgs · chat activo`
        : name
          ? "chat activo"
          : "esperando conversación";

    [
      ["#midas-chat-avatar", avatar],
      ["#midas-chat-avatar-result", avatar],
    ].forEach(([sel, val]) => {
      const el = sidebarEl.querySelector(sel);
      if (el) el.textContent = val;
    });

    [
      ["#midas-chat-name", displayName],
      ["#midas-chat-name-result", displayName],
    ].forEach(([sel, val]) => {
      const el = sidebarEl.querySelector(sel);
      if (el) el.textContent = val;
    });

    [
      ["#midas-chat-meta", countLabel],
      ["#midas-chat-meta-result", countLabel],
    ].forEach(([sel, val]) => {
      const el = sidebarEl.querySelector(sel);
      if (el) el.textContent = val;
    });
  }

  function entityRow(key, value) {
    const label = ENTITY_LABELS[key] || key.replace(/_/g, " ");
    return `
      <div class="midas-kv-row">
        <span class="midas-kv-key">${escapeHtml(label)}</span>
        <span class="midas-kv-value">${escapeHtml(value)}</span>
      </div>
    `;
  }

  function showResult(intent, conversationId) {
    currentConversationId = conversationId;

    const card = sidebarEl?.querySelector("#midas-result-card");
    const generateBtn = sidebarEl?.querySelector("#midas-generate-btn");
    if (!card) return;

    if (intent.intent_detected) {
      const product =
        PRODUCT_LABELS[intent.product_type] || intent.product_type || "Producto financiero";
      const confidence = Math.round((intent.confidence || 0) * 100);
      const badgeText = intent.is_actionable ? "ACCIONABLE" : "BAJA CONFIANZA";
      const badgeCls = intent.is_actionable
        ? "midas-intent-badge midas-intent-badge--actionable"
        : "midas-intent-badge midas-intent-badge--low";

      const entityEntries = Object.entries(intent.entities || {}).filter(
        ([, v]) => v != null && v !== ""
      );
      const entitiesHtml = entityEntries.length
        ? `<div class="midas-kv">${entityEntries.map(([k, v]) => entityRow(k, v)).join("")}</div>`
        : `<div class="midas-kv midas-kv--empty">Sin datos extraídos</div>`;

      card.innerHTML = `
        <div class="midas-section">
          ${sectionHeader("01", "Intención")}
          <div class="midas-intent-card">
            <div class="midas-intent-row">
              <span class="${badgeCls}">${badgeText}</span>
              <span class="midas-intent-confidence">${confidence}%</span>
            </div>
            <div class="midas-intent-product">${escapeHtml(product)}</div>
            <div class="midas-confidence-bar">
              <div class="midas-confidence-fill" style="width: ${confidence}%"></div>
            </div>
          </div>
        </div>

        <div class="midas-section">
          ${sectionHeader("02", "Datos extraídos")}
          ${entitiesHtml}
        </div>

        ${intent.summary
          ? `<div class="midas-section">
               ${sectionHeader("03", "Resumen IA")}
               <div class="midas-summary-card">${escapeHtml(intent.summary)}</div>
             </div>`
          : ""}
      `;

      generateBtn?.classList.toggle("hidden", !intent.is_actionable);
    } else {
      card.innerHTML = `
        <div class="midas-section">
          ${sectionHeader("01", "Intención")}
          <div class="midas-intent-card midas-intent-card--empty">
            <div class="midas-intent-row">
              <span class="midas-intent-badge midas-intent-badge--none">SIN INTENCIÓN</span>
            </div>
            <div class="midas-intent-product midas-intent-product--muted">
              No se detectó intención financiera
            </div>
          </div>
        </div>
        ${intent.summary
          ? `<div class="midas-section">
               ${sectionHeader("02", "Resumen IA")}
               <div class="midas-summary-card">${escapeHtml(intent.summary)}</div>
             </div>`
          : ""}
      `;
      generateBtn?.classList.add("hidden");
    }

    setState(STATES.RESULT);
  }

  function showApplication(app) {
    currentApp = app;
    const container = sidebarEl?.querySelector("#midas-app-content");
    if (!container) return;

    const completeness = Math.round((app.applicant?.completeness ?? 0) * 100);
    const statusLabel = (app.status_label || "Borrador").toUpperCase();
    const appId = app.id || "APP-0048";

    const applicantRows = [
      ["Nombre", app.applicant?.full_name],
      ["Teléfono", app.applicant?.phone],
      ["Ingreso", app.applicant?.estimated_income],
      ["Empleo", app.applicant?.employment_type],
    ].filter(([, v]) => v);

    const productRows = [
      ["Tipo", app.product_request?.product_label],
      ["Monto", app.product_request?.amount],
      ["Plazo", app.product_request?.term],
      ["Ubicación", app.product_request?.location],
    ].filter(([, v]) => v);

    const missing = Array.isArray(app.missing) ? app.missing : [];

    container.innerHTML = `
      <button class="midas-back-link" id="midas-back-btn">← Volver al análisis</button>

      <div class="midas-app-hero">
        <div class="midas-app-hero-row">
          <span class="midas-app-status">${escapeHtml(statusLabel)}</span>
          <span class="midas-app-id">${escapeHtml(appId)}</span>
          <span class="midas-app-completeness">${completeness}%</span>
        </div>
        <div class="midas-app-product">${escapeHtml(app.product_request?.product_label || "Solicitud de crédito")}</div>
        <div class="midas-app-sub">${escapeHtml(app.applicant?.full_name || "")}${app.product_request?.amount ? ` · ${escapeHtml(app.product_request.amount)}` : ""}</div>
        <div class="midas-completeness-bar">
          <div class="midas-completeness-fill" style="width: ${completeness}%"></div>
        </div>
      </div>

      ${applicantRows.length
        ? `<div class="midas-section">
             ${sectionHeader("01", "Solicitante")}
             <div class="midas-kv">
               ${applicantRows.map(([k, v]) => entityRow(k.toLowerCase(), v)).join("")}
             </div>
           </div>`
        : ""}

      ${productRows.length
        ? `<div class="midas-section">
             ${sectionHeader("02", "Producto")}
             <div class="midas-kv">
               ${productRows.map(([k, v]) => entityRow(k.toLowerCase(), v)).join("")}
             </div>
           </div>`
        : ""}

      ${missing.length
        ? `<div class="midas-section">
             ${sectionHeader("03", `Faltan · ${missing.length}`)}
             <div class="midas-missing-list">
               ${missing.map((m) => `
                 <div class="midas-missing-item">
                   <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                     <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/>
                     <path d="M12 8v5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                     <circle cx="12" cy="16" r="1" fill="currentColor"/>
                   </svg>
                   <span class="midas-missing-text">${escapeHtml(m)}</span>
                   <span class="midas-missing-action">PEDIR</span>
                 </div>
               `).join("")}
             </div>
           </div>`
        : ""}

      <button class="midas-btn midas-btn--primary midas-btn--lg midas-btn--send" id="midas-send-btn">
        ENVIAR SOLICITUD
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <p class="midas-send-note">
        Se enviará al portal del asesor y notificaremos al cliente por WhatsApp.
      </p>
    `;

    container.querySelector("#midas-back-btn")?.addEventListener("click", () => {
      setState(STATES.RESULT);
    });
    container.querySelector("#midas-send-btn")?.addEventListener("click", handleSendClick);

    setState(STATES.APPLICATION);
  }

  function showSubmitted() {
    const container = sidebarEl?.querySelector("#midas-submitted-content");
    if (!container) return;
    const app = currentApp || {};
    const appId = app.id || "APP-0048";
    const now = new Date();
    const hh = String(now.getHours()).padStart(2, "0");
    const mm = String(now.getMinutes()).padStart(2, "0");

    const confettiPieces = Array.from({ length: 24 }, (_, i) => {
      const x = Math.random() * 320;
      const dx = (Math.random() - 0.5) * 120;
      const delay = Math.random() * 200;
      const dur = 1200 + Math.random() * 800;
      const size = 4 + Math.random() * 5;
      const colors = ["#00E676", "#FFD54F", "#42A5F5", "#B388FF"];
      const color = colors[i % 4];
      const rot = Math.random() * 360;
      return `<div class="midas-confetti-piece" style="
        left: 0; top: 0;
        --x: ${x}px; --dx: ${dx}px;
        width: ${size}px; height: ${size * 0.4}px;
        background: ${color};
        animation: midas-confetti ${dur}ms ease-out ${delay}ms forwards;
        transform: translate(${x}px, -20px) rotate(${rot}deg);
      "></div>`;
    }).join("");

    const steps = [
      { done: true, title: "Solicitud creada", sub: "Datos extraídos de la conversación" },
      { done: true, title: "Enviada al portal", sub: "Disponible para comité de crédito" },
      { done: false, title: "Cliente notificado", sub: "Pedimos: doc. identidad, certificado laboral" },
      { done: false, title: "Pre-aprobación", sub: "ETA < 24 horas" },
    ];

    container.innerHTML = `
      <div class="midas-confetti">${confettiPieces}</div>

      <div class="midas-success-hero">
        <div class="midas-success-circle" aria-hidden="true">
          <svg width="42" height="42" viewBox="0 0 24 24" fill="none">
            <path d="M5 12.5l5 5L20 7" stroke="#000" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="midas-success-check"/>
          </svg>
        </div>
        <div class="midas-success-eyebrow">SOLICITUD ENVIADA</div>
        <div class="midas-success-title">¡Primera solicitud!</div>
        <p class="midas-success-sub">
          De una conversación de WhatsApp a una solicitud estructurada en <strong>23 segundos</strong>.
        </p>
      </div>

      <div class="midas-receipt">
        <div class="midas-app-hero-row">
          <span class="midas-app-status midas-app-status--sent">ENVIADA</span>
          <span class="midas-app-id">${escapeHtml(appId)}</span>
          <span class="midas-app-timestamp">HOY · ${hh}:${mm}</span>
        </div>
        <div class="midas-receipt-product">${escapeHtml(app.product_request?.product_label || "Solicitud de crédito")}</div>
        <div class="midas-receipt-sub">${escapeHtml(app.applicant?.full_name || "")}${app.product_request?.amount ? ` · ${escapeHtml(app.product_request.amount)}` : ""}</div>
      </div>

      <div class="midas-section">
        ${sectionHeader("01", "Siguientes pasos")}
        <div class="midas-timeline">
          ${steps.map((s) => `
            <div class="midas-timeline-item ${s.done ? "is-done" : ""}">
              <span class="midas-timeline-dot" aria-hidden="true">
                ${s.done ? `<svg width="8" height="8" viewBox="0 0 24 24" fill="none"><path d="M5 12l5 5L20 7" stroke="#000" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/></svg>` : ""}
              </span>
              <div class="midas-timeline-title">${escapeHtml(s.title)}</div>
              <div class="midas-timeline-sub">${escapeHtml(s.sub)}</div>
            </div>
          `).join("")}
        </div>
      </div>

      <div class="midas-btn-row">
        <button class="midas-btn midas-btn--primary" id="midas-view-portal-btn">
          VER EN PORTAL
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M7 17L17 7M8 7h9v9" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <button class="midas-btn midas-btn--ghost" id="midas-close-submitted-btn">CERRAR</button>
      </div>
    `;

    container.querySelector("#midas-close-submitted-btn")?.addEventListener("click", () => {
      setState(STATES.IDLE);
      currentApp = null;
    });

    setState(STATES.SUBMITTED);
  }

  function showError(message) {
    const el = sidebarEl?.querySelector("#midas-error-text");
    if (el) el.textContent = message;
    setState(STATES.ERROR);
  }

  function handleAnalyzeClick() {
    if (onAnalyze) onAnalyze();
  }

  function handleGenerateClick() {
    if (onGenerateApplication && currentConversationId) {
      onGenerateApplication(currentConversationId);
    }
  }

  function handleSendClick(e) {
    const btn = e?.currentTarget;
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" class="midas-btn-spinner" aria-hidden="true">
          <circle cx="12" cy="12" r="9" stroke="#000" stroke-opacity="0.2" stroke-width="2.5" fill="none"/>
          <path d="M12 3a9 9 0 019 9" stroke="#000" stroke-width="2.5" fill="none" stroke-linecap="round"/>
        </svg>
        ENVIANDO…
      `;
    }
    setTimeout(() => {
      showSubmitted();
    }, 900);
  }

  function handleToggle() {
    sidebarEl?.classList.toggle("midas-collapsed");
    const btn = sidebarEl?.querySelector("#midas-toggle");
    if (btn) {
      btn.textContent = sidebarEl.classList.contains("midas-collapsed") ? "+" : "−";
    }
  }

  MIDAS.sidebar = {
    STATES,
    init,
    setState,
    setChatInfo,
    showResult,
    showApplication,
    showSubmitted,
    showError,
    getConversationId: () => currentConversationId,
  };
})();
