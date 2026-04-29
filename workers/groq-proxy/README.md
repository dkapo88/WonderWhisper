# Groq Proxy Worker

This Worker keeps `GROQ_API_KEY` on Cloudflare instead of inside the Android app.

## What it proxies
- `POST /openai/v1/audio/transcriptions`
- `POST /openai/v1/chat/completions`
- `GET /health`

Any request under `/openai/v1/*` is forwarded to `https://api.groq.com/openai/v1/*` with the server-side `GROQ_API_KEY` secret.

## First-time setup
1. Install dependencies:
   ```bash
   cd workers/groq-proxy
   npm install
   ```
2. Log in to Cloudflare:
   ```bash
   npx wrangler login
   ```
3. Add the Groq secret:
   ```bash
   npx wrangler secret put GROQ_API_KEY
   ```
4. Optional: add an app token speed bump:
   ```bash
   npx wrangler secret put APP_PROXY_TOKEN
   ```
5. Run locally:
   ```bash
   npm run dev
   ```
6. Deploy:
   ```bash
   npm run deploy
   ```

## Local development secrets
- Copy `.dev.vars.example` to `.dev.vars` for local testing only.
- Do not commit `.dev.vars`.

## Android integration plan
1. Replace direct `https://api.groq.com` calls with your deployed Worker base URL.
2. Stop shipping the embedded Groq fallback key.
3. If `APP_PROXY_TOKEN` is set, send it as `X-App-Proxy-Token` from the app.

## Quick checks
- Health check:
  ```bash
  curl https://YOUR_WORKER_URL/health
  ```
- Chat completion check:
  ```bash
  curl https://YOUR_WORKER_URL/openai/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -d '{"model":"openai/gpt-oss-20b","messages":[{"role":"user","content":"hello"}]}'
  ```

## Notes
- `APP_PROXY_TOKEN` is only a light barrier. A determined attacker can still extract a static client token from an APK.
- For stronger abuse protection later, add real user auth, quotas, and rate limiting.
