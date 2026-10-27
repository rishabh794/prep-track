# Backend audit fixes and API contracts

This follow-up addresses the six P1 findings and three missing API features in the [30-day baseline audit](backend-30-day-audit.md). The original report remains a record of the behavior before these changes.

## Corrected user flows

- Both recall routes load owned grading inputs in a short transaction, run grading outside a database transaction, and revalidate ownership when saving the result. Interview feedback uses the same separation.
- Recall confirmation serializes learning updates for the user and records confirmation state. Repeating the same confirmation returns HTTP 200 without another learning update or history event. Changing an already-confirmed grade returns 400. Concurrent confirmations receive the same protection.
- Regeneration persists the effective current budget. Skipped work is excluded from active workload/value totals. Plan responses also expose `remainingEstimatedMinutes`, `skippedMinutes`, and `skippedCount`. Completing a skipped item clears its skipped state; completed work cannot subsequently be skipped.
- Plans, attempts, revision due dates, forecasts, and interview date calculations use the user's configured calendar. Stored practice timestamps are converted to that calendar for retention and interview-boost calculations, including midnight boundaries.
- Pattern-gap recovery schedules the actual linked prerequisite as a retry action, using its own problem ID, time estimate, and completion state. The original problem returns after an independent coding solve of the prerequisite following the failure. A recall alone does not satisfy this coding requirement. Shared prerequisites are deduplicated. Missing prerequisites produce an explicit code-from-scratch fallback.

Related validation issues were also corrected: negative timing/hint signals and contradictory independent outcomes with hints return 400; assisted/failed attempts without a reason receive a generic coding follow-up; unknown revision outcomes no longer become failures; malformed/duplicate interview topics and unfinished confirmation outcomes return 400; interview boosts cannot be replayed through editable outcome state. New value breakdowns expose the overdue bonus. Missing routes and unsupported methods retain 404/405 semantics.

## Study settings

`PUT /api/onboarding/settings` accepts partial updates. Both slot limits now allow zero:

```json
{
  "dailyTimeBudgetMin": 90,
  "maxNewProblemsDay": 0,
  "maxRevisionsDay": 10,
  "timezone": "Asia/Kolkata"
}
```

Use `maxNewProblemsDay:0` for review-only days, `maxRevisionsDay:0` for new-work-only days, or both zero for a rest day. Setting changes apply to new plans; explicitly regenerate an existing plan to apply changed limits while preserving completed/skipped items.

`timezone` must be an IANA timezone such as `UTC`, `Asia/Kolkata`, or `America/Los_Angeles`. Invalid or blank values return 400 and roll back the whole settings update. Settings responses, onboarding completion, and `GET /api/auth/me` return the timezone. Existing accounts retain their stored timezone, with UTC as the fallback.

## Activity history

All routes require authentication and return only the current user's records. The problem ID identifies a catalog problem, rather than another user.

| Method | Route | Behavior |
|---|---|---|
| GET | `/api/attempts?page=0&size=20` | Paginated activity across problems |
| GET | `/api/attempts?problemId=1&page=0&size=20` | The same history filtered to one problem |
| GET | `/api/problems/1/attempts?page=0&size=20` | Per-problem alias with the same page contract |

The response has a stable page envelope:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "last": true
}
```

Entries include `id`, `problemId`, `problemTitle`, `activityType`, `outcome`, `failureReason`, `timeTakenSec`, `hintsUsed`, `isRevision`, `recallCheckId`, `followUpType`, `notes`, and `attemptedAt`. Timestamps are returned as UTC instants. Ordering is newest timestamp first, then descending ID, so equal timestamps paginate consistently. Page numbers are nonnegative, size is 1–100, and unsupported offsets return 400. Unknown problem IDs return 404.

Activity types distinguish `CODING_ATTEMPT`, `CODING_REVIEW`, and `RECALL_REVIEW`. Revision reviews append history in the same transaction as their learning update. Repeated recall confirmation appends nothing. Coding-review submissions can include optional `failureReason`, `timeTakenSec`, `hintsUsed`, and `notes` alongside their outcome.

The migration labels existing attempt-table rows using their old revision flag. Coding reviews omitted by the previous implementation cannot be reconstructed from aggregate counters; complete typed history begins with the updated write paths.

## Personal insight notes

| Method | Route | Behavior |
|---|---|---|
| GET | `/api/problems/1/note` | Returns `{ "problemId":1, "insightNote":null }` when no note exists |
| PUT | `/api/problems/1/note` | Stores the authenticated user's note |
| GET | `/api/problems/1` | Includes the current user's `insightNote` in problem detail |

Write a note using:

```json
{ "insightNote": "Look up the complement before inserting the current value." }
```

An empty string clears it. Missing/null values and values over 20,000 characters return 400. Notes may be saved before the first attempt and do not award learning credit, change problem completion, or append an attempt. Other users receive their own note or null.

## Database upgrade

Flyway migrations V15–V17 add typed activity history, recall confirmation state, and independent interview-topic confirmation state.

Legacy recall checks do not identify whether they were already confirmed: the old implementation populated `final_grade` during initial grading. The upgrade conservatively invalidates those old checks. Confirmation returns a clear 400 asking for a fresh recall, protecting existing learning state from replay without pretending pending checks were completed. New checks work normally. Previously confirmed interview topics preserve their confirmation marker.

## Verification and remaining findings

Regression coverage includes real HTTP requests with open-in-view disabled, grading outside transactions, concurrent confirmation, legacy recall handling, prerequisite recovery, user-local date boundaries, history completeness/pagination/privacy, and note edits without learning changes.

Verification on 27 October 2026: **134 tests passed**, including the two extended audit scenarios. The updated journey completed **988 HTTP requests and 1,854 invariant checks**, with no HTTP 500 responses and no remaining P1 or missing-API findings. A separate reconstruction checked 1,080 retention values and 30 forecast aggregations. The populated V14-to-current migration test passed. Verification used isolated H2 databases; production data and live AI services were not accessed.

Run the standard suite from `backend` with `./mvnw.cmd test`. Run the extended month-long HTTP journey with `./mvnw.cmd '-Dtest=ThirtyDayBackendAudit' test`. The audit now fails if any request returns 500 or any P1/missing-feature finding remains, while still exporting lower-priority observations.

The remaining P2 planner findings are candidate-pool diversity penalties and repeated recall-only cards for one pattern. The broader calibration suggestions in the baseline report—first-review spacing, adaptive time estimates, forecast uncertainty, progress timelines, and catalog completion—remain separate follow-up work.
