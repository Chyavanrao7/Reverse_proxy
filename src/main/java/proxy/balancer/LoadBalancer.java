package proxy.balancer;

import proxy.Backend;
import java.util.List;

public interface LoadBalancer {

    Backend select(List<Backend> backends, String clientIP);

    String name();
}
