package proxy.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Config {

    private final int proxyPort;
    private final int adminPort;

    private final List<String> backendUrls;
    private final List<Integer> backendWeights;
    private final String algorithm;

    private final boolean healthCheckEnabled;
    private final int healthCheckIntervalSeconds;
    private final int healthCheckTimeoutSeconds;
    private final String healthCheckPath;
    private final int healthCheckFailureThreshold;
    private final int healthCheckSuccessThreshold;

    private final boolean circuitBreakerEnabled;
    private final int circuitBreakerFailureThreshold;
    private final int circuitBreakerTimeoutSeconds;

    private final boolean rateLimitEnabled;
    private final double rateLimitRequestsPerSecond;
    private final int rateLimitBurst;

    private Config(Properties p) {
        this.proxyPort  = Integer.parseInt(p.getProperty("proxy.port", "8080"));
        this.adminPort  = Integer.parseInt(p.getProperty("proxy.admin.port", "9090"));

        this.backendUrls = Arrays.asList(p.getProperty("backends", "").split(","));

        this.backendWeights = Arrays.stream(
                        p.getProperty("backend.weights", "1").split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();

        this.algorithm = p.getProperty("load.balancer.algorithm", "roundrobin").trim();

        this.healthCheckEnabled          = Boolean.parseBoolean(p.getProperty("health.check.enabled", "true"));
        this.healthCheckIntervalSeconds  = Integer.parseInt(p.getProperty("health.check.interval.seconds", "10"));
        this.healthCheckTimeoutSeconds   = Integer.parseInt(p.getProperty("health.check.timeout.seconds", "2"));
        this.healthCheckPath             = p.getProperty("health.check.path", "/health");
        this.healthCheckFailureThreshold = Integer.parseInt(p.getProperty("health.check.failure.threshold", "3"));
        this.healthCheckSuccessThreshold = Integer.parseInt(p.getProperty("health.check.success.threshold", "2"));

        this.circuitBreakerEnabled          = Boolean.parseBoolean(p.getProperty("circuit.breaker.enabled", "true"));
        this.circuitBreakerFailureThreshold = Integer.parseInt(p.getProperty("circuit.breaker.failure.threshold", "5"));
        this.circuitBreakerTimeoutSeconds   = Integer.parseInt(p.getProperty("circuit.breaker.timeout.seconds", "30"));

        this.rateLimitEnabled            = Boolean.parseBoolean(p.getProperty("rate.limit.enabled", "true"));
        this.rateLimitRequestsPerSecond  = Double.parseDouble(p.getProperty("rate.limit.requests.per.second", "10"));
        this.rateLimitBurst              = Integer.parseInt(p.getProperty("rate.limit.burst", "20"));
    }

    public static Config load(String path) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read config file: " + path, e);
        }
        return new Config(props);
    }

    // ── Getters (no setters — immutable after construction) ───────────────────
    public int getProxyPort()                   { return proxyPort; }
    public int getAdminPort()                   { return adminPort; }
    public List<String> getBackendUrls()        { return backendUrls; }
    public List<Integer> getBackendWeights()    { return backendWeights; }
    public String getAlgorithm()                { return algorithm; }

    public boolean isHealthCheckEnabled()       { return healthCheckEnabled; }
    public int getHealthCheckIntervalSeconds()  { return healthCheckIntervalSeconds; }
    public int getHealthCheckTimeoutSeconds()   { return healthCheckTimeoutSeconds; }
    public String getHealthCheckPath()          { return healthCheckPath; }
    public int getHealthCheckFailureThreshold() { return healthCheckFailureThreshold; }
    public int getHealthCheckSuccessThreshold() { return healthCheckSuccessThreshold; }

    public boolean isCircuitBreakerEnabled()       { return circuitBreakerEnabled; }
    public int getCircuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
    public int getCircuitBreakerTimeoutSeconds()   { return circuitBreakerTimeoutSeconds; }

    public boolean isRateLimitEnabled()           { return rateLimitEnabled; }
    public double getRateLimitRequestsPerSecond() { return rateLimitRequestsPerSecond; }
    public int getRateLimitBurst()                { return rateLimitBurst; }
}
