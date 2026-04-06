# Platform Engineering Reference

> Reference for: IDPs, Backstage, DORA/SPACE metrics, golden paths, developer experience, toil reduction

## Table of Contents
1. [Platform Engineering Overview](#platform-engineering-overview)
2. [Internal Developer Platforms](#internal-developer-platforms)
3. [Backstage](#backstage)
4. [Other IDP Tools](#other-idp-tools)
5. [Golden Paths](#golden-paths)
6. [DORA Metrics](#dora-metrics)
7. [SPACE Framework](#space-framework)
8. [Developer Experience](#developer-experience)
9. [Toil Reduction](#toil-reduction)
10. [Platform Team Operating Model](#platform-team-operating-model)

---

## Platform Engineering Overview

**55% of organizations** adopted platform engineering by 2025. Gartner predicts **80% will have dedicated platform teams by 2026**. Companies using IDPs report delivering updates **up to 40% faster** while cutting operational overhead nearly in half.

### What platform engineering is
Platform engineering builds and maintains **Internal Developer Platforms (IDPs)** — self-service layers that abstract infrastructure complexity from development teams. The goal is to reduce cognitive load on developers while maintaining operational standards.

### What platform engineering is NOT
- Not a rebrand of DevOps
- Not building tools for tool's sake
- Not gatekeeping infrastructure access
- Not a central team that becomes a bottleneck

### Core principles
1. **Platform as a product**: Treat developers as customers. Measure adoption, satisfaction, and NPS.
2. **Self-service by default**: Developers should provision what they need without filing tickets.
3. **Guardrails, not gates**: Enable speed with safety constraints built into the platform.
4. **Golden paths**: Provide opinionated, well-lit paths that make the right thing the easy thing.
5. **Continuous improvement**: Iterate based on developer feedback and usage data.

---

## Internal Developer Platforms

### IDP architecture layers
```
┌─────────────────────────────────────────┐
│         Developer Interface              │  ← Backstage, CLI, API
│         (Portal, Templates, Docs)        │
├─────────────────────────────────────────┤
│         Integration & Orchestration      │  ← ArgoCD, Flux, Crossplane
│         (Workflows, Pipelines, GitOps)   │
├─────────────────────────────────────────┤
│         Resource Management              │  ← Terraform, Crossplane, Helm
│         (Compute, Storage, Networking)   │
├─────────────────────────────────────────┤
│         Observability & Security         │  ← OTel, Prometheus, Falco
│         (Monitoring, Logging, Policies)  │
├─────────────────────────────────────────┤
│         Infrastructure                   │  ← AWS/GCP/Azure, Kubernetes
│         (Cloud, Clusters, Networks)      │
└─────────────────────────────────────────┘
```

### IDP capabilities checklist
- [ ] **Service catalog**: Inventory of all services, owners, dependencies, API docs
- [ ] **Self-service provisioning**: Create databases, caches, queues, K8s namespaces via portal
- [ ] **CI/CD templates**: Standardized pipelines with security scanning baked in
- [ ] **Environment management**: Spin up ephemeral environments for testing
- [ ] **Secrets management**: Self-service access to secrets with audit trail
- [ ] **Observability templates**: Pre-configured dashboards and alerts per service type
- [ ] **Documentation hub**: Centralized, searchable technical documentation
- [ ] **Cost visibility**: Per-team, per-service cost allocation and budgets

---

## Backstage

**89% market share** among IDP tools. Created by Spotify. CNCF Incubating project. Weekly releases (v1.44+ as of late 2025).

### Core features
- **Software Catalog**: Central registry of all software (services, libraries, data pipelines, ML models, documentation)
- **Software Templates**: Scaffolding for new services with best practices baked in
- **TechDocs**: Docs-like-code (Markdown → published documentation site)
- **Search**: Unified search across catalog, docs, and plugins
- **Plugin ecosystem**: 200+ community plugins

### Architecture
```
┌─────────────────────────────────────┐
│          Backstage Frontend          │  React, Material UI
├─────────────────────────────────────┤
│          Backstage Backend           │  Node.js
├──────────┬──────────┬───────────────┤
│ Catalog  │Templates │   TechDocs    │  Core plugins
├──────────┼──────────┼───────────────┤
│ GitHub   │ ArgoCD   │   Kubernetes  │  Integration plugins
│ PagerDuty│ Grafana  │   Vault       │
└──────────┴──────────┴───────────────┘
│          PostgreSQL Database          │
└─────────────────────────────────────┘
```

### Software Template example
```yaml
apiVersion: scaffolder.backstage.io/v1beta3
kind: Template
metadata:
  name: spring-boot-service
  title: Spring Boot Microservice
  description: Create a new Spring Boot microservice with CI/CD, monitoring, and GitOps
  tags: [java, spring-boot, recommended]
spec:
  owner: platform-team
  type: service
  parameters:
    - title: Service Information
      required: [name, owner, description]
      properties:
        name:
          title: Service Name
          type: string
          pattern: '^[a-z][a-z0-9-]*$'
        owner:
          title: Owner Team
          type: string
          ui:field: OwnerPicker
        description:
          title: Description
          type: string
    - title: Infrastructure
      properties:
        database:
          title: Database
          type: string
          enum: [none, postgresql, redis]
          default: none
        environment:
          title: Target Environment
          type: string
          enum: [dev, staging, production]
  steps:
    - id: fetch-template
      name: Fetch Template
      action: fetch:template
      input:
        url: ./skeleton
        values:
          name: ${{ parameters.name }}
          owner: ${{ parameters.owner }}
          database: ${{ parameters.database }}

    - id: publish-repo
      name: Publish to GitHub
      action: publish:github
      input:
        repoUrl: github.com?owner=myorg&repo=${{ parameters.name }}
        description: ${{ parameters.description }}

    - id: register-catalog
      name: Register in Catalog
      action: catalog:register
      input:
        repoContentsUrl: ${{ steps['publish-repo'].output.repoContentsUrl }}
        catalogInfoPath: /catalog-info.yaml

    - id: create-argocd-app
      name: Create ArgoCD Application
      action: argocd:create-application
      input:
        appName: ${{ parameters.name }}
        repoUrl: ${{ steps['publish-repo'].output.remoteUrl }}
        path: k8s/overlays/${{ parameters.environment }}

  output:
    links:
      - title: Repository
        url: ${{ steps['publish-repo'].output.remoteUrl }}
      - title: Service in Catalog
        icon: catalog
        entityRef: ${{ steps['register-catalog'].output.entityRef }}
```

### Backstage catalog-info.yaml
```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: api-gateway
  description: API Gateway service
  annotations:
    github.com/project-slug: myorg/api-gateway
    backstage.io/techdocs-ref: dir:.
    argocd/app-name: api-gateway
    grafana/dashboard-selector: "app=api-gateway"
    pagerduty.com/service-id: PXXXXXX
  tags:
    - java
    - spring-boot
    - tier-1
  links:
    - url: https://api.example.com
      title: Production URL
spec:
  type: service
  lifecycle: production
  owner: platform-team
  system: core-platform
  providesApis:
    - api-gateway-api
  dependsOn:
    - component:user-service
    - resource:postgres-main
```

### New Frontend System (Release Candidate, late 2025)
Modular, micro-frontend architecture. Allows plugins to be loaded dynamically. Backstage UI (BUI) design system in alpha.

### MCP Integration
Backstage now supports MCP (Model Context Protocol) for AI tool integration, combining AI assistants with the engineering source of truth.

---

## Other IDP Tools

### Humanitec Platform Orchestrator v2 (September 2025)
- Expanded beyond Kubernetes to any compute target (serverless, VMs)
- AI-first design for intelligent resource matching
- Score specification for workload definition (open-source)
- Best for: Teams wanting a commercial, opinionated platform orchestrator

### Kratix (Kubernetes-native)
- **Promises**: Contracts specifying what the platform provides as-a-service
- Inner-source collaboration model
- Built on Kubernetes controllers
- Best for: Teams wanting to build their IDP from Kubernetes primitives

### Port
- No-code developer portal
- Self-service actions backed by GitHub Actions, GitLab CI, or custom webhooks
- Scorecards for tracking engineering standards adoption
- Best for: Teams wanting a portal without building Backstage from scratch

---

## Golden Paths

### Definition
A golden path is an **opinionated, supported path** for accomplishing a common task. It's the recommended way to do things, with guardrails and automation built in. Developers can deviate, but the golden path is the path of least resistance.

### Golden path examples

**New service golden path:**
1. Use Backstage template to scaffold → generates repo with CI/CD, Dockerfile, Helm chart, monitoring
2. Push code → CI pipeline runs lint, test, build, scan, sign automatically
3. ArgoCD detects new chart → deploys to dev automatically
4. PR to promote to staging → automated canary with Argo Rollouts
5. Manual approval → production deployment

**Database provisioning golden path:**
1. Developer requests PostgreSQL via Backstage self-service form
2. Crossplane Composition provisions RDS instance with encryption, backup, monitoring
3. External Secrets Operator injects connection string into application namespace
4. Grafana dashboard auto-generated from template

**Incident response golden path:**
1. Alert fires → PagerDuty routes to on-call
2. Responder opens incident channel (automated via incident.io)
3. Runbook linked in alert annotation
4. Post-mortem template auto-populated with timeline from PagerDuty
5. Action items tracked in Jira/Linear with SLO impact analysis

### Golden path anti-patterns
- ❌ Mandating the golden path (it should be attractive, not forced)
- ❌ Building golden paths without developer input
- ❌ No escape hatch for legitimate exceptions
- ❌ One golden path for all team sizes and use cases

---

## DORA Metrics

### 2025 evolution
The 2025 DORA report (now "State of AI-Assisted Software Development"):
- Replaced Elite/High/Medium/Low with **seven team archetypes** based on eight measures
- Added **Rework Rate** as fifth core metric
- Reclassified Failed Deployment Recovery Time from stability to throughput

### Core metrics
| Metric | What it measures | Elite benchmark |
|--------|-----------------|-----------------|
| **Deployment Frequency** | How often code reaches production | Multiple times per day |
| **Lead Time for Changes** | Commit to production | Less than 1 hour |
| **Change Failure Rate** | % of deployments causing failure | 0-5% |
| **Failed Deployment Recovery Time** | Time to restore service | Less than 1 hour |
| **Rework Rate** (new) | % of changes that are rework | Low (exact threshold TBD) |

### Key 2025 finding on AI
AI adoption **positively correlates with delivery throughput** but also **correlates with higher instability** — more change failures and increased rework. AI functions as an amplifier: accelerating high performers, magnifying dysfunction in struggling teams.

### Measuring DORA
- **Deployment Frequency**: Count deployments from CI/CD system (ArgoCD sync events, GitHub deployments API)
- **Lead Time**: Measure from first commit to production deployment timestamp
- **Change Failure Rate**: Incidents tagged to deployments / total deployments
- **Recovery Time**: Incident duration (detection to resolution)
- **Tools**: Sleuth, LinearB, Faros AI, or custom dashboards from CI/CD + incident data

---

## SPACE Framework

Complements DORA by adding human and systemic dimensions. Adopted by Microsoft, GitHub, Netflix, Spotify.

### Five dimensions
| Dimension | What it measures | Example metrics |
|-----------|-----------------|-----------------|
| **Satisfaction** | Developer happiness and fulfillment | Survey scores, eNPS, burnout indicators |
| **Performance** | Outcomes and impact | Customer impact, code quality, reliability |
| **Activity** | Volume of work | Commits, PRs, deployments, reviews |
| **Communication** | Collaboration quality | PR review turnaround, knowledge sharing |
| **Efficiency** | Flow and minimizing waste | Build times, context switches, wait times |

### Using SPACE effectively
- Measure **at least 3 dimensions** — never optimize for just Activity
- **Never use SPACE for individual performance evaluation** — it's for team and system improvement
- Combine with DORA for a complete picture: DORA measures delivery, SPACE measures the experience
- Survey quarterly, instrument continuously

---

## Developer Experience

### Key DX metrics to track
- **Time to first deploy**: How long from "git clone" to first production deployment
- **Build time**: P50 and P95 CI pipeline duration
- **Environment spin-up time**: Time to get a working development environment
- **PR merge time**: Time from PR open to merge
- **Cognitive load**: Number of tools, dashboards, and portals a developer must use

### Reducing cognitive load
- **Single pane of glass**: Backstage as the starting point for all development activities
- **Standardized tooling**: Same CI/CD, monitoring, and deployment patterns across teams
- **Self-service infrastructure**: No tickets required for common resources
- **Documentation as code**: TechDocs alongside source code, auto-published
- **Pre-configured dev environments**: Dev containers, Codespaces, or Gitpod

---

## Toil Reduction

### Defining toil (Google SRE)
Toil is work that is: manual, repetitive, automatable, tactical (no lasting value), scales linearly with growth, and has no enduring value.

### Common DevOps toil and solutions

| Toil | Solution |
|------|----------|
| Manual deployments | GitOps (ArgoCD/Flux) |
| Certificate rotation | cert-manager with Let's Encrypt |
| Secret rotation | Vault auto-rotation |
| Node patching | Managed node groups, Bottlerocket, auto-upgrade |
| Capacity planning | Karpenter + HPA/KEDA |
| Incident routing | PagerDuty with intelligent routing |
| Access requests | Backstage self-service + automated RBAC |
| Environment setup | Software Templates + Crossplane |
| Dashboard creation | Grafana dashboard-as-code + templates |
| Runbook execution | Rundeck, PagerDuty Process Automation |

### Toil budget
Google SRE recommends: **max 50% of SRE time on toil**. Track toil hours weekly. If toil exceeds 50%, prioritize automation projects over feature work.

---

## Platform Team Operating Model

### Team structure
- **Platform product manager**: Owns the platform roadmap, prioritizes based on developer feedback
- **Platform engineers** (3-7): Build and maintain the IDP, golden paths, and shared tooling
- **Developer advocates** (1-2): Bridge between platform team and development teams
- **SRE/Reliability** (2-3): Maintain platform reliability, incident response, SLO management

### Platform team responsibilities
1. **Build**: Create and maintain the IDP, templates, pipelines, and shared infrastructure
2. **Support**: Help teams adopt the platform, troubleshoot issues, provide office hours
3. **Measure**: Track adoption, satisfaction, DORA metrics, cost efficiency
4. **Iterate**: Continuously improve based on feedback and usage patterns

### Maturity model
| Level | Description | Indicators |
|-------|-------------|------------|
| **1. Ad hoc** | No platform, teams self-serve everything | High variance in practices, duplicated effort |
| **2. Standardized** | Shared CI/CD, common monitoring | Some templates, basic documentation |
| **3. Self-service** | Backstage portal, automated provisioning | Developers create services without tickets |
| **4. Optimized** | Golden paths, automated compliance | DORA metrics tracked, <1hr lead time |
| **5. AI-augmented** | AI-assisted operations, predictive scaling | Autonomous incident response, intelligent recommendations |

### Success metrics for platform teams
- **Adoption rate**: % of teams using the platform (target: >80%)
- **Developer NPS**: Net Promoter Score from internal surveys (target: >50)
- **Time to production**: Average time from idea to production deployment
- **Toil ratio**: % of time spent on manual, repetitive tasks (target: <30%)
- **Platform reliability**: Uptime of platform services themselves (target: 99.9%)