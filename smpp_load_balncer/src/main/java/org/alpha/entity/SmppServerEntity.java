package org.alpha.entity;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "smpp_servers")
public class SmppServerEntity extends PanacheEntity {

    @Column(nullable = false)
    public String host;

    @Column(nullable = false)
    public int port;

    @Column(name = "system_id", nullable = false)
    public String systemId;

    @Column(nullable = false)
    public String password;

    @Column(nullable = false)
    public int priority;
    @Column
    public String region;

    @Column(nullable = false)
    public boolean status;

    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
