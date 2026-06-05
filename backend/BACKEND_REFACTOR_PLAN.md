# Backend Refactor Plan

## Goal

Move the backend from a single large `DesignSpecForumService` toward the design-spec layering:

`Controller -> DesignSpecForumService Facade -> domain services -> state machines / PointManager / notification / admin log`.

The first delivery keeps all controllers and `/api/v1` contracts unchanged. It only extracts the highest-risk rules first: resource status transitions, request reward status transitions, and reward point transactions.

## Current Phase: Domain Foundation

This phase establishes stable domain boundaries while keeping `DesignSpecForumService` as the compatibility facade.

Implemented or planned in this phase:

- Add `ResourceStateMachine` and `ResourceAction` under `domain/statemachine`.
- Add `RequestStateMachine` and `RequestAction` under `domain/statemachine`.
- Add `PointManager` and `JdbcPointManager` under `service/point`.
- Route resource submit, withdraw, admin audit/offline/copyright/restore/delete through `ResourceStateMachine`.
- Route request cancel, settle, and admin close through `RequestStateMachine`.
- Route request reward freeze, refund, and transfer through `PointManager`.
- Keep controller method calls and response shape unchanged.

## Facade Transition Strategy

`DesignSpecForumService` remains the facade for this phase. Public methods stay in place so controllers do not change.

Next refactor phases:

1. Extract shared support:
   - `TxSupport`
   - `ValueSupport`
   - `ForumLookupService`
   - `MappingSupport`

2. Extract domain services:
   - `ResourceQueryService`
   - `ResourceService`
   - `RequestRewardService`
   - `InteractionService`
   - `ReportComplaintService`
   - `AppealService`

3. Extract cross-cutting services:
   - `AdminLogService`
   - `NotificationEventService`
   - `NotificationDispatcher`
   - `AdminCatalogService`
   - `AdminSystemService`
   - `AdminMemberService`

## Invariants

Resource lifecycle:

- Statuses are `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`, `REVIEWING_RISK`, `OFFLINE`, `COPYRIGHT_DOWN`, `DELETED`.
- Controllers must not directly overwrite status.
- Every status change must go through the state machine.
- Rejection, offline, and copyright-down actions require a reason.
- Admin-only actions must require an admin role.
- Owner-only actions must require resource ownership.

Request reward lifecycle:

- Statuses are `ONGOING`, `RESOLVED`, `CANCELLED`, `CLOSED`.
- Cancel and accept-reply actions require owner permission.
- Close and restore actions require admin permission.
- Admin close requires a reason.

Reward points:

- Business services must not directly update `member_point_account`.
- `PointManager` must lock point accounts with `SELECT ... FOR UPDATE`.
- Two-account transfers must lock member ids in ascending order.
- Every point mutation must write `point_flow`.
- Duplicate freeze, refund, and transfer operations must fail instead of changing balances twice.

## Test Plan

- Unit tests cover resource and request state-machine transition rules.
- Testcontainers MySQL integration tests cover reward freeze, refund, transfer, and duplicate operation rejection.
- Existing MockMvc and integration tests remain the API regression suite.
- Final checks:
  - `.\mvnw.cmd -s D:\tmp\maven-aliyun-settings.xml test`
  - `git diff --check`

## Deferred Work

- Full `DesignSpecForumService` facade-only conversion.
- DTO migration from `Map<String, Object>`.
- Real file streaming or signed download URL.
- Redis token blacklist.
- Real email verification delivery.
- Object storage integration.
