package proxy.health;

import proxy.Backend;
import proxy.ProxyLogger;
import proxy.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthChecker {

    private final List<Backend> backends;
    private final Config config;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    public HealthChecker(List<Backend> backends, Config config) {
        this.backends = backends;
        this.config = config;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getHealthCheckTimeoutSeconds()))
                .build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-checker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        ProxyLogger.info("Health checker started — interval: " +
                config.getHealthCheckIntervalSeconds() + "s, path: " +
                config.getHealthCheckPath());

        scheduler.scheduleAtFixedRate(
                this::checkAll,
                5,
                config.getHealthCheckIntervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        scheduler.shutdown();
        ProxyLogger.info("Health checker stopped");
    }

    private void checkAll() {
        for (Backend backend : backends) {
            checkOne(backend);
        }
    }


    private void checkOne(Backend backend) {
        String healthUrl = backend.getUrl() + config.getHealthCheckPath();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(config.getHealthCheckTimeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            if (response.statusCode() == 200) {
                onSuccess(backend);
            } else {
                onFailure(backend, "HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            onFailure(backend, e.getMessage());
        }
    }

    private void onSuccess(Backend backend) {
        backend.recordHealthSuccess();

        if (!backend.isAlive()) {
            if (backend.getConsecutiveSuccesses() >= config.getHealthCheckSuccessThreshold()) {
                backend.setAlive(true);
                ProxyLogger.info("Backend RECOVERED: " + backend.getUrl() +
                        " (" + backend.getConsecutiveSuccesses() + " consecutive successes)");
            }
        }
    }

    private void onFailure(Backend backend, String reason) {
        backend.recordHealthFailure();

        if (backend.isAlive()) {
            if (backend.getConsecutiveFailures() >= config.getHealthCheckFailureThreshold()) {
                backend.setAlive(false);
                ProxyLogger.warn("Backend MARKED DOWN: " + backend.getUrl() +
                        " | reason: " + reason +
                        " | failures: " + backend.getConsecutiveFailures());
            } else {
                ProxyLogger.warn("Backend health check failed (" +
                        backend.getConsecutiveFailures() + "/" +
                        config.getHealthCheckFailureThreshold() + "): " +
                        backend.getUrl() + " | " + reason);
            }
        }
    }
}
