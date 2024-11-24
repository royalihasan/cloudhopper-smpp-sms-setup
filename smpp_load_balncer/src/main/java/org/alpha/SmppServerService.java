package org.alpha;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.alpha.entity.SmppServerEntity;

import java.util.List;

@ApplicationScoped
public class SmppServerService {

    // Insert sample SMPP servers
    @Transactional
    public void insertTestServers() {
        SmppServerEntity server1 = new SmppServerEntity();
        server1.host = "smpp.server1.com";
        server1.port = 2775;
        server1.systemId = "system1";
        server1.password = "password1";
        server1.priority = 1;
        server1.region = "North America";
        server1.status = true;

    }

    // Optional: List all servers (to verify insertion)
    public List<SmppServerEntity> getAllServers() {
        return SmppServerEntity.listAll();
    }
}
