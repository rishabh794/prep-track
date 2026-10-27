# 30-day backend user journey audit

Audited on 27 October 2026 against backend commit `15e0c2d`.

**Follow-up:** The six P1 findings and three missing API features are addressed in the working tree. See [backend audit fixes and API contracts](backend-api-fixes.md) for corrected behavior, new endpoints, migration handling, and verification. This document preserves the original baseline; current generated audit artifacts describe the updated behavior.

The backend's learning formulas respond consistently to practice, success, failure, and missed days. However, normal recall and interview-feedback requests fail, replayed confirmations can inflate progress, and some planner responses contradict their own budget or explanation. Fix the six P1 findings below before building further product features.

The audit identified **18 bugs or logic defects and three missing API features**. This report records findings and proposed corrections; production code was not changed.

## Method and evidence

The reproducible harness is [ThirtyDayBackendAudit.java](../backend/src/test/java/com/preptrack/audit/ThirtyDayBackendAudit.java). It starts the actual Spring Boot HTTP server on a random loopback port, with real authentication, controllers, services, persistence, seed catalog, and policy. Requests use Java's HTTP client, rather than directly invoking controllers.

The test database is an isolated H2 database in PostgreSQL compatibility mode. `open-in-view=false` matches the application configuration. No production database or external AI account is used. The calendar advances from **28 September through 27 October 2026**, with additional isolated users for boundary cases. A test-only request filter supplies virtual dates to the static date calls, while an injectable clock supplies forecast dates. Host date behavior uses Asia/Kolkata; the stored user timezone defaults to UTC.

The completed run contains **1,076 HTTP requests**: 1,030 for the month and boundary scenarios, plus 46 supplemental requests. The harness evaluated **1,847 invariants**. An additional reconstruction of the exports checked 1,080 retention values and 30 forecast aggregations without finding arithmetic discrepancies beyond response rounding.

The month includes:

- Registration, daily login, overlapping Blind 75 and NeetCode 150 selections, and onboarding.
- 174 study items: new problems, failed-problem retries, coding reviews, recall grading, and confirmations.
- Missed study on days 7, 15, and 24; a skipped item and regeneration on day 4.
- Budget/slot changes from 90 minutes/3 new to 120 minutes/4 new on day 10, then 60 minutes/2 new on day 19.
- A Google interview on day 22, feedback and topic confirmation, then a new Meta interview target.
- Cross-user access checks; application/interview CRUD; simulator result persistence and ownership; problem, source, relation, sheet, policy, and company reads; empty, past, and capped forecast states.

Outcomes are scripted with a fixed random seed. The chance of an independent solve increases from 35% to 55%, 75%, and 85% across the four study periods. Recall uses the actual offline grading fallback; successful explanations use catalog reference approaches. This validates state transitions and API behavior, not the quality of real learners' explanations or live Gemini grading.

Two workarounds allowed the audit to continue: failed card-ID recalls were retried through the working by-problem endpoint, and failed feedback extraction was followed by manual topic confirmation. A real user following the normal routes would encounter these failures.

Generated evidence is under `backend/target/user-journey-audit/`:

| File | Contents |
|---|---|
| `requests.json` | Numbered, redacted request/response transcript for the month and boundary cases |
| `daily-snapshots.json` | Each day's plan, pattern states, sheet progress, and forecast |
| `findings.json`, `summary.json` | Machine-recorded findings, route counts, and run totals |
| `supplemental/requests.json` | Additional CRUD, validation, catalog-read, and forecast probes |
| `calculation-checks.json` | Reconstruction of retention and forecast arithmetic |

These generated files are ignored by Git. Passwords and tokens are removed from the exports. `M#63` below means trace 63 in the month transcript; `S#15` means trace 15 in the supplemental transcript. The month records 17 finding codes; the supplemental run records five, with interview-feedback failure appearing in both: 21 distinct findings.

## Does the model improve over time?

