const KEYS = {
  API_KEY: "midas_api_key",
  API_URL: "midas_api_url",
  ADVISOR_NAME: "midas_advisor_name",
};

const DEFAULT_API_URL = "http://InfraS-Backe-Rw986DMpmSLZ-46441931.us-east-1.elb.amazonaws.com";

async function get(key) {
  const result = await chrome.storage.local.get(key);
  return result[key] ?? null;
}

async function set(key, value) {
  await chrome.storage.local.set({ [key]: value });
}

async function remove(key) {
  await chrome.storage.local.remove(key);
}

export async function getApiKey() {
  return get(KEYS.API_KEY);
}

export async function setApiKey(apiKey) {
  await set(KEYS.API_KEY, apiKey);
}

export async function getApiUrl() {
  return (await get(KEYS.API_URL)) ?? DEFAULT_API_URL;
}

export async function setApiUrl(url) {
  await set(KEYS.API_URL, url);
}

export async function getAdvisorName() {
  return get(KEYS.ADVISOR_NAME);
}

export async function setAdvisorName(name) {
  await set(KEYS.ADVISOR_NAME, name);
}

export async function clearAuth() {
  await chrome.storage.local.remove([KEYS.API_KEY, KEYS.ADVISOR_NAME]);
}

export async function isAuthenticated() {
  const key = await getApiKey();
  return key !== null;
}
