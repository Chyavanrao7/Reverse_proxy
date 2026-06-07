package proxy.balancer;

import proxy.Backend;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String name() { return "round-robin"; }

    @Override
    public Backend select(List<Backend> backends, String clientIP) {
        List<Backend> alive = backends.stream()
                .filter(Backend::isAlive)
                .toList();

        if (alive.isEmpty()) return null;
        int index = Math.abs(counter.getAndIncrement()) % alive.size();
        return alive.get(index);
    }
}
