# Frontend Integration Guide

This guide is for connecting the frontend branch (`Web_User` / `web_user`) to the deployed backend without changing the backend API contract.

## Backend Base URL

Local backend:

```text
http://localhost:8080
```

Design-spec API prefix:

```text
/api/v1
```

The backend also keeps compatible `/api` routes during the transition.

## CORS

Set `CORS_ALLOWED_ORIGINS` in `backend/.env` to the exact frontend origins that will call the backend.

Examples:

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

For a deployed frontend, replace the local origins with the deployed HTTPS origin. Do not use `*` when credentials or bearer tokens are used.

The backend exposes `X-Trace-Id` to browsers so frontend error reports can be matched with backend logs.

## Auth Token Handling

Login endpoint:

```text
POST /api/v1/auth/login
```

The frontend should send the returned JWT on protected requests:

```text
Authorization: Bearer <token>
```

Disabled or locked accounts should be handled as ordinary API errors using the shared response wrapper.

## File and Resource Rules

- Upload through `/api/v1/attachments/upload`.
- Download through backend resource or attachment download endpoints.
- Never derive or display real server storage paths.
- A normal user can download only published resource attachments.

## Production Connection Options

Option A: browser calls backend directly.

- Frontend environment points to the backend origin, for example `VITE_API_BASE_URL=https://api.example.com`.
- Backend `CORS_ALLOWED_ORIGINS` contains the frontend origin.

Option B: frontend reverse proxy forwards `/api` to backend.

- Browser uses relative `/api` and `/api/v1`.
- Nginx or the hosting gateway proxies `/api` to the backend container.
- Backend CORS still allows the public frontend origin for safety.

## Switching The `web_user` Frontend From Mock To Backend

The current `web_user` worktree documents development-time MSW mocks for `/api`. Before integration deployment, switch the frontend runtime to the real backend:

1. Set the frontend API base URL, for example:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_API_PREFIX=/api
VITE_ENABLE_MOCKS=false
```

For a reverse-proxy deployment, set the frontend API base to the public site origin or use relative `/api` calls, depending on the frontend client implementation.

2. Disable MSW/mock registration for integration and production builds. The current `web_user` client starts MSW only when `VITE_ENABLE_MOCKS=true`, or when no backend base URL is configured in local development.
3. Add the frontend origin to backend `CORS_ALLOWED_ORIGINS`, for example:

```dotenv
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

4. Start the backend and run:

```powershell
.\scripts\verify-deployment.ps1 -BaseUrl http://localhost:8080 -Origin http://localhost:5173
```

5. Start the frontend and confirm browser requests go to the deployed backend instead of MSW. Use the backend `X-Trace-Id` response header when correlating frontend failures with backend logs.

The current `web_user` Axios client accepts the unchanged backend response wrapper and normalizes backend pagination `{ total, list, page, size }` to the existing page model `{ total, items, page, pageSize }`, so pages do not need to bypass the facade or call new DTO endpoints.

For a local smoke test against a running backend, execute from the `web_user` worktree:

```powershell
npm run test:e2e:backend
```

This starts the Vite frontend with `VITE_ENABLE_MOCKS=false`, derives the backend API base from `VITE_API_BASE_URL` and `VITE_API_PREFIX`, and verifies the public resource list response plus the backend `X-Trace-Id` header.

For a one-command backend plus frontend contract smoke from the backend directory, run:

```powershell
.\scripts\verify-frontend-integration.ps1 -FrontendDir ..\.worktrees\Web_User
```

That script starts the backend in no-database smoke mode on port `18080`, waits for `/api/health`, then runs the same `web_user` real-backend e2e command.

For a broader local acceptance pass before handoff, run:

```powershell
.\scripts\verify-local-acceptance.ps1 -FrontendDir ..\.worktrees\Web_User
```

This keeps the current no-Docker environment in mind: it verifies backend tests, frontend tests/build/e2e, real-backend smoke, and whitespace checks without attempting Compose startup.

## Verification

After backend startup:

```powershell
curl http://localhost:8080/api/health
```

Then verify from the frontend:

- Login returns `data.token`.
- Public resource list loads from `/api/v1/resources`.
- Protected profile request includes the bearer token.
- Upload, download, notifications, and admin pages do not read `storage_path`.
- `npm run test:e2e:backend` passes with `VITE_API_BASE_URL` pointing to the backend origin.
- `.\scripts\verify-frontend-integration.ps1 -FrontendDir ..\.worktrees\Web_User` passes for local no-database contract smoke.
- `.\scripts\verify-local-acceptance.ps1 -FrontendDir ..\.worktrees\Web_User` passes for the local no-Docker acceptance gate.
