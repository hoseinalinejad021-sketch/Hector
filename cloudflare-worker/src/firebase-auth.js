const GOOGLE_SECURE_TOKEN_KEYS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
let cachedKeys = null;
let cachedKeysUntil = 0;

export async function verifyFirebaseIdToken(request, projectId) {
  if (!projectId) return null;
  const header = request.headers.get("Authorization") || "";
  const match = header.match(/^Bearer\s+([^\s]+)$/i);
  if (!match) return null;

  try {
    const token = match[1];
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const headerPart = JSON.parse(decodeBase64Url(parts[0]));
    const claims = JSON.parse(decodeBase64Url(parts[1]));
    if (headerPart.alg !== "RS256" || typeof headerPart.kid !== "string") return null;

    const now = Math.floor(Date.now() / 1000);
    if (claims.aud !== projectId ||
        claims.iss !== `https://securetoken.google.com/${projectId}` ||
        typeof claims.sub !== "string" || claims.sub.length < 1 || claims.sub.length > 128 ||
        !Number.isFinite(claims.exp) || claims.exp <= now ||
        !Number.isFinite(claims.iat) || claims.iat > now + 60) return null;

    const keys = await getKeys();
    const jwk = keys.find(key => key.kid === headerPart.kid && key.alg === "RS256" && key.use === "sig");
    if (!jwk) return null;
    const publicKey = await crypto.subtle.importKey(
      "jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
    );
    const signedData = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const signature = decodeBase64UrlBytes(parts[2]);
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, signature, signedData);
    return valid ? { uid: claims.sub } : null;
  } catch {
    return null;
  }
}

async function getKeys() {
  if (cachedKeys && cachedKeysUntil > Date.now()) return cachedKeys;
  const response = await fetch(GOOGLE_SECURE_TOKEN_KEYS, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error("Firebase signing keys unavailable");
  const data = await response.json();
  if (!Array.isArray(data.keys)) throw new Error("Invalid Firebase signing keys");
  cachedKeys = data.keys;
  const cacheControl = response.headers.get("Cache-Control") || "";
  const maxAge = Number((cacheControl.match(/max-age=(\d+)/i) || [])[1] || 300);
  cachedKeysUntil = Date.now() + Math.min(Math.max(maxAge, 60), 3600) * 1000;
  return cachedKeys;
}

function decodeBase64Url(value) {
  return new TextDecoder().decode(decodeBase64UrlBytes(value));
}

function decodeBase64UrlBytes(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  const binary = atob(base64);
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}
