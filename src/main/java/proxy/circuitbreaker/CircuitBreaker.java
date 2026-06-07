package proxy.circuitbreaker;

import proxy.ProxyLogger;

import java.time.Duration;
import java.time.Instant;

public class CircuitBreaker {

    private final String backendUrl;
    private final int failureThreshold;
    private final Duration openTimeout;

    private CircuitState state = CircuitState.CLOSED;
    private int failureCount = 0;
    private Instant openedAt = null;

    public CircuitBreaker(String backendUrl, int failureThreshold, int timeoutSeconds) {
        this.backendUrl       = backendUrl;
        this.failureThreshold = failureThreshold;
        this.openTimeout      = Duration.ofSeconds(timeoutSeconds);
    }

    public synchronized boolean allowRequest() {
        switch (state) {

            case CLOSED:
                return true;

            case OPEN:
                if (Duration.between(openedAt, Instant.now()).compareTo(openTimeout) > 0) {
                    transitionTo(CircuitState.HALF_OPEN);
                    return true;
                }

                return false;

            case HALF_OPEN:
                return false;

            default:
                return true;
        }
    }

    public synchronized void recordSuccess() {
        switch (state) {
            case HALF_OPEN:
                ProxyLogger.info("Circuit CLOSED for " + backendUrl + " — backend recovered");
                failureCount = 0;
                transitionTo(CircuitState.CLOSED);
                break;

            case CLOSED:
                failureCount = 0;
                break;

            case OPEN:
                break;
        }
    }

    public synchronized void recordFailure() {
        switch (state) {

            case CLOSED:
                failureCount++;
                if (failureCount >= failureThreshold) {
                    ProxyLogger.warn("Circuit OPENED for " + backendUrl +
                            " — " + failureCount + " consecutive failures");
                    openedAt = Instant.now();
                    transitionTo(CircuitState.OPEN);
                }
                break;

            case HALF_OPEN:
                ProxyLogger.warn("Circuit re-OPENED for " + backendUrl +
                        " — probe request failed");
                openedAt = Instant.now();
                transitionTo(CircuitState.OPEN);
                break;

            case OPEN:
                break;
        }
    }

    public synchronized CircuitState getState() {
        return state;
    }

    public synchronized boolean isOpen() {
        return state == CircuitState.OPEN || state == CircuitState.HALF_OPEN;
    }

    private void transitionTo(CircuitState newState) {
        ProxyLogger.info("CircuitBreaker [" + backendUrl + "]: " + state + " → " + newState);
        this.state = newState;
    }
}