Yes, under the supplied practice outcomes. These are averages over all 36 returned patterns, including unpracticed patterns, measured after that day's study.

| Day | Average skill | Average retention | Average stability |
|---|---:|---:|---:|
| 1 | 0.31% | 13.89% | 1.00 days |
| 7 — missed | 3.28% | 14.14% | 1.11 days |
| 14 | 13.28% | 46.25% | 1.71 days |
| 21 | 19.31% | 37.83% | 2.31 days |
| 30 | 24.86% | 50.69% | 4.49 days |

Retention should fluctuate. For example, missing day 15 reduced average retention from 46.25% to 26.64%, while skill stayed at 13.28%. That is consistent with the exponential forgetting model, rather than lost progress.

Verified calculations and behavior:

- Coding solves apply the configured skill changes: independent `+0.10 × pattern weight`, assisted `+0.03 × weight`, failure `−0.02 × weight`, with bounds. Independent success multiplies stability by 1.5; failure halves it with a floor of 1 day.
- Successful recall refreshes retention and increases stability without awarding new skill acquisition. The replay defect below can apply that legitimate update more than once.
- Retention agrees with `exp(−days / stability)`, allowing for rounded API stability values. Unpracticed patterns return zero retention.
- Forecast readiness agrees with the company-weighted average of `projectedSkill × projectedRetention`. The arithmetic is internally consistent; that does not establish real-world predictive accuracy.
- Active planned work stayed within the configured budget and shared review/retry and new-problem caps. There were no duplicate problem IDs in a plan, including overlapping sheets. Completed work did not trigger an endless refill.
- Tested owner checks protected forecasts, plan items, applications, interviews, and saved simulator results. Ordinary application/interview CRUD and the core catalog reads succeeded.
- Forecasts returned explicit onboarding, past-date, and capped-horizon states. Distant forecasts disclosed their actual projected-through date.

The Google **projected interview readiness** fell from 18% on day 1 to 7% on day 21, despite rising current skill. This is not an aggregation error: the projection loses future study days, the learner misses days and fails some items, and the budget changes. It should not be presented as a monotonically increasing progress score. The Meta target starts a different company/date projection, so the jump to 23% on day 23 is not a sudden learning gain. Add a separate current-progress timeline, and explain forecast changes to users.

At day 30, Blind 75 showed 39/75 solved and NeetCode showed 46/117 solved. The overlap means those counts must not be added as distinct solved problems. The NeetCode catalog correctly discloses its current subset; completing the missing catalog is a content gap, not a new arithmetic bug.

## P1 — address before further feature work

### 1. Normal revision-card recall returns HTTP 500

**Code:** `CARD_RECALL_HTTP_500`. **Evidence:** 67 failed month requests, including M#63 and M#91.

`POST /api/revisions/{cardId}/recall` with a valid `userApproach` fails with `LazyInitializationException` for the card's `Problem`. The by-problem recall route succeeds with equivalent input. The normal card flow therefore blocks recall grading.

**Cause:** [RevisionService.java](../backend/src/main/java/com/preptrack/revision/service/RevisionService.java), `submitRecallCheck` around line 146, reads a detached lazy problem outside a transaction. The by-problem wrapper supplies a transaction. Give grading an explicitly loaded input snapshot, and persist its result in a separate transaction; keep external AI calls outside database transactions.

### 2. Interview feedback submission returns HTTP 500

**Code:** `INTERVIEW_FEEDBACK_HTTP_500`. **Evidence:** M#742 and S#15.

Submitting ordinary interview feedback fails before extraction. The log identifies a detached lazy `Application` in the interview ownership check.

**Cause:** [ApplicationService.java](../backend/src/main/java/com/preptrack/application/service/ApplicationService.java), `submitFeedback` around line 82 and `getInterviewOwnedByUser` around line 209. The nontransactional caller accesses `interview.application.user`. Load/verify ownership explicitly, perform extraction outside a transaction, and persist through a transaction boundary that is actually invoked through a Spring proxy. The current self-call to annotated `saveExtractionResult` also needs attention.

