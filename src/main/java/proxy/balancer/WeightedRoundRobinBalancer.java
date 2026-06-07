package proxy.balancer;

import proxy.Backend;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WeightedRoundRobinBalancer implements LoadBalancer {

    private final List<Backend> expandedPool;
    private final AtomicInteger counter = new AtomicInteger(0);

    public WeightedRoundRobinBalancer(List<Backend> backends) {
        this.expandedPool = new ArrayList<>();
        for (Backend b : backends) {
            int weight = Math.max(1, b.getWeight());
            for (int i = 0; i < weight; i++) {
                expandedPool.add(b);
            }
        }
    }

    @Override
    public String name() { return "weighted-round-robin"; }

    @Override
    public Backend select(List<Backend> backends, String clientIP)
        int size = expandedPool.size();
        if (size == 0) return null;

        for (int attempt = 0; attempt < size; attempt++) {
            int index = Math.abs(counter.getAndIncrement()) % size;
            Backend candidate = expandedPool.get(index);
            if (candidate.isAlive()) {
                return candidate;
            }
        }

        return null;
    }
}
