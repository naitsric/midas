/**
 * Scraper de mensajes de WhatsApp Web.
 *
 * Los selectores de WhatsApp Web cambian frecuentemente (clases ofuscadas).
 * Centralizamos aquí para facilitar mantenimiento.
 * Preferimos data-testid cuando está disponible.
 */

// eslint-disable-next-line no-var
var MIDAS = window.MIDAS || {};

(function () {
  // --- Selectores (actualizar cuando WhatsApp cambie su DOM) ---

  const SELECTORS = {
    // Header del chat activo — contiene nombre del contacto
    chatHeader: '[data-testid="conversation-info-header-chat-title"]',
    chatHeaderFallback: "header span[title]",

    // Contenedor de mensajes
    messageList: '[data-testid="conversation-panel-messages"]',
    messageListFallback: '[role="application"]',

    // Cada burbuja de mensaje
    messageRow: '[data-testid="msg-container"]',
    messageRowFallback: ".message-in, .message-out",

    // Texto dentro de un mensaje
    messageText: '[data-testid="msg-text"]',
    messageTextFallback: "span.selectable-text",

    // Mensajes salientes (del asesor) vs entrantes (del cliente)
    outgoing: ".message-out",

    // Nombre del usuario en el perfil/header
    profileName: '[data-testid="drawer-header-title"]',
  };

  function querySelector(parent, primary, fallback) {
    return parent.querySelector(primary) || parent.querySelector(fallback);
  }

  function getActiveChatName() {
    const el = querySelector(document, SELECTORS.chatHeader, SELECTORS.chatHeaderFallback);
    return el?.textContent?.trim() || null;
  }

  function getOwnName() {
    const profileEl = document.querySelector(SELECTORS.profileName);
    if (profileEl?.textContent?.trim()) {
      return profileEl.textContent.trim();
    }
    return null;
  }

  function scrapeActiveChat() {
    const clientName = getActiveChatName();
    if (!clientName) {
      return { advisor_name: null, client_name: null, messages: [] };
    }

    const messageContainer = querySelector(
      document,
      SELECTORS.messageList,
      SELECTORS.messageListFallback
    );
    if (!messageContainer) {
      return { advisor_name: null, client_name: clientName, messages: [] };
    }

    const rows = messageContainer.querySelectorAll(
      `${SELECTORS.messageRow}, ${SELECTORS.messageRowFallback}`
    );

    const messages = [];
    const advisorName = getOwnName();

    for (const row of rows) {
      const textEl = querySelector(row, SELECTORS.messageText, SELECTORS.messageTextFallback);
      if (!textEl) continue;

      const text = textEl.textContent?.trim();
      if (!text) continue;

      const isOutgoing =
        row.closest(SELECTORS.outgoing) !== null || row.classList.contains("message-out");

      messages.push({
        sender_name: isOutgoing ? (advisorName || "Asesor") : clientName,
        is_advisor: isOutgoing,
        text,
      });
    }

    return {
      advisor_name: advisorName || "Asesor",
      client_name: clientName,
      messages,
    };
  }

  function observeChatChanges(callback) {
    let currentChat = getActiveChatName();

    const observer = new MutationObserver(() => {
      const newChat = getActiveChatName();
      if (newChat && newChat !== currentChat) {
        currentChat = newChat;
        callback(newChat);
      }
    });

    const target = document.querySelector("header")?.parentElement || document.body;
    observer.observe(target, { childList: true, subtree: true, characterData: true });

    return () => observer.disconnect();
  }

  // Exponer en namespace global
  MIDAS.scraper = {
    getActiveChatName,
    scrapeActiveChat,
    observeChatChanges,
  };
})();