### 3. Reusing a confirmed recall check inflates learning state

**Code:** `RECALL_CHECK_REPLAY`. **Evidence:** M#996–1000.

The same `recallCheckId` can be confirmed twice. The second HTTP 200 changes Hash Map Lookup's attempts from 5 to 6, independent solves from 3 to 4, and stability from 1.7 to 2.5 days, although no new recall was performed. Network retries or repeated clicks can inflate retention projections.

**Cause:** `RevisionService.reviewRevisionWithRecall`, around line 220, verifies card ownership but does not atomically consume the check. Store confirmation state and make repeated confirmation idempotent, or reject it without applying another update. Verify concurrent duplicate requests as well as sequential retries.

### 4. Regeneration returns the previous budget

**Code:** `REGENERATED_BUDGET_STALE`. **Evidence:** M#273 and M#620.

| Settings change | Regenerated work | Returned `dailyBudgetMinutes` |
|---|---:|---:|
| 90 → 120 minutes | 117 minutes | 90 |
| 120 → 60 minutes | 60 minutes | 120 |

The selection uses the new budget, but its response reports the old one. This misrepresents whether the plan fits the user's available time.

**Cause:** [PlannerService.java](../backend/src/main/java/com/preptrack/planner/service/PlannerService.java), `regeneratePlan` around line 77, updates totals but never updates the stored budget. Persist the effective budget with the regenerated plan.

### 5. Planning and forecasting disagree about today's date

**Code:** `DATE_BOUNDARY_DISAGREEMENT`. **Evidence:** M#1006–1007.

At the same instant, `2026-10-27T23:45:00Z`, with host timezone Asia/Kolkata and stored user timezone UTC:

```json
GET /api/plan/today                 -> { "date": "2026-10-28" }
GET /api/forecast/interviews/{id}    -> { "asOfDate": "2026-10-27" }
```

**Cause:** [PlannerController.java](../backend/src/main/java/com/preptrack/planner/controller/PlannerController.java) uses static `LocalDate.now()`, while [ForecastService.java](../backend/src/main/java/com/preptrack/forecast/service/ForecastService.java) uses `TimeService` with user settings. Use the same injectable clock and user timezone for plans, attempts, revisions, interviews, and forecasts. Verify due-date and completion behavior around midnight after fixing it.

### 6. Pattern-gap follow-up does not schedule the prerequisite

**Code:** `PREREQUISITE_ONLY_TEXT`. **Evidence:** M#1016–1017.

A failed Trapping Rain Water attempt with `PATTERN_GAP` returns `EASIER_PREREQUISITE`. The next day's plan schedules Trapping Rain Water again with the text “Try Next Greater Element I first”; the named prerequisite is absent, despite ample budget.

**Cause:** [CandidateCollector.java](../backend/src/main/java/com/preptrack/planner/engine/CandidateCollector.java) retains the original failed problem and copies the follow-up description. It does not substitute or schedule the linked prerequisite. This misses the project document's promised easier-first recovery behavior. Make the prerequisite an actual plan action with its own time estimate and completion state.

## P2 — calculation explanations, scheduling, and validation

