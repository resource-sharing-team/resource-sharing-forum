# Backend API Contract

This document records the current backend API contract for frontend integration and review against the design documents. Controllers remain the source of truth; this file does not introduce new API paths.

## Response Wrapper

All application responses use the same wrapper:

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-06-06T00:00:00Z"
}
```

Created responses use `code: 201` and `message: "created"`. Business and validation errors keep the same wrapper with `data: null`.

## Pagination

Paged responses use:

```json
{
  "total": 0,
  "list": [],
  "page": 1,
  "size": 20
}
```

The implementation still returns `Map<String, Object>` and `PageResult<Map<String, Object>>` where the existing controllers expect them.

## Authentication

- Public: `/api/health`, `/api/auth/**`, `/api/v1/auth/**`, public resource/request/comment reads.
- Protected: user profile, uploads, downloads, interactions, comments write actions, reports, appeals, notifications.
- Admin: `/api/admin/**`, `/api/v1/admin/**`, and resource audit endpoints.
- JWT is supplied as `Authorization: Bearer <token>`.

## Compatibility Paths

The backend keeps legacy `/api` paths and design-spec `/api/v1` paths side by side for the current transition.

| Area | Paths |
| --- | --- |
| Health | `GET /api/health` |
| Auth | `/api/auth/**`, `/api/v1/auth/**` |
| Current user | `/api/me/**`, `/api/v1/user/profile/**` |
| Resources | `/api/resources/**`, `/api/v1/resources/**` |
| Attachments | `/api/files/**`, `/api/v1/attachments/**` |
| Requests | `/api/requests/**`, `/api/v1/requests/**` |
| Comments | `/api/comments/**`, `/api/v1/comments/**` |
| Reports | `/api/reports/**`, `/api/v1/reports/**` |
| Appeals | `/api/appeals/**`, `/api/v1/appeals/**` |
| Notifications | `/api/notifications/**`, `/api/v1/notifications/**` |
| Admin | `/api/admin/**`, `/api/v1/admin/**` where present |

## Frontend Stability Notes

- Do not consume `storage_path`; real storage paths are not exposed.
- Use backend download endpoints instead of constructing file URLs.
- Treat `code`, `message`, `data`, and `timestamp` as the stable response envelope.
- Treat `total`, `list`, `page`, and `size` as the stable pagination envelope.
