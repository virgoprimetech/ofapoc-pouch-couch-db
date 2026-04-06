# CI/CD & Supply Chain Security Reference

> Reference for: GitHub Actions, GitLab CI, Tekton, Dagger, pipeline design, SLSA, Sigstore, cosign, SBOM

## Table of Contents
1. [CI/CD Platform Landscape](#cicd-platform-landscape)
2. [GitHub Actions](#github-actions)
3. [GitLab CI/CD 18](#gitlab-cicd)
4. [Tekton](#tekton)
5. [Dagger](#dagger)
6. [Pipeline Design Principles](#pipeline-design-principles)
7. [Supply Chain Security](#supply-chain-security)
8. [SLSA Framework](#slsa-framework)
9. [Sigstore & Cosign](#sigstore-cosign)
10. [SBOM Generation](#sbom-generation)

---

## CI/CD Platform Landscape

GitHub Actions dominates with 11.5B minutes processed in 2025 (35% YoY). GitLab CI holds strong in self-hosted enterprise. Tekton reached v1.0 GA for Kubernetes-native pipelines. Dagger is the insurgent treating CI/CD as software.

**Selection criteria:**
- **GitHub Actions**: GitHub-native projects, open-source, broad ecosystem
- **GitLab CI**: Self-hosted enterprises, integrated DevSecOps platform
- **Tekton**: Kubernetes-native, vendor-neutral, composable pipelines
- **Dagger**: CI-vendor-agnostic, local-first, complex build logic
- **Jenkins**: Legacy — migrate away unless deep plugin dependencies exist

---

## GitHub Actions

### 2025-2026 key features
- **YAML anchors** (December 2025): Reduce duplication across workflow files
- **Expanded reusable workflows**: 10 nested, 50 total per run
- **OIDC token improvements**: Job-level traceability claims
- **Runner Scale Set Client** (February 2026): Standalone Go module for custom autoscaling without Kubernetes
- **Pricing**: Up to 39% reduction on hosted runners (January 2026)

### Production workflow template
```yaml
name: Build, Scan, Sign, Deploy

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read
  packages: write
  id-token: write  # Required for OIDC/cosign

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  lint-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2 SHA-pinned
      - name: Run linters
        run: make lint
      - name: Run tests
        run: make test

  build-and-push:
    needs: lint-and-test
    runs-on: ubuntu-latest
    outputs:
      digest: ${{ steps.build.outputs.digest }}
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683
      - uses: docker/setup-buildx-action@c47758b77c9736f4b2ef4073d4d51994fabfe349  # v3.7.1
      - uses: docker/login-action@9780b0c442fbb1117ed29e0efdff1e18412f7567  # v3.3.0
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push
        id: build
        uses: docker/build-push-action@4f58ea79222b3b9dc2c8bbdd6debcef730109a75  # v6.9.0
        with:
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  scan:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Scan image with Trivy
        uses: aquasecurity/trivy-action@18f2510ee396bbf400402947e0f18c8ea63fd80e  # SHA-pinned
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
          format: sarif
          output: trivy-results.sarif
          severity: CRITICAL,HIGH
          exit-code: 1

  sign-and-attest:
    needs: [build-and-push, scan]
    runs-on: ubuntu-latest
    steps:
      - uses: sigstore/cosign-installer@dc72c7d5c4d10cd6bcb8cf6e3fd625a9e5e537da  # v3.7.0
      - name: Sign image
        run: |
          cosign sign --yes \
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}@${{ needs.build-and-push.outputs.digest }}
      - name: Generate SBOM
        run: |
          syft ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}@${{ needs.build-and-push.outputs.digest }} \
            -o cyclonedx-json > sbom.json
      - name: Attach SBOM
        run: |
          cosign attach sbom --sbom sbom.json \
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}@${{ needs.build-and-push.outputs.digest }}

  deploy:
    needs: sign-and-attest
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Update ArgoCD application
        run: |
          # Update image tag in GitOps repo
          # ArgoCD auto-syncs from the GitOps repo
          echo "Deploy via GitOps - update manifests with new digest"
```

### Critical: SHA-pin all actions
After the Trivy supply chain compromise (March 2026), SHA-pinning is mandatory:
```yaml
# WRONG - vulnerable to tag hijacking
- uses: actions/checkout@v4

# RIGHT - SHA-pinned to specific commit
- uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
```

Use `pin-github-action` or Renovate/Dependabot to keep SHA pins updated.

---

## GitLab CI/CD

### GitLab 18 (May 2025+)
- **Duo AI bundled**: AI features in Premium and Ultimate tiers
- **Granular job token permissions**: Cross-project `CI_JOB_TOKEN` calls fail without explicit allowlists
- **CI/CD Catalog GA**: GitLab's marketplace for reusable pipeline components
- **CI/CD components**: Versioned, reusable pipeline blocks (similar to GitHub reusable workflows)

### GitLab CI production template
```yaml
stages:
  - lint
  - test
  - build
  - scan
  - sign
  - deploy

variables:
  DOCKER_TLS_CERTDIR: "/certs"
  IMAGE: $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA

lint:
  stage: lint
  image: golangci/golangci-lint:v2.1
  script:
    - golangci-lint run ./...

test:
  stage: test
  image: golang:1.24
  script:
    - go test -race -coverprofile=coverage.out ./...
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: coverage.out

build:
  stage: build
  image: docker:27
  services:
    - docker:27-dind
  script:
    - docker build -t $IMAGE .
    - docker push $IMAGE
    - echo "DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' $IMAGE | cut -d@ -f2)" >> build.env
  artifacts:
    reports:
      dotenv: build.env

scan:
  stage: scan
  image: aquasec/trivy:latest
  script:
    - trivy image --exit-code 1 --severity CRITICAL,HIGH $IMAGE
  allow_failure: false

sign:
  stage: sign
  image: bitnami/cosign:latest
  script:
    - cosign sign --yes $CI_REGISTRY_IMAGE@$DIGEST

deploy_production:
  stage: deploy
  environment:
    name: production
  when: manual
  script:
    - # Update GitOps repo with new image digest
```

---

## Tekton

**Tekton Pipelines v1.0 GA** (May 2025). v1.9 LTS (February 2026). Transitioning from CD Foundation to CNCF Incubating.

### Key features
- **StepAction GA**: Reusable, versioned step definitions
- **Exponential backoff retry** (v1.9): Configurable retry with backoff for flaky steps
- **Pipelines-in-Pipelines** (v1.9): Compose complex workflows from smaller pipelines
- **Cluster resolvers**: Replace deprecated ClusterTask for sharing tasks across namespaces

### Tekton pipeline example
```yaml
apiVersion: tekton.dev/v1
kind: Pipeline
metadata:
  name: build-and-deploy
spec:
  params:
    - name: repo-url
      type: string
    - name: image-reference
      type: string
  workspaces:
    - name: shared-workspace
  tasks:
    - name: fetch-source
      taskRef:
        resolver: cluster
        params:
          - name: kind
            value: task
          - name: name
            value: git-clone
      workspaces:
        - name: output
          workspace: shared-workspace
      params:
        - name: url
          value: $(params.repo-url)

    - name: build-image
      runAfter: [fetch-source]
      taskRef:
        resolver: cluster
        params:
          - name: kind
            value: task
          - name: name
            value: kaniko
      workspaces:
        - name: source
          workspace: shared-workspace
      params:
        - name: IMAGE
          value: $(params.image-reference)
```

---

## Dagger

Created by Docker co-founder Solomon Hykes. v0.20+. Treats CI/CD as software, not configuration.

### Key concepts
- **Modules**: Composable functions in 8 language SDKs (Go, Python, TypeScript, etc.)
- **BuildKit-powered**: Content-addressed caching, parallel execution
- **CI-vendor-agnostic**: Same pipeline runs locally and in any CI system
- **Daggerverse**: Marketplace for cross-language module composition
- **OpenTelemetry native**: Built-in tracing for pipeline debugging

### When to choose Dagger
- Complex build logic that outgrows YAML configuration
- Need to test pipelines locally before CI
- Multi-language projects requiring shared build logic
- Want to avoid vendor lock-in to a specific CI platform

---

## Pipeline Design Principles

### The canonical pipeline stages
```
Commit → Lint → Test → Build → Scan → Sign → Publish → Deploy → Verify
```

### Key principles
1. **Build once, deploy everywhere**: Build artifacts in CI, promote the same artifact through environments
2. **Immutable artifacts**: Container images are tagged by digest, never overwritten
3. **Fail fast**: Lint and unit tests before expensive build steps
4. **Parallel where possible**: Independent jobs run concurrently
5. **Hermetic builds**: Builds should not depend on external state; pin all dependencies
6. **Reproducible**: Same commit + same pipeline = same artifact (deterministic builds)

### Pipeline anti-patterns
- Building different artifacts per environment (defeats promotion model)
- Using `latest` tags in pipeline steps (non-reproducible)
- Storing secrets in pipeline configuration (use OIDC or secret managers)
- No timeout on jobs (runaway builds consume resources)
- Skipping security scans to "save time" (never acceptable)

### Deployment strategies
- **Rolling update**: Default Kubernetes strategy. Zero-downtime with surge and maxUnavailable.
- **Blue-Green**: Two complete environments. Instant rollback by switching traffic.
- **Canary**: Gradual traffic shift (1% → 10% → 50% → 100%) with metric-based promotion.
- **Feature flags**: Deploy code to 100% of pods, enable features for subset of users. Separate deployment from release.

---

## Supply Chain Security

### The March 2026 Trivy incident
A threat actor compromised Trivy v0.69.4 and the trivy-action GitHub Action via stolen credentials, injecting malicious code into one of the most trusted security scanners. Lessons:
1. **SHA-pin all CI/CD actions** — tag-based references are insufficient
2. **Verify binaries with cosign** before execution
3. **Multi-tool scanning** — never depend on a single scanner
4. **Monitor for action/tool updates** that lack signed provenance

### Defense-in-depth scanning strategy
```
Layer 1: SAST (static analysis) — Semgrep, CodeQL, SonarQube
Layer 2: SCA (dependency analysis) — Dependabot, Renovate, Snyk
Layer 3: Container scanning — Trivy + Grype (multi-tool)
Layer 4: IaC scanning — Checkov, tfsec (now part of Trivy), KICS
Layer 5: Runtime scanning — Falco, Tetragon
Layer 6: DAST (dynamic analysis) — OWASP ZAP, Nuclei
```

---

## SLSA Framework

**SLSA v1.2** (Supply-chain Levels for Software Artifacts):

### Build Track levels
- **Level 1**: Provenance exists (build process documented)
- **Level 2**: Hosted build platform (builds run on a service, not developer laptops)
- **Level 3**: Hardened builds (isolated, parameterless, hermetic)

### Source Track levels (new in v1.2)
- Tracks version control integrity and authorization

### Achieving SLSA Level 3
- Use GitHub Actions or GitLab CI with hosted runners
- Generate provenance with `slsa-github-generator` or `slsa-verifier`
- ArgoCD and Flux both produce SLSA Level 3 provenance
- Store provenance alongside artifacts in OCI registries

---

## Sigstore & Cosign

### Cosign v3.0
- Standardized bundle format for signatures
- OCI 1.1 referring artifacts for signatures (no more tag-based signature storage)
- Keyless signing via OIDC (GitHub Actions, GitLab CI, Google Cloud)

### Sign and verify workflow
```bash
# Sign (keyless, uses GitHub OIDC)
cosign sign --yes ghcr.io/org/myapp@sha256:abc123...

# Verify
cosign verify \
  --certificate-identity "https://github.com/org/myapp/.github/workflows/build.yml@refs/heads/main" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  ghcr.io/org/myapp@sha256:abc123...

# Verify with policy
cosign verify-attestation \
  --type slsaprovenance \
  --certificate-identity-regexp ".*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  ghcr.io/org/myapp@sha256:abc123...
```

### Rekor v2
Tile-based transparency log. Cheaper to run, simpler to maintain. All cosign signatures are logged in Rekor for public auditability.

---

## SBOM Generation

### Regulatory drivers
- **EU Cyber Resilience Act**: Mandates SBOMs for products sold in the EU
- **US Executive Order 14028**: Federal agencies require SBOMs from software vendors

### Formats
- **CycloneDX v1.6.1**: Preferred for security teams (VEX integration, vulnerability correlation)
- **SPDX v3.0**: Preferred for license compliance

### Generation tools
```bash
# Syft — generates SBOM from container image
syft ghcr.io/org/myapp@sha256:abc123 -o cyclonedx-json > sbom.json

# Trivy — combined vulnerability scan + SBOM
trivy image --format cyclonedx ghcr.io/org/myapp@sha256:abc123 > sbom.json

# cdxgen — language-aware SBOM generation from source
cdxgen -o sbom.json --type golang .
```

### SBOM lifecycle
1. **Generate** at build time (part of CI pipeline)
2. **Attach** to container image via cosign
3. **Store** in OCI registry alongside the image
4. **Scan** SBOMs against vulnerability databases (Grype, `bomctl`)
5. **Report** to compliance systems and customers