| # | Finding and evidence | User impact and correction |
|---|---|---|
| 7 | **Skipped work is included in regenerated totals.** `SKIPPED_TIME_IN_PLAN_TOTAL`, M#88: 105 reported minutes against a 90-minute budget, while active work fits. | Separate active, completed, and skipped totals; skipped work should not inflate the active workload or value. Source: `PlannerService.regeneratePlan` total calculation. |
| 8 | **Value breakdown omits an applied multiplier.** `VALUE_BREAKDOWN_INCOMPLETE`, e.g. M#116: visible components produce 1.716, returned total is 1.89. | The overdue bonus in `MaintenanceScorer` affects value but is absent from the response breakdown. Expose it so the explanation reconstructs the score. This is an incomplete explanation, not proof that the hidden multiplication is wrong. |
| 9 | **Diversity penalty applies without selected repetition.** `DIVERSITY_PENALTY_WITHOUT_REPEAT`, M#913: Single Number has a 0.5 penalty, with no other selected new Bit Manipulation problem. | `CandidateCollector` marks patterns while enumerating candidates, before knapsack selection. Apply repetition penalties to the selected set, or use an objective that accounts for joint selection. |
| 10 | **Negative study signals are accepted.** `NEGATIVE_ATTEMPT_SIGNALS`, M#992 accepts `timeTakenSec=-120`, `hintsUsed=-3`. | Reject negative values with 400 before storing or updating learning state. Source: `CreateAttemptRequest`. |
| 11 | **Missing failure reason bypasses adaptive follow-up.** `FAILURE_REASON_OPTIONAL`, M#993: an assisted solve without a reason succeeds with no follow-up type. | Require a reason for assisted/failed coding outcomes, or explicitly return a documented generic recovery action. Source: `AttemptService.recordAttempt`. |
| 12 | **Hints and independent success contradict each other.** `CONTRADICTORY_SOLVE_SIGNALS`, M#994 accepts four hints with independent credit. | Define the meaning of independence and validate/normalize conflicting signals. Current input permits overstated acquisition and stability. |
| 13 | **An invalid revision outcome becomes a real failure.** `INVALID_REVIEW_BECOMES_FAILURE`, M#995: `NOT_A_REAL_OUTCOME` returns 200 as `COULD_NOT_SOLVE`. | Reject unknown outcomes/overrides with 400. Do not mutate skill or scheduling on malformed input. Source: `RevisionService.parseAttemptOutcome` and `parseRecallGrade`. |
| 14 | **An item can be completed and skipped simultaneously.** `ITEM_BOTH_COMPLETE_AND_SKIPPED`, M#1010. | Enforce mutually consistent item transitions. Reject skipping completed work, or provide an explicit undo workflow. Source: `PlannerService.skipPlanItem`. |
| 15 | **One plan repeats memory-only work for the same pattern.** `REDUNDANT_PATTERN_RECALLS`, M#1026 schedules Two Sum, Contains Duplicate, and Valid Anagram as three five-minute Hash Map Lookup recalls. | The first successful recall already refreshes that pattern's retention. Account for marginal memory benefit across selected cards. Preserve multiple same-pattern coding exercises where they teach different applications; this finding concerns recall-only duplication. |
| 16 | **Missing nested topic IDs cause HTTP 500.** `MALFORMED_TOPICS_HTTP_500`, S#17 sends `topics:[{"wasWeak":true}]`. | Add nested validation and return 400. Source: `ConfirmTopicsRequest` and the missing `@Valid` on `InterviewController.confirmTopics`. |
| 17 | **Unfinished interview confirmations can replay the weakness boost.** `UNFINISHED_TOPIC_REPLAY`, S#19–20: repeat the same weak topic with `outcome:"UPCOMING"`; boost increases 0.3 → 0.6. | Confirmation state is inferred from a terminal interview outcome. Track it explicitly and apply each interview/pattern contribution once. Standard repeated `PARTIAL` confirmation does not repeat the boost; the unfinished outcome bypasses that protection. Stored boost was read directly to verify the hidden model change. |
| 18 | **Routing errors become internal server errors.** `ROUTING_ERRORS_HTTP_500`, S#25–26: unsupported `GET /api/attempts` and an unknown API path return 500. | Preserve 405 and 404 semantics. `GlobalExceptionHandler`'s catch-all currently catches framework routing exceptions. This also disguises missing endpoints as server failures. |

## Missing API features

