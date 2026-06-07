# Java Reverse Proxy & Load Balancer

A production-grade reverse proxy and load balancer built from scratch in Java — no frameworks, no external HTTP libraries. Implements core distributed systems patterns including circuit breaking, rate limiting, and active health checking.

---

## Features

| Feature | Details |
|---|---|
| **Load Balancing** | Round Robin, Weighted Round Robin, Least Connections, IP Hash |
| **Health Checking** | Active background polling with configurable failure/success thresholds |
| **Circuit Breaker** | Three-state FSM (Closed → Open → Half-Open) per backend |
| **Rate Limiting** | Per-IP token bucket with burst support and automatic eviction |
| **HTTP Forwarding** | Java 11 `HttpClient` with connection pooling and hop-by-hop header stripping |
| **Observability** | Structured request logging + live `/admin/stats` JSON endpoint |
| **Graceful Shutdown** | SIGTERM/SIGINT drain with configurable timeout |
| **Config-Driven** | All settings in `config.properties` — no recompile needed |
| **Containerized** | Docker + Docker Compose for one-command deployment |

---

## Architecture

```
                         ┌─────────────────────────────────────┐
                         │         Reverse Proxy :8080          │
                         │                                      │
  Client Request  ──────►│  1. Rate Limiter (token bucket/IP)   │
                         │  2. Load Balancer (algorithm)        │
                         │  3. Circuit Breaker (per backend)    │
                         │  4. HTTP Forwarder (connection pool) │
                         │                                      │
                         └────────────┬────────────────────────┘
                                      │
                   ┌──────────────────┼──────────────────┐
                   ▼                  ▼                   ▼
             Backend A :8081    Backend B :8082    Backend C :8083

                         ┌─────────────────────────────────────┐
                         │  Background Threads                  │
                         │  • Health Checker (polls /health)    │
                         │  • Rate Limiter eviction (5 min)     │
                         └─────────────────────────────────────┘

                         ┌─────────────────────────────────────┐
                         │  Admin Endpoint :9090                │
                         │  GET /admin/stats → live JSON stats  │
                         └─────────────────────────────────────┘
```

---

## Request Flow

Every incoming request passes through these steps in order:

```
Client
  │
  ▼
Rate Limiter ──── over quota? ──► 429 Too Many Requests
  │
  ▼
Load Balancer ─── no alive backends? ──► 503 Service Unavailable
  │
  ▼
Circuit Breaker ── circuit open? ──► 503 Service Unavailable (fast fail)
  │
  ▼
HTTP Forwarder ──────────────────────► Backend Server
  │                                          │
  │◄─────────────── response ───────────────┘
  │
  ▼
Record outcome (circuit breaker success/failure)
  │
  ▼
Log structured request line
  │
  ▼
Client receives response
```


## Load Balancing Algorithms- Round Robin, Weighted Round Robin, Least Connections, IP Hash 


## Project Structure

```
java/
├── config.properties                         ← All settings (no hardcoding)
├── Dockerfile
│
└── src/main/java/proxy/
    ├── ReverseProxy.java                     ← Entry point — wires everything
    ├── Backend.java                          ← Upstream server model
    ├── ProxyLogger.java                      ← Structured logging
    │
    ├── config/Config.java                    ← Typed config loader
    │
    ├── balancer/
    │   ├── LoadBalancer.java                 ← Strategy interface
    │   ├── BalancerFactory.java              ← Algorithm factory
    │   ├── RoundRobinBalancer.java
    │   ├── WeightedRoundRobinBalancer.java
    │   ├── LeastConnectionsBalancer.java
    │   └── IPHashBalancer.java
    │
    ├── health/HealthChecker.java             ← Background health polling
    │
    ├── circuitbreaker/
    │   ├── CircuitState.java                 ← CLOSED / OPEN / HALF_OPEN
    │   └── CircuitBreaker.java               ← FSM implementation
    │
    ├── ratelimit/
    │   ├── TokenBucket.java                  ← Single-IP bucket
    │   └── RateLimiter.java                  ← Per-IP bucket manager
    │
    └── handler/
        ├── ProxyHandler.java                 ← Request orchestrator
        ├── HttpForwarder.java                ← Forwards via Java 11 HttpClient
        └── AdminHandler.java                 ← /admin/stats endpoint
```

---

## Getting Started

### Prerequisites

- Java 11+ (Java 21 recommended for virtual threads)
- Docker & Docker Compose (for containerized setup)

