package org.alpha;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LoadBalancerStarter {
    private static final Logger LOG = Logger.getLogger(LoadBalancerStarter.class);

    @Inject
    LoadBalancer loadBalancer;

    void onStart(@Observes StartupEvent ev) {
        LOG.info("Starting SMPP Load Balancer...");
        loadBalancer.start();
    }
}