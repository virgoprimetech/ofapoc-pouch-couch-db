# Security & Networking Reference

> Reference for: DevSecOps, runtime security, secrets management, service mesh, Cilium, eBPF networking, Zero Trust

## Table of Contents
1. [Zero Trust Architecture](#zero-trust-architecture)
2. [Runtime Security](#runtime-security)
3. [Secrets Management](#secrets-management)
4. [Policy Engines](#policy-engines)
5. [Workload Identity](#workload-identity)
6. [Service Mesh](#service-mesh)
7. [Cilium & eBPF Networking](#cilium-ebpf-networking)
8. [Network Policies](#network-policies)
9. [Compliance Frameworks](#compliance-frameworks)
10. [Container Security Hardening](#container-security-hardening)

---

## Zero Trust Architecture

### Core principles
1. **Never trust, always verify**: Every request is authenticated and authorized, regardless of network location
2. **Least privilege**: Grant minimum permissions required for each workload and user
3. **Assume breach**: Design systems to limit blast radius when (not if) compromise occurs
4. **Verify explicitly**: Use all available signals (identity, device, location, behavior) for authorization

### Implementation layers in Kubernetes
```
Layer 1: Identity  — SPIFFE/SPIRE, workload identity, mTLS
Layer 2: Network   — Network Policies, service mesh, eBPF
Layer 3: Admission — Kyverno, OPA Gatekeeper, Pod Security Admission
Layer 4: Runtime   — Falco (detection), Tetragon (enforcement)
Layer 5: Data      — Encryption at rest (etcd, volumes), in transit (mTLS)
Layer 6: Audit     — API server audit logs, cloud audit trails
```

---

## Runtime Security

### Falco 0.42 (CNCF Graduated, October 2025)
**Detection engine**: Monitors syscalls and Kubernetes audit events for anomalous behavior.

**Key features:**
- Defaults to modern eBPF probe (replaces kernel module)
- Capture files for forensic analysis
- Available as **AWS EKS add-on**
- 100+ built-in detection rules

**Falco rule example:**
```yaml
- rule: Terminal shell in container
  desc: Detect a shell spawned in a container
  condition: >
    spawned_process and container and
    shell_procs and proc.tty != 0 and
    container_entrypoint
  output: >
    Shell spawned in container
    (user=%user.name container=%container.name
     shell=%proc.name parent=%proc.pname
     image=%container.image.repository)
  priority: WARNING
  tags: [container, shell, mitre_execution]
```

### Falco Talon (automated response)
No-code response engine triggered by Falco detections:
- **13 automated actions**: Terminate pods, apply NetworkPolicies, invoke Lambda, label resources, send notifications
- Millisecond response time
- Configurable per-rule response chains

```yaml
# Falco Talon rule
- action: Terminate Pod
  actionner: kubernetes:terminate
  parameters:
    grace_period_seconds: 0
  rules:
    - Terminal shell in container
    - Crypto miner detected
```

### Tetragon (eBPF kernel enforcement)
Created by Cilium/Isovalent. Operates at a fundamentally different level than Falco.

**Key difference: synchronous enforcement in the Linux kernel**
- Blocks syscalls **before they complete** — eliminates TOCTOU (Time of Check to Time of Use) attack window
- Less than 1% performance overhead
- Kubernetes-native TracingPolicy CRDs
- Monitors process execution, file access, network connections, and privilege escalation

**TracingPolicy example:**
```yaml
apiVersion: cilium.io/v1alpha1
kind: TracingPolicy
metadata:
  name: block-sensitive-file-access
spec:
  kprobes:
    - call: "fd_install"
      syscall: false
      args:
        - index: 0
          type: int
        - index: 1
          type: "file"
      selectors:
        - matchArgs:
            - index: 1
              operator: "Prefix"
              values:
                - "/etc/shadow"
                - "/etc/passwd"
          matchActions:
            - action: Sigkill  # Kill the process immediately
```

### Recommended production stack
```
Detection (broad)  → Falco (syscall monitoring, audit events, 100+ rules)
Enforcement (precise) → Tetragon (kernel-level blocking, file/network/process)
```

Falco casts a wide net for detection; Tetragon provides surgical enforcement for critical policies.

---

## Secrets Management

### HashiCorp Vault 1.21 (October 2025)
- **Post-quantum cryptography** (experimental): SLH-DSA signatures in Transit engine
- **SPIFFE support**: Machine identity via SPIFFE/SPIRE integration
- **Vault Secrets Operator (VSO)**: Mounts secrets directly into pods at runtime via CSI driver
- **Auto-rotation**: Automatic credential rotation for databases, cloud providers, PKI

### Vault in Kubernetes patterns

**Pattern 1: Vault Secrets Operator (recommended)**
```yaml
apiVersion: secrets.hashicorp.com/v1beta1
kind: VaultStaticSecret
metadata:
  name: app-secrets
spec:
  type: kv-v2
  mount: secret
  path: myapp/production
  destination:
    name: app-secrets
    create: true
  refreshAfter: 30s
```

**Pattern 2: External Secrets Operator**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: app-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: app-secrets
  data:
    - secretKey: database-url
      remoteRef:
        key: secret/myapp/production
        property: database_url
```

### External Secrets Operator v0.14
Supports 15+ backends: Vault, AWS Secrets Manager, GCP Secret Manager, Azure Key Vault, 1Password, Doppler, etc.

### Secrets anti-patterns
- ❌ Secrets in environment variables (visible in `/proc`, logged by many frameworks)
- ❌ Secrets in ConfigMaps (stored unencrypted in etcd)
- ❌ Secrets in Git (even encrypted, rotation is painful)
- ❌ Secrets in Terraform state (state files are often not encrypted)
- ❌ Shared secrets across environments (compromised dev secret compromises prod)
- ✅ Vault/KMS with automatic rotation and least-privilege access

---

## Policy Engines

### Kyverno 1.17 (February 2026)
**CEL policy engine GA** — Kubernetes-native policies without learning Rego.

**Capabilities:** Validation, mutation, generation, cleanup, image verification

```yaml
# Require all images from trusted registries
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: restrict-image-registries
spec:
  validationFailureAction: Enforce
  rules:
    - name: validate-registries
      match:
        any:
          - resources:
              kinds: [Pod]
      validate:
        cel:
          expressions:
            - expression: >-
                object.spec.containers.all(c,
                  c.image.startsWith('ghcr.io/myorg/') ||
                  c.image.startsWith('registry.example.com/')
                )
              message: "Images must come from approved registries"
```

```yaml
# Auto-generate NetworkPolicy for every new namespace
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: default-deny-ingress
spec:
  rules:
    - name: generate-default-deny
      match:
        any:
          - resources:
              kinds: [Namespace]
      generate:
        apiVersion: networking.k8s.io/v1
        kind: NetworkPolicy
        name: default-deny-ingress
        namespace: "{{request.object.metadata.name}}"
        data:
          spec:
            podSelector: {}
            policyTypes:
              - Ingress
```

### OPA Gatekeeper
Preferred for complex cross-resource compliance logic (SOC2, multi-attribute authorization). Uses Rego language.

**When to use each:**
- **Kyverno**: Simple-to-moderate policies, teams that don't want to learn Rego, image verification, resource generation
- **OPA Gatekeeper**: Complex compliance rules, cross-resource validation, existing Rego investment
- **Both together**: Many teams run both — Kyverno for common patterns, OPA for complex compliance

---

## Workload Identity

All three clouds now offer native workload identity — eliminating long-lived credentials in pods.

| Cloud | Solution | Key benefit |
|-------|----------|-------------|
| AWS | EKS Pod Identity | Cross-account via `targetRoleArn`, simpler than IRSA |
| GCP | Workload Identity Federation | Direct IAM binding to K8s service accounts |
| Azure | Workload Identity | Replaces deprecated Pod Identity / AAD Pod Identity |

### SPIFFE/SPIRE (CNCF Graduated)
**SPIFFE** (Secure Production Identity Framework for Everyone): Standard for workload identity.
**SPIRE** (SPIFFE Runtime Environment): Production implementation.

**Why SPIRE beyond cloud identity:**
- Cross-cloud identity (workload in AWS talks to workload in GCP)
- Stronger attestation than default RBAC (hardware, process, filesystem attestation)
- Short-lived, automatically rotated X.509 SVIDs (SPIFFE Verifiable Identity Documents)
- Integrates with Vault, Istio, Envoy, and all major service meshes

---

## Service Mesh

### Architecture comparison (2026)

**Istio Ambient Mesh** (GA since Istio 1.24):
- **ztunnel** (Rust DaemonSet): L4 mTLS, authorization, telemetry — always on
- **Waypoint proxies** (optional): L7 routing, JWT validation, rate limiting — only where needed
- Up to **70% CPU/memory savings** over sidecar model
- Multi-cluster ambient mode (beta at KubeCon EU 2026)
- Gateway API Inference Extension (beta) for AI model routing

**Linkerd 2.19** (October 2025):
- **Post-quantum cryptography**: ML-KEM-768 key exchange — first among meshes
- Native sidecar support (beta) for K8s 1.33+
- Ultra-light Rust microproxies
- Simplest operational model
- FIPS 140-3 validated crypto in enterprise builds

**Cilium (mesh-less mesh)**:
- mTLS via SPIFFE without separate control plane
- L7 policy, load balancing, and observability through Hubble
- 60%+ CNI market share
- For many workloads, Cilium alone replaces a traditional service mesh

### Decision framework
```
Q: Do you need L7 traffic management (header routing, JWT, rate limiting)?
  YES → Istio Ambient or Linkerd
  NO  → Cilium provides mTLS and L4 policy without a mesh

Q: Is operational simplicity the top priority?
  YES → Linkerd (simplest) or Cilium (no separate mesh)
  NO  → Istio Ambient (most features)

Q: Do you need post-quantum crypto?
  YES → Linkerd 2.19

Q: Do you need multi-cluster mesh?
  YES → Istio Ambient (beta) or Linkerd Multi-cluster
```

---

## Cilium & eBPF Networking

### Cilium 1.19 (February 2026, 10th anniversary)
- **Strict encryption modes**: Unencrypted inter-node traffic is dropped
- **ztunnel beta integration**: Experimental Istio ztunnel compatibility
- **60%+ market share** as CNI (75%+ including GKE Dataplane V2 and AKS Azure CNI)

### eBPF advantages over iptables
- **Performance**: O(1) lookup vs O(n) iptables chain traversal
- **Programmability**: Custom packet processing logic at kernel level
- **Observability**: Hubble provides deep network visibility without packet capture
- **Security**: Kernel-level enforcement (Tetragon) with process/file/network awareness
- **Scale**: Handles 100K+ services without performance degradation

### Cilium features for DevOps
- **CiliumNetworkPolicy**: Extended network policies with L7 awareness, DNS-based rules, CIDR policies
- **Hubble**: Network flow visualization, service dependency maps, DNS monitoring
- **Cluster Mesh**: Multi-cluster networking with shared services and global load balancing
- **Bandwidth Manager**: Fair queuing and rate limiting at pod level
- **BGP Control Plane**: Native BGP peering for on-premise/hybrid deployments

---

## Network Policies

### Default deny template (apply to every namespace)
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
```

### Allow specific traffic
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-api-to-database
  namespace: myapp
spec:
  podSelector:
    matchLabels:
      app: database
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-server
      ports:
        - protocol: TCP
          port: 5432
```

### CiliumNetworkPolicy (extended)
```yaml
apiVersion: cilium.io/v2
kind: CiliumNetworkPolicy
metadata:
  name: allow-dns-and-api
spec:
  endpointSelector:
    matchLabels:
      app: frontend
  egress:
    - toEndpoints:
        - matchLabels:
            "k8s:io.kubernetes.pod.namespace": kube-system
            k8s-app: kube-dns
      toPorts:
        - ports:
            - port: "53"
              protocol: UDP
    - toFQDNs:
        - matchName: api.example.com
      toPorts:
        - ports:
            - port: "443"
```

---

## Compliance Frameworks

### Mapping controls to Kubernetes

| Compliance | Key K8s controls |
|-----------|-----------------|
| **SOC 2** | RBAC, audit logging, encryption, network policies, image scanning |
| **ISO 27001** | Access control (RBAC), cryptography (mTLS), operations security (monitoring) |
| **PCI DSS** | Network segmentation, encryption, logging, vulnerability management |
| **HIPAA** | Encryption at rest/transit, audit trails, access controls, BAAs with cloud providers |
| **GDPR** | Data encryption, access logging, right to deletion, data residency |
| **CIS Benchmarks** | kube-bench for automated CIS Kubernetes Benchmark scanning |

### Automated compliance scanning
```bash
# CIS Kubernetes Benchmark
kube-bench run --targets node,master,policies

# IaC compliance
checkov -d . --framework terraform --check CIS_KUBERNETES

# Runtime compliance
falco -r /etc/falco/falco_rules.yaml -r /etc/falco/k8s_audit_rules.yaml
```

---

## Container Security Hardening

### Dockerfile best practices
```dockerfile
# Use specific, minimal base image with digest
FROM cgr.dev/chainguard/python:latest-dev@sha256:abc123... AS builder
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Multi-stage: runtime image is distroless
FROM cgr.dev/chainguard/python:latest@sha256:def456...
WORKDIR /app
COPY --from=builder /app /app
COPY . .

# Non-root user (Chainguard images default to non-root)
USER 65534

ENTRYPOINT ["python", "app.py"]
```

### Image selection priority
1. **Chainguard Images**: Minimal, zero-CVE, SBOM-included, signed
2. **Distroless (Google)**: No shell, no package manager, minimal attack surface
3. **Alpine**: Small (~5MB), but has shell and package manager
4. **Slim variants**: Debian-slim, Ubuntu minimal
5. ❌ **Full OS images**: Avoid `ubuntu:latest`, `centos:latest` — too many unnecessary packages

### Image scanning pipeline
```bash
# Multi-tool scanning (defense in depth after Trivy incident)
trivy image --severity CRITICAL,HIGH myimage:v1.0
grype myimage:v1.0 --fail-on critical

# Verify image signature
cosign verify --certificate-identity-regexp ".*" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  myimage:v1.0

# Check for SBOM
cosign verify-attestation --type cyclonedx myimage:v1.0
```