### Run with Docker Compose

```bash
# Clone the repo and enter the java directory
cd reverse-proxy/java

# Start proxy + 3 backends
docker compose up --build
```

### Run Locally

```bash
# Compile
mkdir -p out
javac -d out $(find src -name "*.java")

# Run
java -cp out proxy.ReverseProxy

# Custom config path
java -cp out proxy.ReverseProxy /path/to/config.properties
```

---

## Configuration

All settings live in `config.properties`. Change any value without recompiling.

```properties
# Proxy server
proxy.port=8080
proxy.admin.port=9090

# Backends (comma-separated)
backends=http://localhost:8081,http://localhost:8082,http://localhost:8083
backend.weights=3,1,2

# Algorithm: roundrobin | weighted | leastconn | iphash
load.balancer.algorithm=roundrobin

# Health checking
health.check.enabled=true
health.check.interval.seconds=10
health.check.timeout.seconds=2
health.check.failure.threshold=3    # mark dead after N consecutive failures
health.check.success.threshold=2    # revive after M consecutive successes

# Circuit breaker
circuit.breaker.enabled=true
circuit.breaker.failure.threshold=5
circuit.breaker.timeout.seconds=30

# Rate limiting (token bucket per client IP)
rate.limit.enabled=true
rate.limit.requests.per.second=10
rate.limit.burst=20
```

---

## Testing

**Send a proxied request:**
```bash
curl http://localhost:8080/
```

**Watch round-robin in action:**
```bash
for i in {1..6}; do curl -s http://localhost:8080/ | grep backend; done
# Output cycles through backend1, backend2, backend3
```

**Check live backend stats:**
```bash
curl http://localhost:9090/admin/stats
```

**Sample admin response:**
```json
{
  "algorithm": "round-robin",
  "backends": [
    {
      "url": "http://localhost:8081",
      "alive": true,
      "active_connections": 2,
      "total_requests": 1024,
      "total_errors": 3,
      "circuit_breaker": "CLOSED"
    },
    {
      "url": "http://localhost:8082",
      "alive": false,
      "active_connections": 0,
      "total_requests": 987,
      "total_errors": 47,
      "circuit_breaker": "OPEN"
    }
  ]
}
```

**Test rate limiting:**
```bash
# Send 25 rapid requests — first 20 succeed (burst), rest return 429
for i in {1..25}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
done
```

**Simulate a backend failure (circuit breaker):**
```bash
# Stop one backend
docker compose stop backend2

# Within 3 health check cycles, backend2 is marked dead
# After 5 request failures, its circuit opens
curl http://localhost:9090/admin/stats  # circuit_breaker: "OPEN" for backend2
```

---

## How the Circuit Breaker Works

```
Normal operation
    CLOSED ──(5 consecutive failures)──► OPEN
      ▲                                    │
      │                                    │ (30 second timeout)
      │                                    ▼
    CLOSED ◄──(probe succeeds)────── HALF_OPEN
    OPEN   ◄──(probe fails)──────── HALF_OPEN
```

- **CLOSED** — requests flow normally, failure count tracked
- **OPEN** — all requests rejected instantly (503), no backend contact
- **HALF_OPEN** — one probe request allowed through to test recovery

This prevents a struggling backend from being hammered while it recovers,
and eliminates 30-second timeout waits when a backend is completely down.

---

## How the Token Bucket Rate Limiter Works

Each client IP gets its own independent bucket.

```
capacity = 20 tokens (burst)
refillRate = 10 tokens/second

t=0s:  bucket=20 | 20 rapid requests → bucket=0  ✓ all allowed
t=0s:  bucket=0  | request 21        → 429        ✗ rejected
t=1s:  bucket=10 | 10 requests       → bucket=0  ✓ all allowed
```

Tokens refill lazily at the moment of each request — no background refill thread.
Stale IP buckets are automatically evicted after 10 minutes of inactivity.

---


## Tech Stack

- **Language:** Java 11+ (Java 21 for virtual threads)
- **HTTP Server:** `com.sun.net.httpserver.HttpServer` (built-in JDK)
- **HTTP Client:** `java.net.http.HttpClient` (Java 11 built-in)
- **Concurrency:** `AtomicInteger`, `AtomicBoolean`, `ConcurrentHashMap`, `ScheduledExecutorService`
- **Containerization:** Docker, Docker Compose
- **External dependencies:** None
