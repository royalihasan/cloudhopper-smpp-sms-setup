package org.alpha;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import java.util.List;

@ConfigMapping(prefix = "loadbalancer")
public interface LoadBalancerConfig {
    @WithName("port")
    int port();

    @WithName("servers")
    List<ServerConfig> servers();

    interface ServerConfig {
        @WithName("host")
        String host();

        @WithName("port")
        int port();
    }
}