import * as api from "../services/api-client.js";
import { isAuthenticated } from "../services/storage.js";

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  handleMessage(message)
    .then(sendResponse)
    .catch((err) => sendResponse({ ok: false, error: err.message }));

  // Return true para indicar respuesta asíncrona
  return true;
});

async function handleMessage(message) {
  switch (message.type) {
    case "GET_AUTH_STATUS":
      return handleGetAuthStatus();

    case "IMPORT_CONVERSATION":
      return handleImportConversation(message.data);

    case "DETECT_INTENT":
      return handleDetectIntent(message.conversationId);

    case "GENERATE_APPLICATION":
      return handleGenerateApplication(message.conversationId);

    case "ANALYZE_CONVERSATION":
      return handleAnalyzeConversation(message.data);

    default:
      return { ok: false, error: `Mensaje desconocido: ${message.type}` };
  }
}

async function handleGetAuthStatus() {
  const authenticated = await isAuthenticated();
  if (!authenticated) {
    return { ok: false, error: "No autenticado" };
  }

  try {
    const advisor = await api.getMe();
    return { ok: true, advisorName: advisor.name };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

async function handleImportConversation(data) {
  try {
    const conversation = await api.importConversation(data);
    return { ok: true, conversation };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

async function handleDetectIntent(conversationId) {
  try {
    const intent = await api.detectIntent(conversationId);
    return { ok: true, intent };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

async function handleGenerateApplication(conversationId) {
  try {
    const application = await api.generateApplication(conversationId);
    return { ok: true, application };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

/**
 * Flujo completo: import → detect intent.
 * Un solo mensaje del content script dispara todo el análisis.
 */
async function handleAnalyzeConversation(data) {
  try {
    // 1. Importar conversación
    const conversation = await api.importConversation(data);

    // 2. Detectar intención
    const intent = await api.detectIntent(conversation.id);

    return { ok: true, conversation, intent };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}
