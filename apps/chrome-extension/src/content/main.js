/**
 * Entry point del content script de MIDAS.
 * Se ejecuta en WhatsApp Web.
 *
 * Depende de scraper.js y sidebar.js cargados antes (via manifest content_scripts).
 * Accede a MIDAS.scraper y MIDAS.sidebar.
 */

(function () {
  const { scraper, sidebar } = MIDAS;

  async function initialize() {
    // Inicializar sidebar
    sidebar.init({
      onAnalyzeClick: handleAnalyze,
      onGenerateClick: handleGenerate,
    });

    // Verificar autenticación
    const authStatus = await sendMessage({ type: "GET_AUTH_STATUS" });
    if (authStatus.ok) {
      sidebar.setState(sidebar.STATES.IDLE);
      sidebar.setChatInfo(scraper.getActiveChatName());
    } else {
      sidebar.setState(sidebar.STATES.NO_AUTH);
    }

    // Observar cambios de chat
    scraper.observeChatChanges((newChatName) => {
      sidebar.setChatInfo(newChatName);
      sidebar.setState(sidebar.STATES.IDLE);
    });

    // Escuchar mensajes del popup (cambios de auth)
    chrome.runtime.onMessage.addListener((message) => {
      if (message.type === "AUTH_CHANGED") {
        if (message.authenticated) {
          sidebar.setState(sidebar.STATES.IDLE);
          sidebar.setChatInfo(scraper.getActiveChatName());
        } else {
          sidebar.setState(sidebar.STATES.NO_AUTH);
        }
      }
    });
  }

  /**
   * Flujo de análisis: scrape → import → detect intent
   */
  async function handleAnalyze() {
    const data = scraper.scrapeActiveChat();

    if (!data.messages.length) {
      sidebar.showError(
        "No se encontraron mensajes en este chat. Asegúrate de tener un chat abierto con mensajes visibles."
      );
      return;
    }

    sidebar.setState(sidebar.STATES.ANALYZING);

    const response = await sendMessage({
      type: "ANALYZE_CONVERSATION",
      data: {
        advisor_name: data.advisor_name,
        client_name: data.client_name,
        messages: data.messages,
      },
    });

    if (response.ok) {
      sidebar.showResult(response.intent, response.conversation.id);
    } else {
      sidebar.showError(response.error || "Error al analizar la conversación");
    }
  }

  /**
   * Flujo de generación de solicitud.
   */
  async function handleGenerate(conversationId) {
    sidebar.setState(sidebar.STATES.ANALYZING);

    const response = await sendMessage({
      type: "GENERATE_APPLICATION",
      conversationId,
    });

    if (response.ok) {
      sidebar.showApplication(response.application);
    } else {
      sidebar.showError(response.error || "Error al generar la solicitud");
    }
  }

  function sendMessage(message) {
    return chrome.runtime.sendMessage(message).catch((err) => ({
      ok: false,
      error: err.message || "Error de comunicación con la extensión",
    }));
  }

  // --- Inicializar cuando WhatsApp Web esté listo ---
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => setTimeout(initialize, 2000));
  } else {
    setTimeout(initialize, 2000);
  }
})();
