# Kubernetes & Containers Reference

> Reference for: Kubernetes 1.33–1.35, containerd 2.x, Helm 4, Gateway API, Pod Security, operators

## Table of Contents
1. [Kubernetes Release Landscape](#kubernetes-release-landscape)
2. [Kubernetes 1.33 — Octarine](#kubernetes-133)
3. [Kubernetes 1.34 — Of Wind & Will](#kubernetes-134)
4. [Kubernetes 1.35 — Timbernestes](#kubernetes-135)
5. [Container Runtimes](#container-runtimes)
6. [Helm 4](#helm-4)
7. [Gateway API](#gateway-api)
8. [Pod Security](#pod-security)
9. [Kubernetes Operators](#kubernetes-operators)
10. [Cluster Upgrade Strategy](#cluster-upgrade-strategy)

---

## Kubernetes Release Landscape

As of April 2026, three versions are actively maintained: **1.33, 1.34, 1.35**. Kubernetes 1.36 is in development. Each version receives approximately 14 months of patch support. Managed Kubernetes services (EKS, GKE, AKS) typically lag upstream by 1-2 months for GA releases.

**Version support matrix:**
- 1.33: EOL ~June 2026
- 1.34: EOL ~October 2026
- 1.35: EOL ~February 2027

---

## Kubernetes 1.33

**Codename**: Octarine (April 2025)

### Graduated to GA
- **Native Sidecar Containers (KEP-753)**: The most anticipated feature in years. `initContainers` with `restartPolicy: Always` run alongside the main container with proper lifecycle orchestration. Sidecars start before and terminate after the main container. Critical for logging agents, service mesh proxies, and vault-agent injectors.
- **Topology-Aware Routing**: Traffic prefers endpoints in the same zone, reducing cross-zone data transfer costs.
- **EndpointSlice API**: Fully replaces the legacy Endpoints API (now deprecated). EndpointSlices scale to thousands of endpoints per Service.

### Key changes
- **Endpoints API deprecated**: Migrate all controllers and monitoring to EndpointSlice.
- **In-tree cloud provider removal continues**: AWS, Azure, GCP cloud-controller-managers are now external-only.

### Native sidecar example
```yaml
apiVersion: v1
kind: Pod
spec:
  initContainers:
    - name: log-shipper
      image: fluent/fluent-bit:3.2
      restartPolicy: Always  # This makes it a sidecar
      resources:
        requests:
          cpu: 50m
          memory: 64Mi
        limits:
          cpu: 200m
          memory: 128Mi
  containers:
    - name: app
      image: myapp:1.0.0@sha256:abc123...
```

**Lifecycle guarantees**: Sidecar starts before main container, stays running during main container restarts, and terminates after main container exits. Job completions are calculated based on main containers only — sidecars don't block Job completion.

---

## Kubernetes 1.34

**Codename**: Of Wind & Will (August 2025)

### Graduated to GA
- **Dynamic Resource Allocation (DRA)**: Production-ready scheduling for GPUs, TPUs, FPGAs, and other hardware accelerators as first-class Kubernetes resources. Uses ResourceClaim objects instead of extended resources, enabling structured parameters and device-specific scheduling constraints.
- **Distributed Tracing (kubelet + API server)**: Native OpenTelemetry spans for API server requests and kubelet operations. Cluster-level observability without additional tooling.

### DRA resource claim example
```yaml
apiVersion: resource.k8s.io/v1
kind: ResourceClaim
metadata:
  name: gpu-claim
spec:
  devices:
    requests:
      - name: gpu
        deviceClassName: nvidia-gpu
        count: 2
        selectors:
          - cel:
              expression: "device.attributes['memory'] >= '16Gi'"
---
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: training
      image: ml-training:v1.0
      resources:
        claims:
          - name: gpu-claim
  resourceClaims:
    - name: gpu-claim
      resourceClaimName: gpu-claim
```

---

## Kubernetes 1.35

**Codename**: Timbernestes / The World Tree Release (December 2025)

### Graduated to GA
- **In-Place Pod Resize (KEP-1287)**: Modify CPU and memory on running pods without restart. The kubelet adjusts cgroup limits dynamically. Requires **cgroups v2** — this is the forcing function for the cgroups v1 removal.
- **StatefulSet maxUnavailable**: Enables parallel rolling updates, reducing StatefulSet update times by ~60%.

### Critical breaking changes
- **cgroups v1 support removed entirely**: All nodes MUST run cgroups v2. This affects older OS versions (Amazon Linux 2, Ubuntu 18.04, CentOS 7). Verify with: `stat -fc %T /sys/fs/cgroup/` — must return `cgroup2fs`.
- **IPVS mode deprecated in kube-proxy**: Removal planned for 1.36. Migrate to iptables (default) or eBPF-based alternatives (Cilium).
- **Legacy ServiceAccount token secrets**: Auto-generation fully removed. Use TokenRequestAPI (bound service account tokens) exclusively.

### Alpha features to watch
- **Gang Scheduling**: All-or-nothing scheduling for AI/ML workloads requiring multiple coordinated pods.
- **Configurable HPA tolerance**: Tune the default 10% tolerance for scaling decisions.

### In-place resize example
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: app
      image: myapp:1.0
      resources:
        requests:
          cpu: 500m
          memory: 256Mi
        limits:
          cpu: "1"
          memory: 512Mi
      resizePolicy:
        - resourceName: cpu
          restartPolicy: NotRequired  # Resize without restart
        - resourceName: memory
          restartPolicy: RestartContainer  # Memory resize requires restart
```

**Resize via kubectl**: `kubectl patch pod myapp --subresource resize -p '{"spec":{"containers":[{"name":"app","resources":{"requests":{"cpu":"750m"}}}]}}'`

---

## Container Runtimes

### containerd 2.x (mandatory)
containerd 1.x reached EOL. The 2.x line is required for all Kubernetes clusters.

- **containerd 2.1** (May 2025): OCI image volumes, parallel HTTP range downloads, improved Sandbox API
- **containerd 2.3** (April 2026): First Long Term Stable (LTS) release, aligned with Kubernetes cadence

Key operational notes:
- Default runtime is `runc` with cgroups v2 driver
- For GPU workloads, use NVIDIA Container Toolkit 1.17+ with CDI (Container Device Interface)
- Image pull performance: configure `max_concurrent_downloads = 8` and enable stargz/nydus lazy pulling for large images
- CRI-O remains a viable alternative, especially on Red Hat OpenShift

### Docker in 2026
Docker is a local development tool only. It was removed as a Kubernetes CRI in 1.24. For CI builds, prefer:
- **BuildKit** (standalone): `buildctl build --frontend dockerfile.v0`
- **kaniko**: Builds inside containers without Docker daemon (CI/CD standard)
- **buildah**: OCI-compliant, daemonless, rootless builds

---

## Helm 4

Released November 2025, marking Helm's 10th anniversary. Helm 3 security patches end November 2026.

### Key changes from Helm 3
- **Server-Side Apply (SSA) by default**: Eliminates three-way merge conflicts when multiple tools manage the same resources. Conflicts are detected at field-level ownership.
- **WebAssembly plugin system**: Sandboxed, SOC2/ISO27001-compliant. Replaces the previous Go-based plugin model.
- **OCI registries as primary distribution**: `helm push/pull` to OCI registries by default. Digest-based installs for supply chain integrity.
- **Chart metadata improvements**: New `Chart.lock` format with content hashes.

### Backward compatibility
Well-formed Helm 3 charts deploy without modification under Helm 4. However:
- Charts relying on client-side three-way merge may see different conflict resolution behavior
- Legacy `helm/charts` repository references must migrate to OCI
- Custom plugins need WASM migration (Go plugins still work via compatibility shim, deprecated in Helm 4.1)

### Helm 4 best practices
```bash
# Always use OCI for chart distribution
helm push mychart-1.0.0.tgz oci://registry.example.com/charts

# Pin chart versions in helmfile.yaml
repositories:
  - name: bitnami
    url: oci://registry-1.docker.io/bitnamicharts

# Use JSON schema for values validation
helm install myrelease mychart --values values.yaml --verify
```

### Helmfile and helm-diff
Use `helmfile` for declarative multi-release management. `helm-diff` plugin provides `terraform plan`-like previews:
```bash
helmfile diff  # Show what would change
helmfile apply # Apply only the diff
```

---

## Gateway API

Gateway API v1.4 (October 2025) is the production standard for traffic management, replacing Ingress for all new projects.

### Why Gateway API over Ingress
- **Role-based ownership**: Infrastructure providers manage GatewayClass, cluster operators manage Gateway, application developers manage HTTPRoute
- **Expressive routing**: Header-based, query parameter, method-based routing; traffic splitting, mirroring, URL rewrites
- **Service mesh integration**: GAMMA initiative brings east-west traffic management via the same API (Standard Channel as of v1.4)
- **Multi-implementation**: 7+ conformant implementations (Istio, Cilium, Envoy Gateway, NGINX Gateway Fabric, all cloud providers)

### Gateway API v1.4 features
- **BackendTLSPolicy GA**: Configure TLS verification for backend connections
- **Mesh resource (experimental)**: Mesh-wide settings like default policies
- **Retry budgets**: Per-route retry configuration

### Example: HTTPRoute with canary
```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: myapp
spec:
  parentRefs:
    - name: production-gateway
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /api
      backendRefs:
        - name: myapp-stable
          port: 8080
          weight: 90
        - name: myapp-canary
          port: 8080
          weight: 10
```

---

## Pod Security

### Pod Security Admission (PSA)
Three built-in profiles enforced via namespace labels:
- **Privileged**: Unrestricted (only for system namespaces like `kube-system`)
- **Baseline**: Prevents known privilege escalations (blocks hostNetwork, hostPID, privileged containers)
- **Restricted**: Hardened (non-root, read-only root filesystem, drop ALL capabilities, seccomp RuntimeDefault)

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: production
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
```

### Production security context template
```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 65534
  runAsGroup: 65534
  fsGroup: 65534
  seccompProfile:
    type: RuntimeDefault
containers:
  - securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
      capabilities:
        drop: ["ALL"]
```

### CEL-based ValidatingAdmissionPolicy
Kubernetes 1.30+ supports CEL expressions for custom policies without webhooks:
```yaml
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingAdmissionPolicy
metadata:
  name: require-team-label
spec:
  matchConstraints:
    resourceRules:
      - apiGroups: ["apps"]
        apiVersions: ["v1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["deployments"]
  validations:
    - expression: "has(object.metadata.labels) && 'team' in object.metadata.labels"
      message: "All Deployments must have a 'team' label"
```

---

## Kubernetes Operators

### When to build an operator
Build a custom operator when:
- Your application has complex lifecycle operations (backup, restore, scaling, schema migration)
- You need to encode operational knowledge that would otherwise live in runbooks
- The application requires coordinated multi-component orchestration

Do NOT build an operator when:
- A Helm chart with lifecycle hooks suffices
- The application is stateless and standard Deployment/HPA covers it
- You can't commit to maintaining it long-term

### Operator frameworks (2026)
- **kubebuilder**: The standard for Go-based operators. Uses controller-runtime.
- **Operator SDK**: Red Hat's framework, supports Go, Ansible, and Helm-based operators.
- **Metacontroller**: Lightweight, write controllers as webhooks in any language.
- **KUTTL**: Testing framework for operators with declarative test cases.

### Operator best practices
- Implement all five maturity levels: Basic Install → Seamless Upgrades → Full Lifecycle → Deep Insights → Auto Pilot
- Use finalizers for cleanup, but always set a timeout
- Status subresource for condition reporting (type, status, reason, message, lastTransitionTime)
- Leader election for HA operator deployments
- Rate-limit reconciliation to avoid control plane overload

---

## Cluster Upgrade Strategy

### Pre-upgrade checklist
1. **Read release notes**: Identify deprecated APIs, removed features, and behavior changes
2. **API compatibility scan**: `kubectl get --raw /metrics | grep apiserver_requested_deprecated_apis`
3. **Run pluto**: `pluto detect-all-in-cluster` to find deprecated API versions
4. **Verify cgroups v2** (for 1.35+): `stat -fc %T /sys/fs/cgroup/` must return `cgroup2fs`
5. **PodDisruptionBudgets**: Ensure all critical workloads have PDBs before draining nodes
6. **etcd backup**: `etcdctl snapshot save backup.db` before any upgrade
7. **Test in staging**: Full upgrade cycle with workload validation

### Upgrade order
1. Control plane (API server, controller-manager, scheduler, etcd)
2. Add-ons (CoreDNS, kube-proxy or Cilium, CSI drivers)
3. Worker nodes (rolling, one node pool at a time, respect PDBs)

### Managed Kubernetes upgrade cadence
- **EKS**: Auto-upgrades control plane to latest patch. Minor version upgrades are manual.
- **GKE**: Release channels (Rapid/Regular/Stable) with auto-upgrade windows. Surge upgrades for zero-downtime.
- **AKS**: Planned maintenance windows. Long Term Support (LTS) versions available for extended support.

### Version skew policy
- kubelet: up to 3 minor versions behind API server (e.g., API server 1.35, kubelet 1.32)
- kubectl: ±1 minor version from API server
- etcd: Follow Kubernetes release notes for supported versions