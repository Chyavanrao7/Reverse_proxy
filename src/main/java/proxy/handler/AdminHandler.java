package proxy.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import proxy.Backend;
import proxy.balancer.LoadBalancer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AdminHandler implements HttpHandler {

    private final List<Backend> backends;
    private final LoadBalancer balancer;

    public AdminHandler(List<Backend> backends, LoadBalancer balancer) {
        this.backends = backends;
        this.balancer = balancer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"algorithm\": \"").append(balancer.name()).append("\",\n");
        json.append("  \"backends\": [\n");

        for (int i = 0; i < backends.size(); i++) {
            Backend b = backends.get(i);
            json.append("    {\n");
            json.append("      \"url\": \"").append(b.getUrl()).append("\",\n");
            json.append("      \"alive\": ").append(b.isAlive()).append(",\n");
            json.append("      \"active_connections\": ").append(b.getActiveConnections()).append(",\n");
            json.append("      \"total_requests\": ").append(b.getTotalRequests()).append(",\n");
            json.append("      \"total_errors\": ").append(b.getTotalErrors()).append(",\n");

            String cbState = "disabled";
            if (b.getCircuitBreaker() != null) {
                cbState = b.getCircuitBreaker().getState().toString();
            }
            json.append("      \"circuit_breaker\": \"").append(cbState).append("\"\n");
            json.append("    }");
            if (i < backends.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
