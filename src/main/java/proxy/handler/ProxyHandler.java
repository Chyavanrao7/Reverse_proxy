package proxy.handler;

import com.sun.net.httpserver.HttpExchange;
import proxy.Backend;
import proxy.ProxyLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public class HttpForwarder {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding",
            "te", "trailer", "upgrade", "proxy-authorization",
            "proxy-authenticate"
    );

    private final HttpClient httpClient;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;

    public HttpForwarder(HttpClient httpClient) {
        this.httpClient = httpClient;
    }


    public int forward(HttpExchange exchange, Backend backend, String requestId) throws IOException {

        String path = exchange.getRequestURI().toString();
        URI backendUri = URI.create(backend.getUrl() + path);

        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        HttpRequest.BodyPublisher bodyPublisher = (requestBody.length > 0)
                ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
                : HttpRequest.BodyPublishers.noBody();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(backendUri)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .method(exchange.getRequestMethod(), bodyPublisher);

        exchange.getRequestHeaders().forEach((name, values) -> {
            String lowerName = name.toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lowerName)) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        String clientAddr = exchange.getRemoteAddress().getAddress().getHostAddress();
        builder.header("X-Real-IP", clientAddr);
        builder.header("X-Forwarded-For", clientAddr);
        builder.header("X-Request-ID", requestId);
        builder.header("X-Proxy-By", "java-reverse-proxy/2.0");

        HttpRequest request = builder.build();

        try {

            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            int statusCode = response.statusCode();
            byte[] responseBody = response.body();

            response.headers().map().forEach((name, values) -> {
                String lower = name.toLowerCase();
                if (!HOP_BY_HOP_HEADERS.contains(lower)) {
                    values.forEach(value -> exchange.getResponseHeaders().add(name, value));
                }
            });

            exchange.getResponseHeaders().set("X-Request-ID", requestId);

            exchange.sendResponseHeaders(statusCode, responseBody.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(responseBody);
            }

            return statusCode;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return sendError(exchange, 502, "Proxy interrupted");

        } catch (Exception e) {
            ProxyLogger.error("Backend unreachable: " + backend.getUrl(), e);
            return sendError(exchange, 502, "Backend unreachable: " + e.getMessage());
        }
    }

    private int sendError(HttpExchange exchange, int status, String message) {
        try {
            byte[] body = (message + "\n").getBytes();
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException ignored) {}
        return status;
    }
}
