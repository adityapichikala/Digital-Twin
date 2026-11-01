# TwinDigital MVP Demonstration Script

This is a suggested three-minute walkthrough of the local MVP. Every capability below is labeled with its current implementation and evidence status. Do not present planned or unmeasured results as observed behavior.

## Status key

- **Implemented** — present in source or deployment configuration.
- **Verified** — exercised locally or covered by the stated test count.
- **Planned** — the verification or integration is not yet complete.

## Verified evidence

- **[Verified — run locally on 2026-11-01]** The Compose stack started with all nine services healthy. Gateway login returned an `ADMIN` session, and the authenticated topology endpoint returned 18 nodes and 19 edges from its built-in demo graph.
- **[Verified — automated test counts]** Gateway Jest tests: 17/17 passed. Java service tests recorded in [`RESULTS_TEMPLATE.md`](./testing/RESULTS_TEMPLATE.md): 91/91 total (auth 19, cluster-sync 25, topology 8, simulation 24, cost-risk 15).
- **[Verified — run locally on 2026-11-01]** Frontend typecheck and build passed; lint completed with three warnings and no errors.
- **[Planned]** Live-cluster synchronization latency, load, chaos, and simulation accuracy remain not yet run.

## Before the demo

Start the local MVP from the repository root:

```bash
docker compose up --build -d
docker compose ps
```

Open the frontend at `http://localhost:8088`. The default local login is `admin` / `AdminPassword123!` unless `.env` overrides it. Without `KUBECONFIG_BASE64`, cluster-sync has no live cluster connection and topology uses the seeded demo graph.

## Suggested timeline

| Time | Capability and status | What to show |
|---|---|---|
| 0:00–0:30 | **[Verified — local Compose run, 2026-11-01]** Service startup and gateway login API | Show `docker compose ps`, then call the login endpoint and show the returned `ADMIN` role. The gateway API login was verified; this does not claim a browser-based end-to-end test. |
| 0:30–1:10 | **[Implemented] [Verified — local API run, 2026-11-01]** Topology page and seeded graph | Open the topology route if presenting the UI, then show the authenticated API response: 18 nodes and 19 edges. Clarify that the API run used demo data, not a live EKS snapshot. |
| 1:10–1:45 | **[Implemented] [Planned verification]** Cluster sync and blast-radius analysis | Explain the List+Watch integration and BFS traversal in the code. State that live cluster sync latency and blast-radius accuracy have not yet been measured. |
| 1:45–2:20 | **[Implemented] [Planned verification]** What-if simulation | Show the simulation workbench and describe its five implemented checks: scheduling fit, limit sanity, node removal/PDB impact, HPA impact, and blast radius. Avoid quoting latency, risk accuracy, or a specific result as measured. |
| 2:20–2:45 | **[Implemented] [Planned verification]** Cost and right-sizing | Show the cost-risk page and explain its Prometheus and AWS Cost Explorer integrations. The local Compose stack does not include Prometheus or AWS billing credentials, so do not present live spend or savings figures. |
| 2:45–3:00 | **[Planned]** Measurement roadmap | Close with the next validation work: live-cluster comparison, load testing, chaos testing, and simulation accuracy. Each remains “not yet run” in the results template. |

## Optional API evidence

To repeat the verified gateway flow, sign in and copy `accessToken` from the response:

```bash
curl -sS -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"AdminPassword123!"}'
```

Use that token to retrieve topology:

```bash
curl -sS http://localhost:3000/api/v1/topology \
  -H "Authorization: Bearer <accessToken>"
```

## Limitations to state during the presentation

- **[Planned]** No live EKS cluster was connected during the recorded local run; the graph came from the built-in demo data.
- **[Planned]** Load, chaos, and simulation-accuracy runs have not been executed. Their results are marked “not yet run” in [`RESULTS_TEMPLATE.md`](./testing/RESULTS_TEMPLATE.md).
- **[Implemented, external setup required]** Prometheus and AWS Cost Explorer integrations exist in code but need external services and credentials for live data.
- **[Verified — frontend lint, 2026-11-01]** The frontend linter reports three React effect warnings; typecheck and build pass.
