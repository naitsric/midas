import {
  getApiKey,
  setApiKey,
  getApiUrl,
  setApiUrl,
  getAdvisorName,
  setAdvisorName,
  clearAuth,
} from "../services/storage.js";

const elements = {
  authForm: document.getElementById("auth-form"),
  authStatus: document.getElementById("auth-status"),
  apiKeyInput: document.getElementById("api-key-input"),
  apiUrlInput: document.getElementById("api-url-input"),
  connectBtn: document.getElementById("connect-btn"),
  authError: document.getElementById("auth-error"),
  advisorName: document.getElementById("advisor-name"),
  disconnectBtn: document.getElementById("disconnect-btn"),
};

async function showAuthForm() {
  elements.authForm.classList.remove("hidden");
  elements.authStatus.classList.add("hidden");
  elements.authError.classList.add("hidden");

  const apiUrl = await getApiUrl();
  elements.apiUrlInput.value = apiUrl;
  elements.apiKeyInput.value = "";
  elements.apiKeyInput.focus();
}

async function showAuthStatus() {
  elements.authForm.classList.add("hidden");
  elements.authStatus.classList.remove("hidden");

  const name = await getAdvisorName();
  elements.advisorName.textContent = name || "Asesor";
}

function showError(message) {
  elements.authError.textContent = message;
  elements.authError.classList.remove("hidden");
}

async function handleConnect() {
  const apiKey = elements.apiKeyInput.value.trim();
  const apiUrl = elements.apiUrlInput.value.trim();

  if (!apiKey) {
    showError("Ingresa tu API key");
    return;
  }

  elements.connectBtn.disabled = true;
  elements.connectBtn.textContent = "Conectando...";
  elements.authError.classList.add("hidden");

  try {
    // Guardar temporalmente para que api-client pueda usarlos
    await setApiKey(apiKey);
    if (apiUrl) {
      await setApiUrl(apiUrl);
    }

    // Validar contra el backend via service worker
    const response = await chrome.runtime.sendMessage({ type: "GET_AUTH_STATUS" });

    if (response.ok) {
      await setAdvisorName(response.advisorName);
      await showAuthStatus();
      // Notificar al content script
      notifyContentScript("AUTH_CHANGED", { authenticated: true });
    } else {
      await clearAuth();
      showError(response.error || "API key inválida");
    }
  } catch (err) {
    await clearAuth();
    showError("Error de conexión con el servidor");
  } finally {
    elements.connectBtn.disabled = false;
    elements.connectBtn.textContent = "Conectar";
  }
}

async function handleDisconnect() {
  await clearAuth();
  await showAuthForm();
  notifyContentScript("AUTH_CHANGED", { authenticated: false });
}

function notifyContentScript(type, data) {
  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    if (tabs[0]?.id) {
      chrome.tabs.sendMessage(tabs[0].id, { type, ...data }).catch(() => {
        // Tab no tiene content script — ignorar
      });
    }
  });
}

// --- Init ---

elements.connectBtn.addEventListener("click", handleConnect);
elements.disconnectBtn.addEventListener("click", handleDisconnect);

// Enter para conectar
elements.apiKeyInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter") handleConnect();
});

// Determinar estado inicial
(async () => {
  const apiKey = await getApiKey();
  if (apiKey) {
    await showAuthStatus();
  } else {
    await showAuthForm();
  }
})();
