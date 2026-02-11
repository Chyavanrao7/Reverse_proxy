# Reverse_proxy

A small Java-based reverse proxy / load balancer with simple HTTP backend servers.  
This repo demonstrates a basic round-robin load balancer that forwards TCP/HTTP connections to multiple Java backend servers.

## Project structure
- backend/
  - server.java (class: `HttpBackendServer`) — simple HTTP server that prints request lines and returns a basic HTML response.
  - Dockerfile — builds a minimal Java container image that runs the backend server (uses OpenJDK 17).
- loadbalancer/
  - lb.java (class: `loadbalancer`) — TCP-based load balancer that listens for client connections and forwards data to backends using round-robin.
- LICENSE — MIT License.

## Tools & tech
- Java 17 (OpenJDK)
- Sockets (java.net.Socket / ServerSocket)
- Simple multithreading (Threads / ExecutorService)
- Docker (optional — backend Dockerfile is provided)

## How it works (high level)
- The load balancer listens on port 8888 for incoming client connections.
- It maintains a list of backend servers (e.g. backend1:9001, backend2:9002, backend3:9003).
- For each incoming client, it selects the next backend in round-robin order and opens a TCP connection to that backend.
- Data is forwarded bidirectionally between client and backend (two threads per connection).
- Backend servers are simple HTTP responders that return an HTML page containing their port number.

## Run locally (no Docker)
1. Start multiple backends (each on a different port):
   - Compile:
     - javac backend/server.java
   - Run three backends in separate terminals:
     - java HttpBackendServer 9001
     - java HttpBackendServer 9002
     - java HttpBackendServer 9003
2. Start the load balancer:
   - Compile:
     - javac loadbalancer/lb.java
   - Run:
     - java loadbalancer
   - The load balancer listens on port `8888`.
3. Test:
   - curl http://localhost:8888/
   - Repeat requests and you should see responses coming from different backend ports (round-robin).

## Run with Docker (recommended for hostname resolution)
1. Create a docker network:
   - docker network create reverse_net
2. Build backend image:
   - docker build -t http-backend:latest backend
3. Run backend containers on the network:
   - docker run -d --name backend1 --network reverse_net -e PORT=9001 -p 9001:9001 http-backend:latest
   - docker run -d --name backend2 --network reverse_net -e PORT=9002 -p 9002:9002 http-backend:latest
   - docker run -d --name backend3 --network reverse_net -e PORT=9003 -p 9003:9003 http-backend:latest
4. Options for load balancer:
   - Run the load balancer on the same Docker network (recommended so container hostnames like `backend1` resolve):
     - Create a small image or run an ephemeral container with compiled lb class and attach it to `reverse_net` and expose port 8888.
   - Or run `loadbalancer` binary on a host and ensure it can resolve container hostnames (e.g., run with Docker host network or adjust /etc/hosts).

## Notes & tips
- The load balancer uses a simple round-robin counter and does not check backend health; if a backend is down the connection will fail. Consider adding health checks and retry logic for production use.
- Backends return simple HTML for demonstration. Replace with real application logic as needed.
- If running load balancer and backends in different networks, update the backend hostnames/addresses in `loadbalancer/lb.java`.
- The backend Dockerfile runs `java HttpBackendServer $PORT` — ensure the compiled class or source filename matches that command if you change filenames.

## License
MIT — see LICENSE file.
