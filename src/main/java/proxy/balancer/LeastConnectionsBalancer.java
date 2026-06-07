package proxy.balancer;

import proxy.Backend;
import java.util.List;

public class LeastConnectionsBalancer implements LoadBalancer {

    @Override
    public String name() { return "least-connections"; }

    @Override
    public Backend select(List<Backend> backends, String clientIP) {
        Backend best = null;
        int fewest = Integer.MAX_VALUE;

        for (Backend b : backends) {
            if (!b.isAlive()) continue;

            int active = b.getActiveConnections();

            if (active < fewest) {
                fewest = active;
                best = b;
            }

            if (fewest == 0) break;
        }

        return best;
    }
}
