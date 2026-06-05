# Backend Implementation Status

## Current Branch

- Branch: `refactor/design-spec-domain-foundation`
- Phase: resource, file, request reward, admin system, and audit legacy extraction
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
- Converted `DesignSpecForumService` into a compatibility facade.
- Moved the previous large implementation into `LegacyDesignSpecForumService` as a transition boundary.
- Added support layer classes:
  - `TxSupport`
  - `ValueSupport`
  - `ForumLookupService`
  - `MappingSupport`
- Added design-spec service packages for:
  - identity
  - resource and file
  - interaction
  - request reward
  - report and appeal
  - notification event and dispatcher
  - admin logs, catalog, system config, and member status
- Added structure regression test to keep the facade from depending directly on JDBC, transactions, state machines, or `PointManager`.
- Migrated `ResourceQueryService` off `LegacyDesignSpecForumService`:
  - front resource list
  - resource detail
  - admin resource list
  - resource comments for detail response
- Migrated `service.resource.ResourceService` off `LegacyDesignSpecForumService`:
  - resource publish
  - owner submit and withdraw
  - owner/admin delete
  - admin audit and status transitions
  - resource tag and attachment metadata insertion
- Migrated `service.resource.FileService` off `LegacyDesignSpecForumService` for resource attachment downloads.
- Migrated `RequestRewardService` off `LegacyDesignSpecForumService`:
  - request list and detail
  - request creation and tag insertion
  - owner cancel with reward refund through `PointManager`
  - reply list and creation
  - owner settle with reward transfer through `PointManager`
  - admin close and admin reply deletion
- Migrated `AdminLogService.adminLogs` and resource admin-log writes to the new admin log service.
- Routed resource status notifications through `NotificationDispatcher`.
- Migrated `AdminCatalogService` off `LegacyDesignSpecForumService`:
  - category list, create, update, and disable
  - tag list, create, disable, and merge
  - all admin mutations write `admin_operation_log` through `AdminLogService`
- Migrated `AdminSystemService` off `LegacyDesignSpecForumService`:
  - system config query and update
  - cache refresh admin-log write
- Migrated `AdminMemberService` off `LegacyDesignSpecForumService`:
  - member disable and enable
  - ordinary member-only guard preserved
  - admin-log write and member status notification dispatch
- Migrated audit services off `LegacyDesignSpecForumService`:
  - `ReportComplaintService.report`
  - `ReportComplaintService.handleReport`
  - `AppealService.appeal`
  - `AppealService.handleAppeal`
  - admin report/appeal handling writes `admin_operation_log` through `AdminLogService`
- Isolated `NotificationDispatcher` with `TxSupport.requiresNew` so notification creation or failure-event recording does not roll back core business transactions.
- Fixed `ValueSupport.splitTags` to use a stable comma/full-width comma splitter.
- Expanded structure tests so migrated resource, file, request reward, catalog, system config, member-status, and audit services cannot depend on `LegacyDesignSpecForumService`.

## Current Design Choices

- `DesignSpecForumService` is now a facade and keeps the original public methods as controller-compatible delegators.
- Resource, file, request reward, catalog, system config, member-status, and audit facade paths now use the new module implementations instead of `LegacyDesignSpecForumService`.
- `LegacyDesignSpecForumService` still contains identity, interaction, and duplicated legacy resource/request/admin/audit code during the transition.
- Controllers continue to call the facade.
- API responses remain `{ code, message, data, timestamp }`.
- Pagination remains `{ total, list, page, size }`.
- The service still returns `Map<String, Object>` for compatibility.
- DTO packages are deferred to the next phase.

## Remaining Work

- Move remaining implementation bodies out of `LegacyDesignSpecForumService` into identity and interaction services.
- Replace remaining transitional delegation inside non-resource domain services with owned SQL and helper usage.
- Remove or reduce duplicated resource code from `LegacyDesignSpecForumService` after all internal callers are confirmed migrated.
- Wire remaining notification call sites through `NotificationEventService` and `NotificationDispatcher`.
- Wire any remaining admin operation call sites in future migrated workflows through `AdminLogService`.
- Replace download URL placeholder behavior with real file stream or signed URL.
- Add Redis-backed token blacklist or token version invalidation.
- Add real email delivery; current reset-code flow remains development-oriented.
- Add object storage adapter if the project leaves local file storage.

## Risks

- `LegacyDesignSpecForumService` still directly uses `JdbcTemplate` and contains most remaining identity and interaction SQL.
- Some migrated resource, request, admin-management, and audit logic is duplicated in legacy for transition safety until legacy is fully dismantled.
- Notification and admin-log call sites outside migrated resource/request/admin-management/audit workflows still need to be migrated from legacy implementation into the new services.
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
- Result: 29 tests, 0 failures, 0 errors, 9 skipped.
- Skipped tests: Testcontainers-backed MySQL tests were skipped because local Docker was not available.
