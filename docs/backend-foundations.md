# Backend foundations for the next frontend iteration

The planner now uses a validated central policy, forecasts expose their assumptions and available data, company preferences are stored reference data, and the catalog has versioned updates and an integrity report. Flyway V13 and V14 create the supporting reference-data tables; they apply on normal backend startup. These changes do not require resetting users, attempts, daily plans, or interview records.

## Engine policy

`preptrack.engine` in `application.yml` is now consumed by acquisition/maintenance scoring, pattern observation updates, revision scheduling and time estimates, interview boosts, and default settings for newly registered users or missing settings. Existing saved user budgets and caps remain authoritative. The active defaults preserve the earlier calculations: assisted skill gain is **0.03**, failure skill decay **0.02**, and assisted stability multiplier **1.0**; the previously unused declarations of different values are no longer misleading.

Configuration includes learning gains, initial/maximum stability, maintenance/retry/diversity/follow-up multipliers, overdue bonuses, review intervals, recall and coding cost defaults, retention target, interview proximity, and boost decay. Invalid values and inconsistent ranges fail startup. Increment `policy-version` when intentionally changing a policy used for comparisons.

Authenticated `GET /api/planner/policy` returns a snapshot of the active, non-secret policy. New simulator JSON/text reports also capture a copied policy snapshot; old stored reports without one still load. The synthetic learner's hidden memory assumptions remain separate from these planner estimates.

Example overrides:

```yaml
preptrack:
  engine:
    policy-version: "2"
    recall-minutes: 7
    maintenance-floor: 0.20
    interview-boost-decay-days: 21
```

This requires a backend restart. There is no public policy-writing endpoint.

## Forecast contract

`GET /api/forecast/interviews/{interviewId}` retains ownership checks and existing projection fields. It adds:

- `status`: `AVAILABLE`, `ONBOARDING_REQUIRED`, `INSUFFICIENT_DATA`, or `INTERVIEW_PASSED`.
- `statusMessage`, actual `dailyBudgetMinutes`, new/review caps, and target skill.
- `asOfDate`, `simulationDays`, `projectedThroughDate`, and `horizonCapped`.
- `model`, `modelVersion`, `policyVersion`, and `limitations`.

Unavailable forecasts have `overallReadiness: null` and empty projections. No selected sheet, empty catalogs, and missing pattern links no longer cause imaginary problem availability. Dates follow the user's timezone. New-problem cost uses the mean estimate of actual available problems in each pattern; review cost uses the active engine policy. The page displays effective settings, model limits, and onboarding/insufficient-data states.

The model is explicitly a **pattern-level approximation**, not a replay of the daily knapsack planner or a probability of passing an interview. Its horizon and approximate learning assumptions are validated under `preptrack.forecast`. A capped forecast reports its actual end date instead of implying it projected all the way to a later interview. Multi-pattern problem pools can overlap, which remains stated in the limitations.

## Company-pattern profiles

V13 stores profiles, unique normalized aliases, and pattern weights linked to stable pattern IDs. The maintained source is `backend/src/main/resources/seed/company-profiles.json`. Current Google, Meta/Facebook, and Amazon values are migrated manual preferences with **HEURISTIC** provenance and notes explicitly saying they are not measured question frequencies. They are not newly asserted facts about those companies.

Authenticated read-only endpoints:

- `GET /api/companies`
- `GET /api/companies/{slug}`
- `GET /api/companies/resolve?name=Facebook`

Responses expose aliases, provenance, source version/notes, default weight, and per-pattern weights. Matching uses normalized exact aliases and stable pattern slugs, so an unrelated company containing “Google” in its name does not inherit Google's profile. Unknown companies or unlisted patterns receive neutral weight **1.0**.

Profiles are bulk-loaded into an immutable startup snapshot; planning does not add per-candidate database queries. To update profiles, edit the JSON and increment its version, then restart. Seed validation rejects ambiguous aliases and unknown pattern slugs. Updated releases reconcile seeded aliases/weights; repeat runs repair missing entries. Changing content under the same version is rejected. There is no admin UI or live editing API yet.

## Versioned reference catalog

`backend/src/main/resources/seed/catalog-manifest.json` declares the release version, JSON resources, and each sheet's metadata and expected full size where known. V14 records applied versions and SHA-256 checksums and adds expected-size/release metadata to sheets. Fingerprints use canonical JSON so formatting, key order, and Windows/Linux line endings do not count as data changes.

On startup the loader validates the complete bundle before writing, then synchronizes reference metadata in one transaction. It rejects unresolved source/pattern/relation references, duplicate source identities, duplicate canonical sheet entries, invalid times/weights/orders, and conflicting canonical identities. Sources are used to preserve an existing problem ID even if its display title changes, so attempt history stays linked. A new manifest version is required when content changes; known older releases cannot silently restore old metadata over the latest applied release.

Seed-owned problem descriptions, reference approaches, estimates, source details, pattern links/weights, sheet metadata/order, and relation metadata can be updated. Missing records are repaired on repeated runs even when overall database counts appear sufficient. Unrelated problems and user records are retained; removing an item from a bundle does not automatically purge pre-existing problems or extra associations. Check the integrity response when retiring catalog data.

Authenticated `GET /api/catalog/health` reports actual counts, manifest/applied versions, per-sheet availability, missing bundled entries, and structured `INFO`/`WARNING`/`ERROR` issues. `/api/sheets/available` remains public and adds `expectedProblems`, `countStatus`, and `seedVersion` to its existing actual `totalProblems`.

Current data is reported honestly:

| Sheet | Available | Declared full size | Count status |
|---|---:|---:|---|
| Blind 75 | 75 | 75 | COMPLETE |
| NeetCode 150 | 117 | 150 | INCOMPLETE |
| Striver A-Z | 58 | Not verified | UNVERIFIED |

`countStatus` compares counts with the declared size; it is not certification of the external curriculum. The known 33-problem NeetCode shortfall is a warning, not a startup failure. Bad internal references fail seed loading. No placeholder problems are created to fill missing curriculum entries.

## Verification

Tests cover active policy overrides and invalid settings, preserved defaults, old simulator report compatibility, honest forecast availability and dates, company aliases/provenance/neutral fallbacks, reference-data idempotency, version checks, safe metadata updates with existing attempt history, canonical fingerprints, catalog integrity errors, and API authentication. Run the full backend suite and frontend type/build checks before relying on a new policy or seed release.
