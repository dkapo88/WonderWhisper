const GROQ_API_BASE = "https://api.groq.com";
const ALLOWED_PREFIX = "/openai/v1/";
const FILTERED_REQUEST_HEADERS = new Set([
  "accept",
  "accept-encoding",
  "content-type"
]);
const FILTERED_RESPONSE_HEADERS = new Set([
  "cache-control",
  "content-type"
]);

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }));
    }

    const url = new URL(request.url);

    if (url.pathname === "/" || url.pathname === "/health") {
      return json(
        {
          ok: true,
          service: "groq-proxy",
          proxyTokenEnabled: Boolean(env.APP_PROXY_TOKEN)
        },
        200
      );
    }

    if (!env.GROQ_API_KEY) {
      return json({ error: "GROQ_API_KEY is not configured" }, 500);
    }

    if (env.APP_PROXY_TOKEN) {
      const providedToken = request.headers.get("X-App-Proxy-Token");
      if (providedToken !== env.APP_PROXY_TOKEN) {
        return json({ error: "Unauthorized" }, 401);
      }
    }

    if (!url.pathname.startsWith(ALLOWED_PREFIX)) {
      return json({ error: "Not found" }, 404);
    }

    const upstreamUrl = new URL(`${GROQ_API_BASE}${url.pathname}${url.search}`);
    const headers = buildUpstreamHeaders(request.headers, env.GROQ_API_KEY);
    const init = {
      method: request.method,
      headers,
      redirect: "follow"
    };

    if (request.method !== "GET" && request.method !== "HEAD") {
      init.body = request.body;
    }

    try {
      const upstream = await fetch(upstreamUrl, init);
      return toClientResponse(upstream);
    } catch (error) {
      return json(
        {
          error: "Upstream request failed",
          detail: error instanceof Error ? error.message : String(error)
        },
        502
      );
    }
  }
};

function buildUpstreamHeaders(incomingHeaders, apiKey) {
  const headers = new Headers();

  for (const [key, value] of incomingHeaders.entries()) {
    if (FILTERED_REQUEST_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  }

  headers.set("Authorization", `Bearer ${apiKey}`);
  headers.set("User-Agent", "DictationKeyboardAI-Cloudflare-Proxy/1.0");

  return headers;
}

function toClientResponse(upstream) {
  const headers = new Headers();

  for (const [key, value] of upstream.headers.entries()) {
    if (FILTERED_RESPONSE_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  }

  return withCors(
    new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers
    })
  );
}

function withCors(response) {
  response.headers.set("Access-Control-Allow-Origin", "*");
  response.headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-App-Proxy-Token");
  response.headers.set("Access-Control-Allow-Methods", "GET,POST,HEAD,OPTIONS");
  return response;
}

function json(payload, status) {
  return withCors(
    new Response(JSON.stringify(payload, null, 2), {
      status,
      headers: {
        "Content-Type": "application/json"
      }
    })
  );
}
