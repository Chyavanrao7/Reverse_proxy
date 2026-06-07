package proxy;

import proxy.circuitbreaker.CircuitBreaker;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Backend {

    private final String url;
    private final String host;
    private final int port;
    private final int weight;

    private final AtomicBoolean alive = new AtomicBoolean(true);

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors   = new AtomicLong(0);

    private final AtomicInteger activeConnections = new AtomicInteger(0);

    private CircuitBreaker circuitBreaker;

    public Backend(String url, String host, int port, int weight) {
        this.url    = url;
        this.host   = host;
        this.port   = port;
        this.weight = weight;
    }

    public void recordRequestStart() {
        activeConnections.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void recordRequestEnd() {
        activeConnections.decrementAndGet();
    }

    public void recordError() {
        totalErrors.incrementAndGet();
    }

    public void recordHealthFailure() {
        consecutiveSuccesses.set(0);
        consecutiveFailures.incrementAndGet();
    }

    public void recordHealthSuccess() {
        consecutiveFailures.set(0);
        consecutiveSuccesses.incrementAndGet();
    }

    public int getConsecutiveFailures()  { return consecutiveFailures.get(); }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses.get(); }

    public String getUrl()              { return url; }
    public String getHost()             { return host; }
    public int getPort()                { return port; }
    public int getWeight()              { return weight; }
    public boolean isAlive()            { return alive.get(); }
    public void setAlive(boolean alive) { this.alive.set(alive); }
    public int getActiveConnections()   { return activeConnections.get(); }
    public long getTotalRequests()      { return totalRequests.get(); }
    public long getTotalErrors()        { return totalErrors.get(); }

    public CircuitBreaker getCircuitBreaker()              { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreaker cb)       { this.circuitBreaker = cb; }

    @Override
    public String toString() {
        return url + (alive.get() ? "" : " [DOWN]");
    }
}
