/**
 * Sidebar UI de MIDAS inyectado en WhatsApp Web.
 * Maneja los estados: NO_AUTH, IDLE, ANALYZING, RESULT, APPLICATION, ERROR
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
    ERROR: "error",
  };

  let sidebarEl = null;
  let onAnalyze = null;
  let onGenerateApplication = null;
  let currentConversationId = null;

  function init({ onAnalyzeClick, onGenerateClick }) {
    onAnalyze = onAnalyzeClick;
    onGenerateApplication = onGenerateClick;

    if (document.getElementById("midas-sidebar")) return;

    // Inyectar CSS
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = chrome.runtime.getURL("src/styles/sidebar.css");
    document.head.appendChild(link);

    sidebarEl = document.createElement("div");
    sidebarEl.id = "midas-sidebar";
    sidebarEl.innerHTML = buildHTML();
    document.body.appendChild(sidebarEl);

    // Event listeners
    bindButton("#midas-analyze-btn", handleAnalyzeClick);
    bindButton("#midas-generate-btn", handleGenerateClick);
    bindButton("#midas-retry-btn", handleAnalyzeClick);
    bindButton("#midas-reanalyze-btn", handleAnalyzeClick);
    bindButton("#midas-toggle", handleToggle);
    bindButton("#midas-back-btn", () => setState(STATES.RESULT));
  }

  function bindButton(selector, handler) {
    sidebarEl.querySelector(selector)?.addEventListener("click", handler);
  }

  function buildHTML() {
    return `
      <div class="midas-header">
        <div>
          <div class="midas-header-title">MIDAS</div>
          <div class="midas-header-subtitle">Conversation Intelligence</div>
        </div>
        <button class="midas-toggle-btn" id="midas-toggle" title="Minimizar">\u2212</button>
      </div>
      <div class="midas-content">
        <div class="midas-state midas-no-auth" data-state="${STATES.NO_AUTH}">
          <p>No est\u00e1s conectado. Haz clic en el icono de MIDAS en la barra de extensiones para configurar tu API key.</p>
        </div>

        <div class="midas-state midas-idle" data-state="${STATES.IDLE}">
          <p class="midas-chat-name" id="midas-chat-info">Abre un chat para analizar</p>
          <button class="midas-btn midas-btn-primary" id="midas-analyze-btn">Analizar conversaci\u00f3n</button>
        </div>

        <div class="midas-state midas-analyzing" data-state="${STATES.ANALYZING}">
          <div class="midas-spinner"></div>
          <p>Analizando conversaci\u00f3n...</p>
        </div>

        <div class="midas-state midas-result" data-state="${STATES.RESULT}">
          <div class="midas-result-card" id="midas-result-card"></div>
          <button class="midas-btn midas-btn-primary hidden" id="midas-generate-btn">Generar solicitud</button>
          <button class="midas-btn midas-btn-secondary" id="midas-reanalyze-btn">Analizar de nuevo</button>
        </div>

        <div class="midas-state midas-application" data-state="${STATES.APPLICATION}">
          <div id="midas-app-content"></div>
          <button class="midas-btn midas-btn-secondary" id="midas-back-btn">Volver al an\u00e1lisis</button>
        </div>

        <div class="midas-state midas-error" data-state="${STATES.ERROR}">
          <p class="midas-error-text" id="midas-error-text"></p>
          <button class="midas-btn midas-btn-primary" id="midas-retry-btn">Reintentar</button>
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

  function setChatInfo(clientName) {
    const el = sidebarEl?.querySelector("#midas-chat-info");
    if (el) {
      el.innerHTML = clientName
        ? `Chat con <strong>${clientName}</strong>`
        : "Abre un chat para analizar";
    }
  }

  function showResult(intent, conversationId) {
    currentConversationId = conversationId;

    const card = sidebarEl?.querySelector("#midas-result-card");
    const generateBtn = sidebarEl?.querySelector("#midas-generate-btn");
    if (!card) return;

    if (intent.intent_detected) {
      const productLabels = {
        mortgage: "Cr\u00e9dito Hipotecario",
        auto_loan: "Cr\u00e9dito Vehicular",
        refinance: "Refinanciaci\u00f3n",
        insurance: "Seguro",
      };

      const product = productLabels[intent.product_type] || intent.product_type;
      const confidence = Math.round(intent.confidence * 100);
      const badgeClass = intent.is_actionable ? "midas-badge-actionable" : "midas-badge-not-actionable";
      const badgeText = intent.is_actionable ? "Accionable" : "Baja confianza";

      const entities = intent.entities || {};
      const details = [
        entities.amount && `Monto: ${entities.amount}`,
        entities.term && `Plazo: ${entities.term}`,
        entities.location && `Ubicaci\u00f3n: ${entities.location}`,
      ].filter(Boolean);

      card.innerHTML = `
        <div class="midas-result-header">
          <span class="midas-result-product">${product}</span>
          <span class="midas-result-badge ${badgeClass}">${badgeText}</span>
        </div>
        ${details.map((d) => `<p class="midas-result-detail">${d}</p>`).join("")}
        <p class="midas-result-confidence">Confianza: ${confidence}%</p>
        ${intent.summary ? `<p class="midas-result-summary">${intent.summary}</p>` : ""}
      `;

      generateBtn?.classList.toggle("hidden", !intent.is_actionable);
    } else {
      card.innerHTML = `
        <div class="midas-result-header">
          <span class="midas-result-product">Sin intenci\u00f3n financiera</span>
        </div>
        ${intent.summary ? `<p class="midas-result-summary">${intent.summary}</p>` : ""}
      `;
      generateBtn?.classList.add("hidden");
    }

    setState(STATES.RESULT);
  }

  function showApplication(app) {
    const container = sidebarEl?.querySelector("#midas-app-content");
    if (!container) return;

    container.innerHTML = `
      <div class="midas-result-card">
        <div class="midas-result-header">
          <span class="midas-result-product">Solicitud de Cr\u00e9dito</span>
          <span class="midas-app-status">${app.status_label}</span>
        </div>
      </div>

      <div class="midas-app-section">
        <div class="midas-app-section-title">Solicitante</div>
        ${appField("Nombre", app.applicant.full_name)}
        ${appField("Tel\u00e9fono", app.applicant.phone)}
        ${appField("Ingreso estimado", app.applicant.estimated_income)}
        ${appField("Empleo", app.applicant.employment_type)}
        ${appField("Completitud", `${Math.round(app.applicant.completeness * 100)}%`)}
      </div>

      <div class="midas-app-section">
        <div class="midas-app-section-title">Producto</div>
        ${appField("Tipo", app.product_request.product_label)}
        ${appField("Monto", app.product_request.amount)}
        ${appField("Plazo", app.product_request.term)}
        ${appField("Ubicaci\u00f3n", app.product_request.location)}
      </div>

      <div class="midas-app-section">
        <div class="midas-app-section-title">Resumen</div>
        <p class="midas-result-summary">${app.conversation_summary}</p>
      </div>
    `;

    setState(STATES.APPLICATION);
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

  function handleToggle() {
    sidebarEl?.classList.toggle("collapsed");
    const btn = sidebarEl?.querySelector("#midas-toggle");
    if (btn) {
      btn.textContent = sidebarEl.classList.contains("collapsed") ? "+" : "\u2212";
    }
  }

  function appField(label, value) {
    if (!value) return "";
    return `
      <div class="midas-app-field">
        <span class="midas-app-field-label">${label}</span>
        <span class="midas-app-field-value">${value}</span>
      </div>
    `;
  }

  // Exponer en namespace global
  MIDAS.sidebar = {
    STATES,
    init,
    setState,
    setChatInfo,
    showResult,
    showApplication,
    showError,
    getConversationId: () => currentConversationId,
  };
})();
