package proxy.ratelimit;

import proxy.ProxyLogger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RateLimiter {

    private final double requestsPerSecond;
    private final double burst;

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    private record BucketEntry(TokenBucket bucket, long lastAccessNanos) {
        BucketEntry withNow() {
            return new BucketEntry(bucket, System.nanoTime());
        }
    }

    private final ScheduledExecutorService cleaner;
    private static final long EVICT_AFTER_NANOS = TimeUnit.MINUTES.toNanos(10);

    public RateLimiter(double requestsPerSecond, double burst) {
        this.requestsPerSecond = requestsPerSecond;
        this.burst = burst;

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-cleaner");
            t.setDaemon(true);
            return t;
        });

        cleaner.scheduleAtFixedRate(this::evictStale, 5, 5, TimeUnit.MINUTES);
    }


    public boolean allowRequest(String clientIP) {

        BucketEntry entry = buckets.compute(clientIP, (ip, existing) -> {
            if (existing == null) {
                return new BucketEntry(new TokenBucket(burst, requestsPerSecond), System.nanoTime());
            }
            return existing.withNow();
        });


        synchronized (entry.bucket()) {
            return entry.bucket().tryConsume();
        }
    }

    public int retryAfterSeconds(String clientIP) {
        BucketEntry entry = buckets.get(clientIP);
        if (entry == null) return 1;
        synchronized (entry.bucket()) {
            return (int) Math.ceil(entry.bucket().secondsUntilNextToken());
        }
    }


    private void evictStale() {
        long now = System.nanoTime();
        int before = buckets.size();

        Iterator<Map.Entry<String, BucketEntry>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BucketEntry> entry = it.next();
            if (now - entry.getValue().lastAccessNanos() > EVICT_AFTER_NANOS) {
                it.remove();
            }
        }

        int evicted = before - buckets.size();
        if (evicted > 0) {
            ProxyLogger.info("Rate limiter: evicted " + evicted + " stale IP buckets");
        }
    }

    public void stop() {
        cleaner.shutdown();
    }
}
