import { getApiKey, getApiUrl } from "./storage.js";

class ApiError extends Error {
  constructor(status, detail) {
    super(detail);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request(method, path, body = null) {
  const [apiUrl, apiKey] = await Promise.all([getApiUrl(), getApiKey()]);

  if (!apiKey) {
    throw new ApiError(401, "No hay API key configurada");
  }

  const options = {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": apiKey,
    },
  };

  if (body !== null) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${apiUrl}${path}`, options);

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new ApiError(response.status, errorData.detail || `Error ${response.status}`);
  }

  return response.json();
}

// --- Advisor ---

export async function getMe() {
  return request("GET", "/api/advisors/me");
}

// --- Conversations ---

export async function importConversation(data) {
  return request("POST", "/api/conversations/import", data);
}

export async function listConversations() {
  return request("GET", "/api/conversations");
}

export async function getConversation(id) {
  return request("GET", `/api/conversations/${id}`);
}

// --- Intent ---

export async function detectIntent(conversationId) {
  return request("POST", `/api/conversations/${conversationId}/detect-intent`);
}

// --- Applications ---

export async function generateApplication(conversationId) {
  return request("POST", `/api/conversations/${conversationId}/generate-application`);
}

export async function listApplications() {
  return request("GET", "/api/applications");
}

export async function getApplication(id) {
  return request("GET", `/api/applications/${id}`);
}

export { ApiError };
