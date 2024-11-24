package org.alpha.repository;


import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.alpha.entity.SmppServerEntity;

import java.util.List;

@ApplicationScoped
public class SmppServerRepository implements PanacheRepository<SmppServerEntity> {
    public List<SmppServerEntity> findByRegion(String region) {
        return list("region", region);
    }

    public List<SmppServerEntity> findActiveServers() {
        return list("status", true);
    }
}
