# Observability & SRE Reference

> Reference for: OpenTelemetry, Prometheus 3, Grafana stack, eBPF observability, SLO frameworks, incident management, chaos engineering

## Table of Contents
1. [OpenTelemetry](#opentelemetry)
2. [Prometheus 3.x](#prometheus)
3. [Grafana Stack](#grafana-stack)
4. [eBPF Observability](#ebpf-observability)
5. [SLO/SLI Frameworks](#slo-sli-frameworks)
6. [Incident Management](#incident-management)
7. [Chaos Engineering](#chaos-engineering)
8. [Capacity Planning](#capacity-planning)

---

## OpenTelemetry

Second-highest-velocity CNCF project. ~50% enterprise adoption. Traces, metrics, and logs are all stable. Profiling entered public alpha in early 2026 as the fourth signal.

### Signal maturity (April 2026)
| Signal | Status | Key feature |
|--------|--------|-------------|
| Traces | Stable | Distributed tracing, W3C context propagation |
| Metrics | Stable | OTLP export, Prometheus compatibility |
| Logs | Stable | Structured logging, trace correlation |
| Profiling | Alpha | CPU/memory profiling, trace_id/span_id linkage |

### OTel Collector architecture
```
┌──────────────┐     ┌─────────────────────────────────────┐     ┌──────────────┐
│ Applications │────▶│          OTel Collector              │────▶│   Backends   │
│  (SDK/Auto)  │     │  Receivers → Processors → Exporters │     │ Prom/Loki/   │
└──────────────┘     └─────────────────────────────────────┘     │ Tempo/Mimir  │
                                                                  └──────────────┘
```

### Collector configuration example
```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
  prometheus:
    config:
      scrape_configs:
        - job_name: 'kubernetes-pods'
          kubernetes_sd_configs:
            - role: pod

processors:
  batch:
    timeout: 5s
    send_batch_size: 1024
  memory_limiter:
    check_interval: 1s
    limit_mib: 512
    spike_limit_mib: 128
  k8sattributes:
    extract:
      metadata:
        - k8s.namespace.name
        - k8s.deployment.name
        - k8s.pod.name

exporters:
  otlphttp/tempo:
    endpoint: http://tempo:4318
  prometheusremotewrite:
    endpoint: http://mimir:9009/api/v1/push
  loki:
    endpoint: http://loki:3100/loki/api/v1/push

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, k8sattributes, batch]
      exporters: [otlphttp/tempo]
    metrics:
      receivers: [otlp, prometheus]
      processors: [memory_limiter, batch]
      exporters: [prometheusremotewrite]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, k8sattributes, batch]
      exporters: [loki]
```

### Grafana Alloy (replaces Grafana Agent)
Grafana Agent reached EOL November 2025. **Grafana Alloy** is the replacement — a distribution of the OTel Collector with native support for Prometheus, Loki, Tempo, and Pyroscope pipelines. Use Alloy for Grafana-centric stacks; use the vanilla OTel Collector for vendor-neutral deployments.

### Auto-instrumentation
- **eBPF-based (OBI/Beyla)**: Zero-code-change. Donated from Grafana to OTel. Linux kernel 5.8+. <1% overhead. Supports Go, Python, Java, .NET, Rust.
- **SDK-based**: Add OTel SDK to application code. More control over spans and attributes. Required for custom business metrics.
- **Java Agent**: `opentelemetry-javaagent.jar` auto-instruments 100+ libraries with zero code changes.

---

## Prometheus

### Prometheus 3.8 (November 2025)
Major version 3 was the biggest Prometheus release in years.

**Key features:**
- **Native histograms (stable)**: Exponential bucket boundaries that auto-size. Collapse entire distributions into a single time series. Dramatically reduces cardinality.
- **OTLP ingestion**: Native endpoint at `/api/v1/otlp/v1/metrics`. No adapter needed.
- **UTF-8 metric names**: `http.server.request.duration` works natively — no more dots-to-underscores translation.
- **Remote write 2.0**: Better compression, lower latency, metadata propagation.

### Native histogram recording
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  native_histograms:
    enabled: true
    bucket_factor: 1.1  # 10% bucket growth
```

### Long-term storage solutions

**Grafana Mimir 3.0** (recommended for Grafana stacks):
- Decoupled read/write paths via Apache Kafka
- Streaming query engine (92% less memory, 38% faster)
- Scales to 1 billion active series per tenant
- Clear successor to Cortex

**Thanos** (sidecar pattern):
- Prometheus sidecar uploads TSDB blocks to object storage
- Global querying across multiple Prometheus instances
- Better for teams wanting minimal architecture changes

**VictoriaMetrics**:
- Drop-in Prometheus replacement with better compression
- Single-binary deployment option
- Good for cost-sensitive environments

### Prometheus best practices
- **Recording rules** for frequently queried expressions to reduce query latency
- **Alerting rules** with `for` duration to avoid flapping
- **Federation** only for aggregated metrics — never federate raw series
- **Relabeling** to drop high-cardinality labels at scrape time
- **External labels** for identifying Prometheus instances in multi-cluster setups

---

## Grafana Stack

### Grafana 12 (GrafanaCON 2025)
- **Grafana Assistant (GA in Cloud)**: Natural-language querying and incident investigation
- **Scenes framework**: Programmatic dashboard construction for embedding
- **Improved alerting**: Unified alerting engine with silence management

### Loki 3.5
- **Native OTLP log ingestion**: Direct OTLP endpoint, no Promtail/Alloy required for OTel-instrumented apps
- **Structured metadata**: Attach key-value pairs to log lines without indexing
- **Bloom filters**: Accelerate queries on unindexed fields

### Tempo 2.9
- **MCP server support**: AI assistants can query traces directly
- **TraceQL metrics sampling**: Derive metrics from traces without full processing
- **vParquet4**: Improved storage format with better query performance

### Mimir 3.0
- See Prometheus section above for long-term storage details

### Grafana dashboard design principles
- **USE method** for infrastructure: Utilization, Saturation, Errors
- **RED method** for services: Rate, Errors, Duration
- **Four Golden Signals** (Google SRE): Latency, Traffic, Errors, Saturation
- **Dashboard hierarchy**: Overview → Service → Component → Debug
- **Template variables**: Environment, cluster, namespace, service as dashboard variables
- **Alert annotations**: Include runbook link, dashboard link, and recent changes in every alert

---

## eBPF Observability

### OpenTelemetry eBPF Instrumentation (OBI/Beyla)
Grafana donated Beyla to OpenTelemetry in May 2025. OBI provides zero-code-change observability by intercepting system calls and network events at the kernel level.

**Capabilities:**
- HTTP/gRPC/SQL trace generation without code changes
- Application-level metrics (request rate, latency, error rate)
- Network flow monitoring
- Process-level resource usage

**Requirements**: Linux kernel 5.8+, BPF Type Format (BTF) enabled, `CAP_SYS_ADMIN` or `CAP_BPF` + `CAP_PERFMON`

### Hubble (Cilium observability)
- Network flow visibility at the kernel level
- L3/L4/L7 protocol observability
- DNS query monitoring
- Network policy verdict logging
- Integrates with Grafana for visualization

---

## SLO/SLI Frameworks

### Defining SLOs
**SLI** (Service Level Indicator): A quantitative measure of service behavior.
**SLO** (Service Level Objective): A target value for an SLI.
**Error Budget**: 1 - SLO. The allowed amount of unreliability.

### SLI types
- **Availability**: Proportion of successful requests (HTTP 2xx/3xx out of total)
- **Latency**: Proportion of requests faster than a threshold (p99 < 200ms)
- **Throughput**: Request rate meeting capacity expectations
- **Correctness**: Proportion of requests returning correct results

### Multi-window multi-burn-rate alerting (Google SRE Workbook)
```
Page alert:   14.4x burn rate over 1-hour window AND 6x over 6-hour window
Ticket alert: 1x burn rate over 3-day window
```

This approach:
- Catches fast burns (outages) quickly via page
- Catches slow burns (degradation) via ticket
- Minimizes false positives compared to simple threshold alerts

### SLO-as-code with Sloth
```yaml
# sloth.yaml
version: "prometheus/v1"
service: "api-gateway"
slos:
  - name: "requests-availability"
    objective: 99.9
    description: "99.9% of requests should be successful"
    sli:
      events:
        error_query: sum(rate(http_requests_total{job="api-gateway",code=~"5.."}[{{.window}}]))
        total_query: sum(rate(http_requests_total{job="api-gateway"}[{{.window}}]))
    alerting:
      name: APIGatewayAvailability
      labels:
        team: platform
      annotations:
        runbook: https://wiki.example.com/runbooks/api-gateway-availability
      page_alert:
        labels:
          severity: critical
      ticket_alert:
        labels:
          severity: warning
```

Sloth generates Prometheus recording rules and alerting rules from this spec.

### OpenSLO
Vendor-neutral SLO specification format. Supported by Nobl9, Dynatrace, and other platforms.

---

## Incident Management

### Incident lifecycle
```
Detection → Triage → Mitigation → Resolution → Post-mortem → Action items
```

### Tool landscape (2026)
- **PagerDuty**: SRE Agent (GA October 2025) for autonomous detection, triage, and remediation. 91% alert noise reduction.
- **OpsGenie**: EOL April 5, 2027. Migrate to Jira Service Management or alternatives.
- **incident.io**: AI Scribe for real-time call transcription, 80% automated response.
- **Rootly**: End-to-end incident management at roughly half PagerDuty's cost.
- **FireHydrant**: Structured post-incident analysis.

### Post-mortem template
```markdown
# Incident Post-Mortem: [Title]

## Summary
- **Duration**: [start time] to [end time] ([total duration])
- **Severity**: SEV-[1/2/3/4]
- **Impact**: [Who was affected and how]
- **Detection**: [How was it detected — alert, customer report, monitoring]

## Timeline
| Time (UTC) | Event |
|------------|-------|
| HH:MM | First alert fired |
| HH:MM | On-call acknowledged |
| HH:MM | Root cause identified |
| HH:MM | Mitigation applied |
| HH:MM | Service fully recovered |

## Root Cause
[Technical explanation of what went wrong]

## Contributing Factors
- [Factor 1: e.g., missing monitoring for X]
- [Factor 2: e.g., no rate limiting on Y]

## What Went Well
- [Good 1: e.g., alerting fired within 2 minutes]
- [Good 2: e.g., runbook was accurate]

## Action Items
| Priority | Action | Owner | Due Date |
|----------|--------|-------|----------|
| P1 | [Action] | [Name] | [Date] |
| P2 | [Action] | [Name] | [Date] |

## Lessons Learned
[What should we change about our systems, processes, or culture?]
```

### Alerting best practices
- Every alert must be **actionable** — if you can't act on it, delete it
- Every alert must have a **runbook** linked in annotations
- Alerts should fire on **symptoms** (error rate, latency), not causes (CPU, memory)
- Use **error budgets** to drive alerting thresholds, not arbitrary percentages
- Route alerts to the **right team** — not a catch-all channel
- **Silence** planned maintenance windows proactively

---

## Chaos Engineering

### CNCF chaos tools
- **LitmusChaos 3.x** (CNCF Incubating): MCP server for AI-powered chaos experiments via natural language
- **Chaos Mesh** (CNCF Incubating): Kubernetes-native, fine-grained fault injection

### Maturity levels
1. **Level 1**: Run experiments in staging only
2. **Level 2**: Integrate chaos into CI/CD (break builds on resilience failures)
3. **Level 3**: Automated production chaos with safety controls (abort conditions, blast radius limits)
4. **Level 4**: AI-powered experiment selection based on system topology and past incidents

### Common experiments
- **Pod failure**: Kill random pods to test restart behavior and PDB compliance
- **Network latency**: Inject 100-500ms delay between services to test timeout handling
- **DNS failure**: Block DNS resolution to test fallback behavior
- **CPU/Memory stress**: Saturate resources to test autoscaling and degradation handling
- **Zone failure**: Simulate entire AZ outage to test multi-zone resilience

### Steady-state hypothesis pattern
```yaml
# LitmusChaos experiment
apiVersion: litmuschaos.io/v1alpha1
kind: ChaosExperiment
metadata:
  name: pod-delete
spec:
  definition:
    scope: Namespaced
    permissions: [...]
    env:
      - name: TOTAL_CHAOS_DURATION
        value: '30'
      - name: CHAOS_INTERVAL
        value: '10'
      - name: PODS_AFFECTED_PERC
        value: '50'
    # Abort if error rate exceeds 5%
    probe:
      - name: check-error-rate
        type: promProbe
        mode: Continuous
        runProperties:
          probeTimeout: 5
          interval: 5
        promProbe/inputs:
          endpoint: http://prometheus:9090
          query: sum(rate(http_requests_total{code=~"5.."}[1m])) / sum(rate(http_requests_total[1m]))
          comparator:
            type: float
            criteria: "<="
            value: "0.05"
```

---

## Capacity Planning

### Autoscaling stack (recommended 2026)
- **HPA**: Horizontal pod autoscaling based on CPU/memory/custom metrics
- **KEDA**: Event-driven autoscaling with scale-to-zero (queue depth, cron schedules, custom metrics)
- **VPA**: Vertical pod autoscaling in **recommendation mode only** — use recommendations to right-size, don't auto-apply in production
- **Karpenter**: Just-in-time node provisioning with Spot/On-Demand mixing

### Right-sizing methodology
1. Deploy VPA in recommendation mode alongside all workloads
2. Collect 2 weeks of recommendations
3. Set requests to VPA's P95 recommendation, limits to 2x requests
4. Monitor actual usage vs requests/limits with `container_cpu_usage_seconds_total` and `container_memory_working_set_bytes`
5. Review quarterly

### Capacity planning metrics
- **Utilization**: actual / requested (target: 60-80%)
- **Saturation**: throttling events, OOM kills, pending pods
- **Headroom**: available capacity for burst (target: 20-40% during peak)
- **Cost per request**: total infrastructure cost / total request count (the ultimate FinOps metric)