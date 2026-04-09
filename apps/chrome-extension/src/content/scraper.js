/**
 * Scraper de mensajes de WhatsApp Web.
 *
 * Los selectores de WhatsApp Web cambian frecuentemente (clases ofuscadas).
 * Centralizamos aquí para facilitar mantenimiento.
 *
 * Selectores verificados: 2026-04-09
 */

// eslint-disable-next-line no-var
var MIDAS = window.MIDAS || {};

(function () {
  // --- Selectores (actualizar cuando WhatsApp cambie su DOM) ---

  const SELECTORS = {
    // Header del chat activo — contiene nombre del contacto
    chatHeader: "header span[title]",

    // Cada fila de mensaje (WhatsApp usa role="row" para cada burbuja)
    messageRow: '[role="row"]',

    // Texto dentro de un mensaje
    messageText: '[data-testid="selectable-text"]',

    // Clases que WhatsApp inyecta dentro del row para distinguir dirección
    outgoing: ".message-out",
    incoming: ".message-in",
  };

  function getActiveChatName() {
    const el = document.querySelector(SELECTORS.chatHeader);
    return el?.textContent?.trim() || null;
  }

  function getOwnName() {
    // En chats individuales no se muestra el nombre propio.
    // Usamos el título del header del drawer de perfil si está abierto.
    const profileEl = document.querySelector('[data-testid="drawer-header-title"]');
    return profileEl?.textContent?.trim() || null;
  }

  function scrapeActiveChat() {
    const clientName = getActiveChatName();
    if (!clientName) {
      return { advisor_name: null, client_name: null, messages: [] };
    }

    const rows = document.querySelectorAll(SELECTORS.messageRow);
    if (!rows.length) {
      return { advisor_name: null, client_name: clientName, messages: [] };
    }

    const messages = [];
    const advisorName = getOwnName();

    for (const row of rows) {
      const textEl = row.querySelector(SELECTORS.messageText);
      if (!textEl) continue;

      const text = textEl.textContent?.trim();
      if (!text) continue;

      // WhatsApp pone .message-out o .message-in dentro del row HTML
      const isOutgoing = !!row.querySelector(SELECTORS.outgoing);

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
