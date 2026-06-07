package proxy.balancer;

import proxy.Backend;
import java.util.List;

public class IPHashBalancer implements LoadBalancer {

    @Override
    public String name() { return "ip-hash"; }

    @Override
    public Backend select(List<Backend> backends, String clientIP) {
        List<Backend> alive = backends.stream()
                .filter(Backend::isAlive)
                .toList();

        if (alive.isEmpty()) return null;
        int hash  = clientIP.hashCode();
        int index = Math.abs(hash) % alive.size();

        return alive.get(index);
    }
}
