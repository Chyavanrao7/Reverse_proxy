package proxy;

import com.sun.net.httpserver.HttpServer;
import proxy.balancer.BalancerFactory;
import proxy.balancer.LoadBalancer;
import proxy.circuitbreaker.CircuitBreaker;
import proxy.config.Config;
import proxy.handler.AdminHandler;
import proxy.handler.HttpForwarder;
import proxy.handler.ProxyHandler;
import proxy.health.HealthChecker;
import proxy.ratelimit.RateLimiter;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ReverseProxy {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "config.properties";
        Config config = Config.load(configPath);

        ProxyLogger.info("=== Reverse Proxy Starting ===");
        ProxyLogger.info("Config: " + configPath);
        ProxyLogger.info("Algorithm: " + config.getAlgorithm());

        List<String> urls    = config.getBackendUrls();
        List<Integer> weights = config.getBackendWeights();
        List<Backend> backends = new ArrayList<>();

        for (int i = 0; i < urls.size(); i++) {
            String rawUrl = urls.get(i).trim();
            if (rawUrl.isEmpty()) continue;

            String[] parts = rawUrl.replace("http://", "").split(":");
            String host = parts[0];
            int port    = Integer.parseInt(parts[1]);

            int weight = (i < weights.size()) ? weights.get(i) : 1;

            Backend backend = new Backend(rawUrl, host, port, weight);
            backends.add(backend);
            ProxyLogger.info("Registered backend: " + rawUrl + " (weight=" + weight + ")");
        }

        if (backends.isEmpty()) {
            ProxyLogger.error("No backends configured — check config.properties");
            System.exit(1);
        }

        if (config.isCircuitBreakerEnabled()) {
            for (Backend backend : backends) {
                CircuitBreaker cb = new CircuitBreaker(
                        backend.getUrl(),
                        config.getCircuitBreakerFailureThreshold(),
                        config.getCircuitBreakerTimeoutSeconds()
                );
                backend.setCircuitBreaker(cb);
            }
            ProxyLogger.info("Circuit breakers enabled — threshold: " +
                    config.getCircuitBreakerFailureThreshold() + " failures, timeout: " +
                    config.getCircuitBreakerTimeoutSeconds() + "s");
        }

        LoadBalancer balancer = BalancerFactory.create(config.getAlgorithm(), backends);
        ProxyLogger.info("Load balancer: " + balancer.name());

        HealthChecker healthChecker = null;
        if (config.isHealthCheckEnabled()) {
            healthChecker = new HealthChecker(backends, config);
            healthChecker.start();
        }

        RateLimiter rateLimiter = null;
        if (config.isRateLimitEnabled()) {
            rateLimiter = new RateLimiter(
                    config.getRateLimitRequestsPerSecond(),
                    config.getRateLimitBurst()
            );
            ProxyLogger.info("Rate limiter enabled — " +
                    config.getRateLimitRequestsPerSecond() + " req/s, burst: " +
                    config.getRateLimitBurst());
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpForwarder forwarder = new HttpForwarder(httpClient);

        ProxyHandler proxyHandler = new ProxyHandler(
                backends,
                balancer,
                forwarder,
                rateLimiter,
                config.isCircuitBreakerEnabled()
        );

        AdminHandler adminHandler = new AdminHandler(backends, balancer);

        HttpServer server = HttpServer.create(
                new InetSocketAddress(config.getProxyPort()),
                0
        );

        server.createContext("/", proxyHandler);

        HttpServer adminServer = HttpServer.create(
                new InetSocketAddress(config.getAdminPort()),
                0
        );
        adminServer.createContext("/admin/stats", adminHandler);
        adminServer.createContext("/health", exchange -> {
            byte[] ok = "{\"status\":\"ok\"}\n".getBytes();
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.getResponseBody().close();
        });

        try {
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            adminServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            ProxyLogger.info("Using Java 21 virtual threads");
        } catch (NoSuchMethodError e) {
            server.setExecutor(Executors.newCachedThreadPool());
            adminServer.setExecutor(Executors.newCachedThreadPool());
            ProxyLogger.info("Using platform threads (upgrade to Java 21 for virtual threads)");
        }

        server.start();
        adminServer.start();

        ProxyLogger.info("Proxy listening on      :" + config.getProxyPort());
        ProxyLogger.info("Admin endpoint at       http://localhost:" + config.getAdminPort() + "/admin/stats");
        ProxyLogger.info("Backends: " + backends);
        ProxyLogger.info("=== Ready ===");

        HealthChecker finalHealthChecker = healthChecker;
        RateLimiter   finalRateLimiter   = rateLimiter;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ProxyLogger.info("Shutdown signal received — stopping...");

            if (finalHealthChecker != null) finalHealthChecker.stop();
            if (finalRateLimiter   != null) finalRateLimiter.stop();

            server.stop(5);
            adminServer.stop(0);

            ProxyLogger.info("Shutdown complete");
        }, "shutdown-hook"));
    }
}
