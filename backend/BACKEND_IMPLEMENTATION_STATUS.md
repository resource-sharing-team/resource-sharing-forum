# Backend Implementation Status

## Current Branch

- Branch: `refactor/design-spec-domain-foundation`
- Phase: domain foundation refactor
- API contract: unchanged
- Controller contract: unchanged

## Completed In This Phase

- Added resource lifecycle state machine.
- Added request reward lifecycle state machine.
- Added reward point manager interface and JDBC implementation.
- Integrated `DesignSpecForumService` with:
  - `ResourceStateMachine` for resource submit, withdraw, and admin transitions.
  - `RequestStateMachine` for request cancel, settle, and admin close.
  - `PointManager` for request reward freeze, refund, and transfer.
- Removed request reward point account mutations from `DesignSpecForumService`.
- Added unit tests for state-machine rules.
- Added MySQL integration tests for `PointManager`.

## Current Design Choices

- `DesignSpecForumService` is still the facade and still contains most public methods.
- Controllers continue to call the facade.
- API responses remain `{ code, message, data, timestamp }`.
- Pagination remains `{ total, list, page, size }`.
- The service still returns `Map<String, Object>` for compatibility.
- DTO packages are deferred to the next phase.

## Remaining Work

- Extract shared helpers from `DesignSpecForumService`:
  - transaction support
  - value parsing support
  - lookup support
  - row mapping support
- Extract domain services:
  - resource query and lifecycle
  - file upload/download
  - interaction and comments
  - request reward workflow
  - report and appeal workflow
- Extract cross-cutting services:
  - admin logs
  - notification events
  - notification dispatcher
  - system config
  - category and tag management
  - member enable/disable
- Replace download URL placeholder behavior with real file stream or signed URL.
- Add Redis-backed token blacklist or token version invalidation.
- Add real email delivery; current reset-code flow remains development-oriented.
- Add object storage adapter if the project leaves local file storage.

## Risks

- `DesignSpecForumService` still directly uses `JdbcTemplate` outside the newly extracted point and state-machine boundaries.
- Notification and admin-log calls are still mixed into the facade.
- Some legacy fallback paths remain for no-database smoke tests.
- Testcontainers tests are skipped automatically when Docker is unavailable.

## Verification

Run before each commit:

```powershell
.\mvnw.cmd -s D:\tmp\maven-aliyun-settings.xml test
git diff --check
```

Latest local verification:

- `.\mvnw.cmd -s D:\tmp\maven-aliyun-settings.xml test`: passed.
- Result: 27 tests, 0 failures, 0 errors, 9 skipped.
- Skipped tests: Testcontainers-backed MySQL tests were skipped because local Docker was not available.
