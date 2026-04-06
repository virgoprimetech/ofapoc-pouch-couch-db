# Infrastructure as Code & GitOps Reference

> Reference for: Terraform, OpenTofu, Pulumi, Crossplane, ArgoCD v3, Flux v2.8, GitOps patterns

## Table of Contents
1. [IaC Landscape Overview](#iac-landscape-overview)
2. [Terraform 1.14+](#terraform)
3. [OpenTofu 1.11](#opentofu)
4. [Pulumi](#pulumi)
5. [Crossplane 2.x](#crossplane)
6. [IaC Selection Framework](#iac-selection-framework)
7. [ArgoCD v3](#argocd-v3)
8. [Flux v2.8](#flux-v28)
9. [GitOps Patterns](#gitops-patterns)
10. [Progressive Delivery](#progressive-delivery)

---

## IaC Landscape Overview

The IaC world consolidated around four tools after CDKTF was deprecated (December 10, 2025). IBM acquired HashiCorp for $6.4B (closed February 27, 2025) but has not changed the BSL 1.1 license.

**The four-tool ecosystem:**
- **Terraform** (BSL 1.1): Largest ecosystem, enterprise support via IBM/HashiCorp
- **OpenTofu** (MPL 2.0): Open-source fork with unique features (state encryption, `enabled` meta-argument)
- **Pulumi** (Apache 2.0): General-purpose languages, AI-assisted, direct TF module execution
- **Crossplane** (Apache 2.0): Kubernetes-native continuous reconciliation, CNCF Graduated

---

## Terraform

Current stable: **1.14.x**. Alpha: 1.15.

### Key features (1.9–1.14)
- **`terraform test`**: Native testing framework with `run` blocks, `mock_provider`, and `skip_cleanup`. Test `.tftest.hcl` files alongside modules.
- **Ephemeral resources** (experimental): Resources that exist only during plan/apply, never stored in state. Ideal for temporary credentials and short-lived tokens.
- **Provider-defined functions**: Providers can expose custom functions callable in HCL expressions.
- **Stacks** (public beta): Orchestrate multiple root modules in a single deployment action. Handles cross-module dependencies and ordering.
- **Continuous validation**: HCP Terraform continuously evaluates `check` blocks and `postcondition` assertions against live infrastructure.
- **Drift detection**: HCP Terraform detects configuration drift and triggers alerts or auto-remediation.

### Terraform project structure (recommended)
```
infrastructure/
├── modules/                   # Reusable modules
│   ├── vpc/
│   ├── eks-cluster/
│   └── rds/
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── backend.tf
│   │   └── terraform.tfvars
│   ├── staging/
│   └── production/
├── tests/
│   └── vpc.tftest.hcl
└── .terraform-version
```

### Terraform test example
```hcl
# tests/vpc.tftest.hcl
run "creates_vpc_with_correct_cidr" {
  command = plan

  variables {
    vpc_cidr = "10.0.0.0/16"
    environment = "test"
  }

  assert {
    condition     = aws_vpc.main.cidr_block == "10.0.0.0/16"
    error_message = "VPC CIDR block does not match"
  }

  assert {
    condition     = aws_vpc.main.enable_dns_hostnames == true
    error_message = "DNS hostnames must be enabled"
  }
}
```

### State management best practices
- **Always use remote state** with locking: S3 + DynamoDB (Terraform) or S3 native locking (OpenTofu 1.10+)
- **One state file per environment per component**: Never mix dev and prod in the same state
- **Enable state encryption**: S3 server-side encryption + KMS key at minimum
- **State file access control**: IAM policies restricting who can read/write state
- **Regular state backups**: Enable S3 versioning on the state bucket

---

## OpenTofu

Current stable: **1.11.x**. CNCF Sandbox project since April 2025. Approaching 10M GitHub downloads.

### Unique features (not in Terraform)
- **Client-side state encryption**: Encrypt state files before they reach any backend. Works with any backend (S3, GCS, Azure Blob, local). Uses AES-GCM or PBKDF2.
- **`enabled` meta-argument**: Clean conditional resource creation. Replaces the `count = var.x ? 1 : 0` pattern.
- **Provider `for_each`**: Eliminates repetitive multi-region/multi-account provider blocks.
- **Native S3 state locking without DynamoDB**: Uses S3 conditional writes (requires S3 with strong consistency).
- **OCI registry distribution**: Distribute modules via OCI registries alongside container images.

### State encryption configuration
```hcl
# backend.tf
terraform {
  encryption {
    method "aes_gcm" "default" {
      keys = key_provider.aws_kms.main
    }
    key_provider "aws_kms" "main" {
      kms_key_id = "arn:aws:kms:us-east-1:123456789:key/abc-123"
      region     = "us-east-1"
    }
    state {
      method = method.aes_gcm.default
    }
    plan {
      method = method.aes_gcm.default
    }
  }
}
```

### Enabled meta-argument
```hcl
resource "aws_cloudwatch_log_group" "app" {
  enabled           = var.enable_logging  # Clean boolean toggle
  name              = "/app/${var.environment}"
  retention_in_days = 30
}
```

### Migration from Terraform to OpenTofu
1. Replace `terraform` binary with `tofu` (drop-in compatible for most workflows)
2. Update CI/CD scripts: `terraform plan` → `tofu plan`
3. State files are compatible — no migration needed
4. Provider registry: OpenTofu uses its own registry but can access HashiCorp's
5. Test thoroughly: Some edge cases in provider behavior may differ

---

## Pulumi

### Key capabilities (2025-2026)
- **General-purpose languages**: TypeScript, Python, Go, Java (1.0 GA), C#, YAML
- **Pulumi Neo**: AI-powered infrastructure agent that generates, deploys, and troubleshoots IaC
- **Direct Terraform module execution**: Run TF modules inside Pulumi programs without conversion
- **Journaling GA**: Up to 20x faster state operations via incremental state updates
- **350K+ community members**, 170+ cloud providers

### When to choose Pulumi
- Team is developer-heavy and prefers real programming languages over HCL
- Need complex logic (loops, conditionals, API calls) during provisioning
- Want to share types between application code and infrastructure code
- AI-assisted infrastructure management is a priority

### Pulumi example (TypeScript)
```typescript
import * as pulumi from "@pulumi/pulumi";
import * as aws from "@pulumi/aws";
import * as eks from "@pulumi/eks";

const cluster = new eks.Cluster("production", {
    vpcId: vpc.id,
    subnetIds: privateSubnets.map(s => s.id),
    instanceType: "m7g.xlarge",
    desiredCapacity: 3,
    minSize: 2,
    maxSize: 10,
    version: "1.35",
    nodeRootVolumeEncrypted: true,
    enabledClusterLogTypes: ["api", "audit", "authenticator"],
});

export const kubeconfig = cluster.kubeconfig;
```

---

## Crossplane

**Crossplane 2.0** (August 2025, CNCF Graduated November 2025). 3,000+ contributors. Users include Nike, NASA, SAP.

### Architectural shift in v2
- **Namespace-scoped by default**: Both XRs and MRs are namespace-scoped. Claims are deprecated.
- **Composition Functions**: Python, Go, KCL functions replace native patch/transform. More expressive, testable.
- **Application management**: Extends beyond infrastructure to manage application lifecycle alongside cloud resources.

### When to choose Crossplane
- Platform team building self-service infrastructure for developers
- GitOps-first workflow where desired state lives in Kubernetes
- Need continuous reconciliation (detect and fix drift automatically)
- Multi-cloud abstraction with a single API

### Crossplane XRD + Composition example
```yaml
# XRD: Define the API
apiVersion: apiextensions.crossplane.io/v1
kind: CompositeResourceDefinition
metadata:
  name: databases.platform.example.com
spec:
  group: platform.example.com
  names:
    kind: Database
    plural: databases
  versions:
    - name: v1alpha1
      served: true
      referenceable: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              properties:
                engine:
                  type: string
                  enum: [postgres, mysql]
                size:
                  type: string
                  enum: [small, medium, large]
---
# Composition: Implement the API
apiVersion: apiextensions.crossplane.io/v1
kind: Composition
metadata:
  name: database-aws
spec:
  compositeTypeRef:
    apiVersion: platform.example.com/v1alpha1
    kind: Database
  mode: Pipeline
  pipeline:
    - step: create-rds
      functionRef:
        name: function-go-templating
      input:
        apiVersion: gotemplating.fn.crossplane.io/v1beta1
        kind: GoTemplate
        source: Inline
        inline:
          template: |
            apiVersion: rds.aws.upbound.io/v1beta2
            kind: Instance
            metadata:
              annotations:
                gotemplating.fn.crossplane.io/composition-resource-name: rds-instance
            spec:
              forProvider:
                engine: {{ .observed.composite.resource.spec.engine }}
                instanceClass: {{ if eq .observed.composite.resource.spec.size "small" }}db.t4g.micro{{ else }}db.r7g.large{{ end }}
```

---

## IaC Selection Framework

| Criterion | Terraform | OpenTofu | Pulumi | Crossplane |
|-----------|-----------|----------|--------|------------|
| License | BSL 1.1 | MPL 2.0 (open) | Apache 2.0 | Apache 2.0 |
| Language | HCL | HCL | TS/Py/Go/Java/C# | YAML + Functions |
| State management | Remote backends | Remote + encrypted | Pulumi Cloud/self-hosted | Kubernetes etcd |
| Drift detection | HCP Terraform only | Manual | Pulumi Cloud | Continuous (native) |
| Learning curve | Moderate | Low (TF compatible) | Low for developers | High (K8s concepts) |
| Ecosystem | Largest (5000+ providers) | TF-compatible | 170+ providers | 200+ providers |
| Best for | Established teams | Open-source commitment | Developer-centric teams | K8s platform teams |

**Decision rule**: Use Terraform/OpenTofu for traditional infrastructure provisioning. Use Pulumi when teams prefer real programming languages. Use Crossplane when building a self-service platform on Kubernetes.

---

## ArgoCD v3

ArgoCD v3.0 (May 2025), v3.1 (August 2025), v3.2 (January 2026). All images signed with cosign, SLSA Level 3 provenance.

### v3 key features
- **Refined RBAC**: Granular project-level permissions with AppProject scoping
- **Native OCI support** (v3.1): Store manifests in OCI registries alongside container images
- **Source Hydrator** (v3.1): Links dry commits (Kustomize/Helm) with upstream code commits for full traceability
- **CLI plugins** (v3.1): Extend ArgoCD functionality via pluggable CLI commands
- **Improved resource tracking**: Better handling of server-side apply field ownership
- **Enhanced UI**: New application topology view with real-time health status

### ArgoCD Application example
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: myapp-production
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: production
  source:
    repoURL: oci://registry.example.com/manifests/myapp
    targetRevision: 1.5.0
    helm:
      valueFiles:
        - values-production.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: myapp
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - ServerSideApply=true
      - CreateNamespace=true
    retry:
      limit: 5
      backoff:
        duration: 5s
        maxDuration: 3m
        factor: 2
```

### App-of-Apps pattern
```yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: cluster-addons
  namespace: argocd
spec:
  generators:
    - git:
        repoURL: https://github.com/org/cluster-addons.git
        revision: HEAD
        directories:
          - path: addons/*
  template:
    metadata:
      name: '{{path.basename}}'
    spec:
      project: cluster-addons
      source:
        repoURL: https://github.com/org/cluster-addons.git
        targetRevision: HEAD
        path: '{{path}}'
      destination:
        server: https://kubernetes.default.svc
        namespace: '{{path.basename}}'
```

---

## Flux v2.8

Flux v2.8 (February 2026). CNCF Graduated project.

### v2.8 key features
- **Helm v4 support**: Server-side apply and kstatus-based health checking
- **Flux Web UI**: Via Flux Operator, provides dashboard for multi-cluster visibility
- **CEL-based health check expressions**: Custom health evaluation logic
- **ArtifactGenerator API**: Monorepo decomposition with optimized reconciliation

### v2.7 features (September 2025)
- **Image update automation GA**: Automatically update manifests when new container images are pushed
- **Improved OCI support**: Cosign verification for Helm charts and OCI artifacts
- **Cross-namespace references**: Controlled sharing of sources across namespaces

### Flux GitOps example
```yaml
# GitRepository source
apiVersion: source.toolkit.fluxcd.io/v1
kind: GitRepository
metadata:
  name: app-manifests
  namespace: flux-system
spec:
  interval: 1m
  url: https://github.com/org/app-manifests
  ref:
    branch: main
  verify:
    provider: cosign
    secretRef:
      name: cosign-public-key
---
# Kustomization
apiVersion: kustomize.toolkit.fluxcd.io/v1
kind: Kustomization
metadata:
  name: myapp-production
  namespace: flux-system
spec:
  interval: 5m
  sourceRef:
    kind: GitRepository
    name: app-manifests
  path: ./clusters/production/myapp
  prune: true
  healthChecks:
    - apiVersion: apps/v1
      kind: Deployment
      name: myapp
      namespace: myapp
  timeout: 3m
```

---

## GitOps Patterns

### Repository strategies

**Monorepo**: All environments in one repo with path-based separation.
- Pro: Single source of truth, atomic cross-environment changes
- Con: Complex RBAC, blast radius of misconfiguration
- Best for: Small teams, tightly coupled environments

**Polyrepo**: Separate repos per environment or per application.
- Pro: Strong isolation, simple permissions
- Con: Promotion requires cross-repo PRs, harder to audit
- Best for: Large organizations, strict compliance requirements

**Hybrid (recommended)**: App manifests in app repo, cluster config in ops repo, promotion via image tags or OCI artifacts.

### Environment promotion
```
feature-branch → dev (auto-deploy on merge)
                → staging (auto-deploy after dev health check passes)
                → production (manual approval gate + canary)
```

Implement via:
- **ArgoCD**: ApplicationSet with pull request generator (dev), image updater (staging), manual sync (prod)
- **Flux**: Image automation controller updates staging; PR-based promotion to prod

### Multi-cluster GitOps
- **ArgoCD**: ApplicationSets with cluster generator. Hub cluster manages spoke clusters.
- **Flux**: Flux Operator deploys FluxInstance per cluster. OCI artifacts as canonical source.
- **Key principle**: One Git commit = one desired state across all target clusters

---

## Progressive Delivery

### Argo Rollouts
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: myapp
spec:
  replicas: 10
  strategy:
    canary:
      canaryService: myapp-canary
      stableService: myapp-stable
      trafficRouting:
        plugins:
          argoproj-labs/gatewayAPI:
            httpRoute: myapp-route
            namespace: myapp
      steps:
        - setWeight: 10
        - pause: {duration: 5m}
        - analysis:
            templates:
              - templateName: success-rate
            args:
              - name: service-name
                value: myapp-canary
        - setWeight: 50
        - pause: {duration: 10m}
        - setWeight: 100
      analysis:
        successfulRunHistoryLimit: 3
        unsuccessfulRunHistoryLimit: 3
---
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
spec:
  metrics:
    - name: success-rate
      interval: 60s
      successCondition: result[0] >= 0.99
      provider:
        prometheus:
          address: http://prometheus:9090
          query: |
            sum(rate(http_requests_total{service="{{args.service-name}}",code=~"2.."}[5m]))
            /
            sum(rate(http_requests_total{service="{{args.service-name}}"}[5m]))
```

### Flagger (Flux ecosystem)
Flagger works with Gateway API, Istio, Linkerd, and Cilium for automated canary promotion based on SLO metrics. Use Flagger when Flux is your GitOps engine; use Argo Rollouts when ArgoCD is your engine.