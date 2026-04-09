/**
 * MIDAS Content Script - The "Brain" in the DOM
 */

console.log("MIDAS: Content Script loaded on WhatsApp Web.");

// Función para inyectar el Sidebar de MIDAS
function injectMidasSidebar() {
  if (document.getElementById("midas-root")) return;

  const sidebar = document.createElement("div");
  sidebar.id = "midas-root";
  sidebar.innerHTML = `
    <div class="midas-sidebar-header">
      <img src="${chrome.runtime.getURL("icons/icon16.png")}" alt="MIDAS" />
      <span>MIDAS Intelligence</span>
    </div>
    <div class="midas-sidebar-content">
      <div id="midas-status">Waiting for financial signals...</div>
      <div id="midas-intent-card" style="display: none;">
        <h4 id="intent-type">Mortgage Detected</h4>
        <p id="intent-summary"></p>
        <button id="generate-app-btn">Review Application</button>
      </div>
    </div>
  `;
  document.body.appendChild(sidebar);
  console.log("MIDAS: Sidebar injected.");
}

// Observar cambios en el DOM para detectar mensajes
const observer = new MutationObserver((mutations) => {
  // Aquí iría la lógica del scraper pasivo
  // Por ahora, simulamos una detección después de 5 segundos
});

observer.observe(document.body, { childList: true, subtree: true });

// Inyectar Sidebar al cargar
window.addEventListener("load", () => {
  setTimeout(injectMidasSidebar, 3000);
});
