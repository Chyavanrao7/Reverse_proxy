package proxy.balancer;

import proxy.Backend;
import java.util.List;

public class BalancerFactory {
    public static LoadBalancer create(String algorithm, List<Backend> backends) {
        return switch (algorithm.toLowerCase().trim()) {
            case "roundrobin", "round-robin", ""
                    -> new RoundRobinBalancer();

            case "weighted", "weightedroundrobin", "weighted-round-robin"
                    -> new WeightedRoundRobinBalancer(backends);

            case "leastconn", "leastconnections", "least-connections"
                    -> new LeastConnectionsBalancer();

            case "iphash", "ip-hash"
                    -> new IPHashBalancer();

            default -> throw new IllegalArgumentException(
                    "Unknown algorithm: '" + algorithm + "'. " +
                            "Valid options: roundrobin, weighted, leastconn, iphash");
        };
    }
}
