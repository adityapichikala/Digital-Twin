# TwinDigital Frontend SPA

The TwinDigital user interface is a high-performance Single Page Application (SPA) built with React 18, Vite, and TypeScript. It provides interactive digital twin visualization, topology graphs, what-if simulations, and AWS/Kubernetes cost analytics.

## Core Capabilities
- **Topology Canvas**: Interactive directed acyclic graph (DAG) rendered with `@xyflow/react` and Dagre hierarchical layout, visualizing Kubernetes Nodes, Pods, Deployments, and Services.
- **Blast Radius Analyzer**: Inspect upstream callers and downstream dependencies impacted by modifying or deleting workloads.
- **What-If Simulation Workbench**: Test proposed Kubernetes change sets (resource resizing, pod scaling, node drain) against real cluster capacity constraints before touching production.
- **Cluster Cost & Right-Sizing Dashboard**: 7-day p95 PromQL actual usage analysis vs. requests, AWS Cost Explorer billing breakdown, and right-sizing recommendations.

## Development & Build Commands
```bash
# Start local development server (proxies /api to gateway:8080 or :3000)
npm run dev

# Run TypeScript type check
npm run typecheck

# Run linter
npm run lint

# Build production bundle
npm run build
```