| # | Missing feature | Evidence and minimum useful implementation |
|---|---|---|
| 19 | **Revision-only settings.** `NO_REVISION_ONLY_MODE` | M#1001 rejects `maxNewProblemsDay:0`. Both slot limits currently have a minimum of 1. Permit zero where intentional, and generate a valid revision-only or acquisition-only plan. |
| 20 | **Readable and editable timezone.** `TIMEZONE_NOT_USER_CONFIGURABLE` | M#1002 accepts a timezone update but ignores it; M#1003 omits timezone from settings. Expose an IANA timezone, validate it, and use it consistently with finding 5. |
| 21 | **Attempt history and user-note retrieval.** `NO_ATTEMPT_HISTORY_API` | S#25 and S#42 fail; source inspection confirms the write-only attempt controller and absence of the project document's `GET /api/problems/{id}/attempts`. Problem detail also omits the saved insight note. Add a paginated, owned history API and a note read/edit contract. |

History needs a consistent event model as well as a read route. The month's learner submitted 57 attempts through `/api/attempts` and 117 reviews through revision routes. Revision reviews update aggregate counts, but do not append `UserProblemAttempt` rows; only recall reviews have separate recall-check records. A future history API backed solely by `user_problem_attempts` would omit the 50 full coding-review events in this journey. Record review events with their outcome, reason, timing, hints, and source, while keeping memory-only recalls distinguishable from coding attempts.

## Model and product improvements to consider

These are calibration or product gaps, separate from the 21 confirmed findings above.

1. **Expose a current-progress timeline.** Preserve daily skill, retention, completed work, and historical policy/forecast versions. Users need to see their actual improvement separately from a changing future interview projection. Current pattern-state and forecast reads do not provide that timeline.
2. **Revisit the first-review policy.** A first independent solve starts at stability 1.5 days, but the first review is scheduled three days later. The model predicts `exp(-3/1.5) ≈ 13.5%` retention then, far below the configured 70% retention target used for subsequent reviews. This is an explicit policy exception, rather than an arithmetic bug. Decide whether that initial delay is intentional and calibrate it against observed recall outcomes.
3. **Adapt time estimates and learning assumptions to observed behavior.** Attempt timing and hints are stored, while planning uses catalog time estimates and learning gains depend on outcome and pattern weight. Collect valid review signals and use sufficient history to improve estimates, with a stable fallback for new users.
4. **Calibrate forecast uncertainty.** The API correctly labels its output as an approximation. It assumes deterministic future study, does not replay the full planner, and can count a multi-pattern problem in multiple pattern pools. Add empirical completion/success assumptions and a range or scenario comparison before treating its score as a dependable readiness assessment.
5. **Complete the disclosed catalog and recovery paths.** NeetCode contains 117 items. Expand reviewed content, reference approaches, and prerequisite coverage so sparse patterns and missing relations do not weaken recovery recommendations.

## Reproduction and follow-up order

From `backend`, run:

```powershell
.\mvnw.cmd '-Dtest=ThirtyDayBackendAudit' test
```

The audit is explicitly selected because its name does not match the default unit-test naming pattern. A successful Maven exit means both audit scenarios completed and exported evidence; it does **not** mean the product has no findings. The harness deliberately collects defects so later days can still be exercised.

Recommended order:

1. Restore recall and feedback flows; make recall confirmation atomic and idempotent.
2. Unify user-local dates; fix regenerated budget and workload totals.
3. Schedule real prerequisites; correct selected-set diversity and recall redundancy.
4. Validate outcome/signal/topic inputs and interview confirmation state; preserve HTTP error semantics.
5. Add coherent history/notes and progress reads, then revision-only settings and calibration improvements.

After fixes, convert the relevant failing scenarios into regression assertions and repeat the journey. Also run an isolated PostgreSQL integration pass, concurrency tests, and live-AI grading evaluation. This run does not cover browser rendering, real elapsed 30-day JWT expiry, restart/deployment behavior, concurrent requests, PostgreSQL-specific behavior, or live external AI responses.
