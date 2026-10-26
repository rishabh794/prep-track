# Phase 7: simulator and proof

Open **Simulator** in the signed-in navigation (`/simulator`). The default experiment compares sheet order, random with seven-day reviews, fixed-ease SM-2, and PrepTrack across 30 days and 50 paired learners under both exponential and power-law forgetting. Settings, sample deviations, daily snapshots, baseline differences, model version, seed, and caveats are included in JSON. Download a text report from the results page. The `runId` in the URL reloads the saved result after a refresh.

## API

All endpoints require the usual bearer token. Results belong to the account that ran them. There is no admin role in the existing authentication model, so the demo simulator is available to signed-in accounts with bounded workload and one active run per application instance.

```http
POST /api/simulator/run
Content-Type: application/json
Authorization: Bearer <token>

{
  "days": 30,
  "learnerCount": 50,
  "dailyBudgetMinutes": 90,
  "maxNewProblems": 3,
  "maxRevisions": 10,
  "targetSkill": 0.8,
  "seed": 42,
  "sheetId": null
}
```

An empty JSON object uses those defaults. `sheetId: null` selects the seeded NeetCode 150 sheet; the report records actual catalog counts rather than assuming the seed contains 150 problems. A successful POST returns 201, the complete result, and a Location header. Saved runs survive application restarts in `simulation_runs` (Flyway V12).

- `GET /api/simulator/results/{runId}` returns JSON.
- `GET /api/simulator/results/{runId}/summary` returns the text report with caveats.
- Invalid settings return 400; missing sheets/runs and another user's runs return 404. A concurrent run returns 429.

Limits: days 1–60, learners 1–100, budget 5–240 minutes, new slots 0–5, review slots 0–10, target 0.1–1. At least one slot must be enabled. To bound compute, `days × learners × budget × (new slots + 1) × (review slots + 1)` must be at most 12,000,000. Catalogs must contain 1–500 problems with pattern links and positive time estimates. All strategies respect the same limits; unused time is reported honestly.

## What the experiment measures

Learners start with no practiced patterns. Learning rate is sampled from `LogNormal(-2.3, 0.5)` and initial memory stability from `LogNormal(0, 0.5)`. Matching learner indices have identical parameters across all strategies and curves. Outcome noise is keyed to learner, day, and problem, independent of strategy execution order. The random baseline has its own deterministic selection stream.

Hidden skill improves by `learningRate × linkWeight × (1 - skill) × feedback`, where feedback is 1 for independent solving, 0.65 for help, and 0.2 for failure. Recall-only study multiplies that gain by 0.25. Hidden stability grows by `1 + learningRate × feedback × exposure`, with exposure 4 for recall and 2 for coding. Skill is clamped to [0,1] and stability to 365 days. Outcome probability uses weighted retained skill, difficulty, and Gaussian noise. These are explicit synthetic assumptions, not human measurements.

The planner sees only observed outcomes and its own estimated pattern state, updated by the same observation model as the real app. It always estimates exponential forgetting. PrepTrack uses the production acquisition/maintenance scorers, revision time estimator, and bounded knapsack. Offline candidate collection omits interview boosts and prerequisite substitution. Review intervals follow the production first-review/count/stability rule. Baselines use no pattern state for scheduling: sheet order never revises, random reviews at seven days, and per-problem SM-2 uses intervals 1, 6, then ×2.5 (failure resets to one day). All strategies use observed follow-up modes for review costs.

Retention is measured **24 hours after each study day** across every pattern in the selected catalog; untouched patterns contribute zero. This avoids granting perfect scores to patterns just practiced. Retained skill (`skill × retention`) and distinct solved coverage accompany retention so a strategy cannot hide shallow learning behind frequent review. Coverage includes assisted solves. Mean and sample standard deviation are across learners; paired differences are percentage-point differences, not relative percentage improvements or claims of statistical significance. One learner has standard deviation zero.

Reports never force a winner or substitute the illustrative percentages from the project plan. A positive paired difference means PrepTrack had higher retention for that experiment. Power-law outcomes may shrink or reverse the advantage. Synthetic results need complementary real-world pilot evidence.

## Generate a demo account

With the normal database/JWT environment configured and Java available, run from the project root:

```powershell
.\scripts\create-demo-data.ps1 -Email 'demo@preptrack.local'
```

The script prompts for a password without echoing it, starts the backend on a temporary port with the explicit `demo` profile, generates history, and exits. Startup seed loading runs before demo generation. The generator creates a fresh account, selects NeetCode 150, sets a 90-minute budget, records ten days ending yesterday in Asia/Kolkata, saves completed historical plans/attempts, updates pattern estimates and revision cards, and adds a sample upcoming interview. Historical timestamps are explicitly backdated. All sample records are marked synthetic; no AI calls are made. An existing email is rejected without changing its data. No demo account is created during normal startup.

For a backend kept running after generation, set `DEMO_EMAIL` and `DEMO_PASSWORD` and start it with `--spring.profiles.active=demo` without `--preptrack.demo.exit=true`.

## Verification

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
.\node_modules\.bin\tsc.cmd --noEmit
npm run build
```

The simulator tests cover both curves, sampled/paired learners, hidden-state isolation, repeatability, daily budget and slot enforcement, baseline intervals, sample deviations, input validation, authentication, persisted-result ownership, production-style closed persistence contexts, and the ten-day demo. The default 30-day/50-learner experiment writes report artifacts to `backend/target/simulator-proof/default-run.json` and `default-run.txt` during the backend test run.
