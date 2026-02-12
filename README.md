# Java Reverse Proxy / Load Balancer

A simple TCP reverse proxy and load balancer written in Java. The proxy accepts client connections on a single port and distributes traffic across multiple backend HTTP servers using **round-robin** scheduling.

## Architecture

```
                    ┌─────────────────┐
                    │   Load Balancer │
                    │   (port 8888)   │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
   ┌───────────┐      ┌───────────┐      ┌───────────┐
   │ Backend 1 │      │ Backend 2 │      │ Backend 3 │
   │  (9001)   │      │  (9002)   │      │  (9003)   │
   └───────────┘      └───────────┘      └───────────┘
```

- **Load balancer**: Listens for TCP connections and forwards each new connection to the next backend in round-robin order. Data is bidirectionally streamed between client and backend.
- **Backend servers**: Simple HTTP servers that respond with a greeting including their port number (useful for verifying which backend handled the request).

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose

## Quick Start

From the project root:

```bash
docker compose up --build
```

- **Proxy** is exposed on **http://localhost:8080** (mapped to internal port 8888).
- **Backends** run on ports 9001, 9002, and 9003 (internal to the Docker network).

### Test the load balancer

```bash
# Send multiple requests; you should see responses from different backends (round-robin)
curl http://localhost:8080/
curl http://localhost:8080/
curl http://localhost:8080/
```

Each response will show which backend port handled the request (e.g. "Hello from Backend Server on port 9001").

## Project Structure

```
java-reverse-proxy/
├── loadbalancer/
│   ├── Dockerfile
│   └── loadbalancer.java    # TCP reverse proxy with round-robin
├── backend/
│   ├── Dockerfile
│   └── HttpBackendServer.java   # Simple HTTP server
├── docker-compose.yml
└── README.md
```

## How It Works

### Load balancer (`loadbalancer.java`)

- Listens on port **8888**.
- Maintains a list of backend addresses: `backend1:9001`, `backend2:9002`, `backend3:9003`.
- For each accepted client connection:
  1. Selects the next backend using a synchronized round-robin counter.
  2. Opens a TCP connection to that backend.
  3. Spawns two threads to forward data: client → backend and backend → client.
- Uses a cached thread pool for handling connections concurrently.

### Backend (`HttpBackendServer.java`)

- Listens on a configurable port (default **9001**; set via `PORT` in Docker).
- Reads the HTTP request (headers) and responds with a minimal HTML page indicating the server port.
- One thread per connection.

## Configuration

| Component   | Port (host) | Port (container) | Notes                    |
|------------|-------------|------------------|--------------------------|
| Proxy      | 8080        | 8888             | Change in `docker-compose.yml` if needed |
| Backend 1  | —           | 9001             | Set via `PORT` env       |
| Backend 2  | —           | 9002             | Set via `PORT` env       |
| Backend 3  | —           | 9003             | Set via `PORT` env       |

Backend hostnames (`backend1`, `backend2`, `backend3`) are Docker Compose service names and must match the list in `loadbalancer.java` if you add or rename services.

## Requirements

- **Java**: Both components are built and run with **OpenJDK 17** in the provided Dockerfiles.

## License

See repository for license information.
