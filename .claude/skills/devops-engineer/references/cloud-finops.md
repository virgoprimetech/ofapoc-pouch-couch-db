# Cloud Platforms & FinOps Reference

> Reference for: AWS EKS, GCP GKE, Azure AKS, managed Kubernetes, FinOps, cost optimization, multi-cloud

## Table of Contents
1. [Managed Kubernetes Comparison](#managed-kubernetes-comparison)
2. [AWS EKS](#aws-eks)
3. [GCP GKE](#gcp-gke)
4. [Azure AKS](#azure-aks)
5. [Multi-Cloud Strategy](#multi-cloud-strategy)
6. [FinOps Practices](#finops-practices)
7. [Cost Optimization Patterns](#cost-optimization-patterns)
8. [Well-Architected Considerations](#well-architected-considerations)

---

## Managed Kubernetes Comparison

| Feature | EKS | GKE | AKS |
|---------|-----|-----|-----|
| Latest K8s version | 1.33 | 1.33 | 1.33 |
| Auto-managed compute | EKS Auto Mode | Autopilot | AKS Automatic |
| Default CNI | VPC CNI | Dataplane V2 (Cilium) | Azure CNI Overlay + Cilium |
| Node autoscaler | Karpenter (managed) | GKE Autopilot | Karpenter (managed) |
| GitOps integration | Managed ArgoCD (EKS Capabilities) | Config Sync | Flux extension |
| GPU support | P5/P5e (H100/H200) | A4X (GB200 NVL72) | ND H100 v5 |
| Control plane SLA | 99.95% | 99.95% | 99.95% (with uptime SLA) |
| Pricing model | $0.10/hr + compute | $0.10/hr + compute | Free control plane + compute |

---

## AWS EKS

### EKS Auto Mode (GA 2025)
EKS Auto Mode extends management beyond the control plane to include compute, networking, load balancing, and storage — all running off-cluster on AWS-owned infrastructure.

**What it manages:**
- Compute autoscaling via managed Karpenter
- Pod networking via VPC CNI
- ALB load balancing
- EBS CSI storage provisioning

**Key details:**
- Uses **Bottlerocket OS** with locked-down security (IMDSv2, encrypted EBS, SELinux)
- **12% management premium** over standard EC2 pricing
- Cannot SSH into nodes (by design)
- Supports Spot instances for cost optimization

**When to use Auto Mode vs Standard:**
- Auto Mode: New clusters, teams without deep K8s ops experience, cost-conscious teams wanting managed Karpenter
- Standard: Existing clusters with custom AMIs, GPU workloads needing specific drivers, compliance requiring node access

### EKS Capabilities (GA November 2025)
- **Managed ArgoCD**: First-party GitOps as an EKS add-on
- **AWS Controllers for Kubernetes (ACK)**: Manage AWS resources via Kubernetes CRDs
- Available as EKS add-ons for simplified lifecycle management

### EKS Pod Identity
Replaces IAM Roles for Service Accounts (IRSA) with a simpler model:
- No need to create OIDC providers
- Cross-account access via `targetRoleArn` (new)
- Simpler IAM policy configuration
- Automatic token rotation

```yaml
# Pod Identity Association
apiVersion: eks.amazonaws.com/v1
kind: PodIdentityAssociation
metadata:
  name: s3-access
spec:
  serviceAccountName: myapp-sa
  namespace: myapp
  roleArn: arn:aws:iam::123456789:role/myapp-s3-role
```

### EKS networking
- **VPC CNI**: Each pod gets a real VPC IP (no overlay). Use prefix delegation for higher pod density.
- **Security Groups for Pods**: Assign SGs directly to pods for compliance
- **IPv6 support**: Dual-stack clusters for large-scale deployments
- **AWS Load Balancer Controller**: Manages ALB/NLB via Kubernetes Ingress and Gateway API

---

## GCP GKE

### GKE Autopilot
Google's fully managed Kubernetes where you pay per pod, not per node.

**Key features (2025-2026):**
- Autopilot features available in Standard clusters (GA September 2025)
- **7x faster pod scheduling** with optimized scheduling algorithms
- **Custom Compute Classes**: Define hardware requirements per workload with Spot fallback
- **A4X VMs**: NVIDIA GB200 NVL72 — first GPU VMs on Arm with Grace Blackwell architecture
- **Dataplane V2 (Cilium)**: Default CNI with eBPF-based networking

### Compute Classes
```yaml
apiVersion: cloud.google.com/v1
kind: ComputeClass
metadata:
  name: gpu-workloads
spec:
  priorities:
    - machineFamily: a4x
      spot: true
    - machineFamily: a3-mega
      spot: true
    - machineFamily: a3-mega
      spot: false
```

### GKE vs Autopilot decision
- **Autopilot**: Most workloads, teams wanting minimal ops, cost optimization via per-pod billing
- **Standard**: Custom node images, DaemonSets, privileged containers, specific kernel requirements

---

## Azure AKS

### AKS Automatic (GA August 2025)
Ships with HPA, VPA, KEDA, and Karpenter all enabled by default, plus Azure CNI Overlay with Cilium.

**Key features:**
- **Managed System Node Pools** (Preview November 2025): Core components run on Microsoft-owned infrastructure
- **Pod Readiness SLA**: Financially backed guarantee that pods reach readiness (beyond API server uptime)
- **Azure CNI Overlay + Cilium**: Default networking stack with eBPF
- **KEDA by default**: Event-driven autoscaling out of the box

### AKS Workload Identity
Replaces Pod Identity (deprecated) and AAD Pod Identity:
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: myapp-sa
  annotations:
    azure.workload.identity/client-id: "00000000-0000-0000-0000-000000000000"
---
apiVersion: v1
kind: Pod
metadata:
  labels:
    azure.workload.identity/use: "true"
spec:
  serviceAccountName: myapp-sa
```

---

## Multi-Cloud Strategy

### When multi-cloud makes sense
- **Regulatory requirements**: Data sovereignty laws requiring specific regions
- **Vendor negotiation leverage**: Credible ability to shift workloads
- **Best-of-breed services**: ML on GCP, general compute on AWS, enterprise on Azure
- **Disaster recovery**: Cross-cloud DR for critical workloads

### When to avoid multi-cloud
- Small/medium team without dedicated platform engineering
- Primary motivation is "avoiding vendor lock-in" (the portability cost exceeds the lock-in cost for most organizations)
- No compliance requirement driving it

### Multi-cloud Kubernetes patterns
- **Crossplane**: Single control plane provisioning across clouds
- **Cluster API**: Kubernetes-native cluster lifecycle management
- **Fleet management**: Rancher, Google Anthos, Azure Arc
- **GitOps across clusters**: ArgoCD ApplicationSets with cluster generators

### Portable abstractions
| Layer | Portable | Cloud-specific |
|-------|----------|---------------|
| Compute | Kubernetes (EKS/GKE/AKS) | EC2/GCE/VMs |
| Networking | Gateway API, Cilium | VPC/Cloud NAT/Azure VNet |
| Storage | CSI drivers, Rook-Ceph | EBS/Persistent Disk/Azure Disk |
| DNS | ExternalDNS | Route53/Cloud DNS/Azure DNS |
| Secrets | External Secrets Operator | Secrets Manager/KMS |
| Monitoring | OTel + Prometheus + Grafana | CloudWatch/Cloud Monitoring/Azure Monitor |

---

## FinOps Practices

### FOCUS Specification v1.3 (December 2025)
FinOps Open Cost & Usage Specification. Provides a vendor-neutral format for cloud cost data.

**v1.3 additions:**
- Contract commitment datasets (reserved instances, savings plans)
- Shared cost allocation
- Data recency metadata

57% of FinOps practitioners plan to adopt FOCUS within 12 months. All three clouds support FOCUS data exports.

### FinOps lifecycle
```
Inform → Optimize → Operate
  │          │          │
  ▼          ▼          ▼
Visibility  Actions   Governance
Tagging     Right-size  Policies
Allocation  Spot/RI    Budgets
Showback    Cleanup    Alerts
```

### Tagging strategy (non-negotiable)
Every resource must have:
```
team: platform-engineering
environment: production
service: api-gateway
cost-center: CC-1234
managed-by: terraform
```

Enforce via:
- Terraform: `default_tags` in provider block
- AWS: Service Control Policies (SCPs) requiring tags
- GCP: Organization policies
- Azure: Azure Policy with deny effect

### Cost visibility tools
- **OpenCost** (CNCF Incubating): Kubernetes-native cost monitoring. Promless mode. MCP server for AI agents.
- **Kubecost**: Commercial extension of OpenCost with savings recommendations
- **Cloud provider tools**: AWS Cost Explorer, GCP Cloud Billing, Azure Cost Management
- **FOCUS exports**: Normalize data across clouds for unified reporting

---

## Cost Optimization Patterns

### Compute optimization

**1. Karpenter for just-in-time provisioning**
```yaml
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: default
spec:
  template:
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: [amd64, arm64]
        - key: karpenter.sh/capacity-type
          operator: In
          values: [spot, on-demand]
        - key: karpenter.k8s.aws/instance-family
          operator: In
          values: [m7g, m7i, c7g, c7i, r7g, r7i]
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default
  limits:
    cpu: "1000"
    memory: 1000Gi
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 30s
```

Key Karpenter features:
- Provisions pods in ~55 seconds (vs 3-4 minutes for Cluster Autoscaler)
- Cost-aware Spot/On-Demand mixing with fallback
- Bin-packing optimization to maximize node utilization
- Consolidation: automatically removes underutilized nodes
- Drift detection: replaces nodes when AMI or config changes

**2. Spot instances strategy**
- Use Spot for stateless, fault-tolerant workloads
- Diversify across 10+ instance types to reduce interruption rate
- Set `terminationGracePeriodSeconds` to handle 2-minute Spot warnings
- Never run stateful workloads (databases, persistent queues) on Spot

**3. Right-sizing with VPA**
- Deploy VPA in recommendation mode
- Review recommendations weekly
- Set requests to P95 recommendation, limits to 2x

**4. Reserved capacity**
- Compute Savings Plans (AWS): 1 or 3 year, up to 66% savings
- Committed Use Discounts (GCP): 1 or 3 year
- Azure Reserved Instances: 1 or 3 year
- Target: cover baseline capacity (steady-state minimum) with reservations, burst with Spot/On-Demand

### Storage optimization
- Delete unused PersistentVolumes and snapshots
- Use `gp3` (AWS) instead of `gp2` — same performance, 20% cheaper
- Lifecycle policies for S3/GCS — transition to infrequent access after 30 days, glacier after 90
- Right-size EBS volumes based on actual throughput/IOPS usage

### Network optimization
- Topology-aware routing (K8s 1.33 GA) to reduce cross-zone traffic
- Use VPC endpoints (AWS) / Private Service Connect (GCP) to avoid NAT Gateway charges
- Compress data in transit between services
- Cache aggressively at CDN level (CloudFront/Cloud CDN/Azure CDN)

### FinOps metrics
- **Unit cost**: Cost per request, cost per user, cost per transaction
- **Waste ratio**: Idle resources / total provisioned
- **Coverage ratio**: Reserved/committed capacity / total capacity
- **Efficiency score**: Actual utilization / requested resources

---

## Well-Architected Considerations

### Reliability pillar essentials
- Multi-AZ deployment for all production workloads
- Pod Disruption Budgets for all Deployments and StatefulSets
- Topology spread constraints for even distribution
- Health checks (readiness + liveness + startup probes)
- Circuit breakers for external dependencies
- Disaster recovery runbook tested quarterly

### Security pillar essentials
- Encryption at rest and in transit (always)
- Workload identity (Pod Identity / Workload Identity) — no long-lived credentials
- Network segmentation (VPC, subnets, security groups, Network Policies)
- Audit logging enabled (CloudTrail / Cloud Audit Logs / Azure Activity Log)
- Vulnerability scanning in CI/CD and runtime

### Cost optimization pillar essentials
- Tagging strategy enforced via policy
- Right-sizing reviews monthly
- Reserved capacity covering baseline
- Spot for fault-tolerant workloads
- Automated cleanup of idle resources
- FinOps team or champion in each engineering team

### Performance pillar essentials
- Autoscaling at pod (HPA/KEDA) and node (Karpenter) levels
- CDN for static content and API caching
- Database read replicas for read-heavy workloads
- Connection pooling (PgBouncer for PostgreSQL)
- Regional deployment for latency-sensitive workloads