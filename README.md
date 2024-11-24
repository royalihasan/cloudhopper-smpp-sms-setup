
# Two-Way SMS SMPP Project

This repository contains a two-way SMS solution, implemented using two main modules: **smpp_load_balancer** and **smpp_server_quarkus_docker_setup**. These modules leverage **Quarkus**, a Kubernetes-native Java framework designed for cloud-native applications.

---

---

## Modules Overview

### **1. smpp_load_balancer**
The **smpp_load_balancer** module is designed to distribute SMS message traffic efficiently across multiple SMPP connections. It utilizes **Quarkus's reactive capabilities** and integrates with PostgreSQL for persistence.

#### **Key Features**
- **Load Balancing**: Distributes SMS traffic across multiple SMPP connections.
- **RESTful API**: Provides endpoints for managing and monitoring SMPP connections.
- **PostgreSQL Integration**: Ensures data persistence for server configurations.
- **Scheduled Tasks**: Leverages the Quarkus Scheduler for periodic jobs.
### Prerequisites
- **Java 17+**
- **Maven 3.6+**
- **Docker**
- **PostgreSQL**
#### **Configuration Properties**
The load balancer module requires the following configurations:

```properties
# Datasource configuration for PostgreSQL
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${QUARKUS_DATASOURCE_USERNAME:smpp_admin}
quarkus.datasource.password=${QUARKUS_DATASOURCE_PASSWORD:smpp_password}
quarkus.datasource.jdbc.url=${QUARKUS_DATASOURCE_URL:jdbc:postgresql://localhost:5432/smpp_server}

# Hibernate configuration
quarkus.hibernate-orm.database.generation=update
quarkus.hibernate-orm.log.sql=true

# Load balancer port
loadbalancer.port=3000
```


### Running the Load Balancer

Note: Make you are in write directory
```bash
cd smpp_load_balncer
```
To run the module in development mode:

```bash
./mvnw quarkus:dev
```

### Building and Running the Docker Image

1. **Clean the project**:
   ```bash
   ./mvnw clean
   ```
2. **Package the application**:
   ```bash
   ./mvnw package
   ```
3. **Build the Docker image**:
   ```bash
   docker build -f src/main/docker/Dockerfile.jvm -t quarkus/smpp_load_balancer .
   ```
   > **Note**: Ensure the `Dockerfile.jvm` is present at the specified path.

---

## Configuration

### Load Balancer Configuration

The following properties are used to configure the load balancer:

```properties
# Datasource configuration for PostgreSQL
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${QUARKUS_DATASOURCE_USERNAME:smpp_admin}
quarkus.datasource.password=${QUARKUS_DATASOURCE_PASSWORD:smpp_password}
quarkus.datasource.jdbc.url=${QUARKUS_DATASOURCE_URL:jdbc:postgresql://localhost:5432/smpp_server}

# Hibernate configuration
quarkus.hibernate-orm.database.generation=update
quarkus.hibernate-orm.log.sql=true

# Load balancer port
loadbalancer.port=3000
```

### Database Schema: `smpp_servers`

The load balancer relies on a database to manage SMPP server configurations, including details like host, port, and priority.

```sql
-- Create the smpp_servers table
CREATE TABLE smpp_servers (
    id SERIAL PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    system_id VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    priority INT DEFAULT 0, -- Priority for weighted routing
    region VARCHAR(100), -- Optional region for geographic routing
    status BOOLEAN DEFAULT TRUE, -- Indicates if the server is active
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Function to update the 'updated_at' column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for 'updated_at' column
CREATE TRIGGER set_updated_at
BEFORE UPDATE ON smpp_servers
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
```

---
### Prerequisites
- **Java 11+**
- **Maven 3.6+**
- **Docker**
- **PostgreSQL**