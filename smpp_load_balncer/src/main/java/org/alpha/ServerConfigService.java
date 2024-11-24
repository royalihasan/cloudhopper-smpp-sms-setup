package org.alpha;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.alpha.entity.SmppServerEntity;

import java.util.List;

import org.jboss.logging.Logger;

@ApplicationScoped
public class ServerConfigService {
    private static final Logger LOG = Logger.getLogger(ServerConfigService.class);

    @Transactional
    public List<SmppServerEntity> getActiveServers() {
        return SmppServerEntity.find("status = true ORDER BY priority DESC").list();
    }

    @Transactional
    public void markServerAsInactive(Long serverId) {
        SmppServerEntity server = SmppServerEntity.findById(serverId);
        if (server != null) {
            server.status = false;
            server.persist();
            LOG.info("Server " + server.host + " marked as inactive.");
        }
    }
    @Transactional
    public List<SmppServerEntity> getInactiveServers() {
        return SmppServerEntity.find("status = false").list();
    }
    @Transactional
    public void markServerAsActive(Long serverId) {
        SmppServerEntity server = SmppServerEntity.findById(serverId);
        if (server != null && !server.status) { // Ensure it was inactive
            server.status = true;
            server.persist();
            LOG.info("Server " + server.host + " marked as active.");
        }
    }
}

