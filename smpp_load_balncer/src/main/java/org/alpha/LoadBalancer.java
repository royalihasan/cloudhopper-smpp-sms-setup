package org.alpha;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

import org.alpha.entity.SmppServerEntity;
import org.jboss.logging.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class LoadBalancer {

    private static final Logger LOG = Logger.getLogger(LoadBalancer.class);

    private final ServerConfigService serverConfigService;
    private final LoadBalancerConfig config;

    private ServerSocket serverSocket;
    private final AtomicInteger currentServerIndex = new AtomicInteger(0);
    private volatile boolean running = true;
    private final CopyOnWriteArrayList<SmppServerEntity> activeServers = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<String, ClientInfo> clientDetails = new ConcurrentHashMap<>();

    @Inject
    public LoadBalancer(ServerConfigService serverConfigService, LoadBalancerConfig config) {
        this.serverConfigService = serverConfigService;
        this.config = config;
    }

    public static class ClientInfo {
        public String ip;
        public long connectionTime;
        public int messagesSent;

        public ClientInfo(String ip, long connectionTime) {
            this.ip = ip;
            this.connectionTime = connectionTime;
            this.messagesSent = 0;
        }
    }

    public void start() {
        try {
            LOG.info("Initializing load balancer on port: " + config.port());
            serverSocket = new ServerSocket(config.port());
            LOG.info("Load balancer started successfully on port " + config.port());

            refreshServerList();

            Thread acceptThread = new Thread(this::acceptConnections, "lb-acceptor");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException e) {
            LOG.error("Failed to start load balancer", e);
            throw new RuntimeException("Failed to start load balancer", e);
        }
    }

    @Scheduled(every = "30s")
    void refreshServerList() {
        List<SmppServerEntity> active = serverConfigService.getActiveServers();
        List<SmppServerEntity> healthyServers = new ArrayList<>();

        for (SmppServerEntity server : active) {
            if (isServerHealthy(server)) {
                healthyServers.add(server);
                LOG.info("Server " + server.host + " is healthy.");
            } else {
                serverConfigService.markServerAsInactive(server.id);
            }
        }

        List<SmppServerEntity> inactive = serverConfigService.getInactiveServers();
        for (SmppServerEntity server : inactive) {
            if (isServerHealthy(server)) {
                serverConfigService.markServerAsActive(server.id);
                healthyServers.add(server);
                LOG.info("Server " + server.host + " is now healthy and reactivated.");
            }
        }

        activeServers.clear();
        activeServers.addAll(healthyServers);
        LOG.info("Refreshed server list. Active servers: " + activeServers.size());
        activeServers.forEach(s -> LOG.info("Active SMPP server: " + s.host + ":" + s.port +
                " (Priority: " + s.priority + ", Region: " + s.region + ")"));
    }

    private boolean isServerHealthy(SmppServerEntity server) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(server.host, server.port), 5000);
            socket.close();
            return true;
        } catch (IOException e) {
            LOG.warn("Health check failed for server: " + server.host + ":" + server.port);
            return false;
        }
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientIp = clientSocket.getInetAddress().getHostAddress();
                clientDetails.put(clientIp, new ClientInfo(clientIp, System.currentTimeMillis()));
                LOG.info("New client connection accepted from: " + clientIp);

                handleClient(clientSocket);
            } catch (IOException e) {
                if (running) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }
    private void logSmppBindRequest(InputStream in, String clientHost, OutputStream out) {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead = in.read(buffer);  // Read data from the client

            if (bytesRead > 0) {
                // Create an InputStream to parse the data
                ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer, 0, bytesRead);
                DataInputStream dataIn = new DataInputStream(byteStream);

                // Parse the SMPP bind request PDU
                int commandLength = dataIn.readInt();   // Total length of the PDU
                int commandId = dataIn.readInt();       // Command ID
                int commandStatus = dataIn.readInt();   // Command Status (should be 0 for requests)
                int sequenceNumber = dataIn.readInt();  // Sequence Number

                // Read the system ID (client name), which is typically a 16-byte field in the bind request
                byte[] systemIdBytes = new byte[16];
                dataIn.read(systemIdBytes);  // Read the system ID (client name)
                String clientName = new String(systemIdBytes).trim();  // Convert bytes to string

                // Log the parsed data
                LOG.info("SMPP Client Bind Request:");
                LOG.info("Client Host: " + clientHost);
                LOG.info("Client Name (System ID): " + clientName);
                LOG.info("Command Length: " + commandLength);
                LOG.info("Command ID: " + commandId);
                LOG.info("Command Status: " + commandStatus);
                LOG.info("Sequence Number: " + sequenceNumber);

                // Forward the data to the server
                out.write(buffer, 0, bytesRead);  // Pass the data forward to the server
                out.flush();
            }
        } catch (IOException e) {
            LOG.error("Failed to parse SMPP bind request from client at " + clientHost, e);
        }
    }




    private void handleClient(Socket clientSocket) {
        SmppServerEntity server = getNextServer();

        if (server == null) {
            LOG.error("No active servers available. Returning 503 Service Unavailable.");
            send503ServiceUnavailable(clientSocket);
            return;
        }


        CompletableFuture.runAsync(() -> {
            try {

                // Connect to the SMPP server (create server-side socket)
                Socket serverSocket = new Socket(server.host, server.port);
                LOG.info("Successfully connected to server: " + server.host + ":" + server.port);

                // Log and forward the bind request from client to the server
                logSmppBindRequest(clientSocket.getInputStream(), clientSocket.getInetAddress().getHostAddress(),
                        serverSocket.getOutputStream());

                LOG.info("Connecting client to server: " + server.host + ":" + server.port);
                LOG.info("Successfully connected to server: " + server.host + ":" + server.port);

                Stream clientToServer = new Stream(clientSocket, serverSocket,
                        String.format("Client → Server(%s:%d)", server.host, server.port));
                Stream serverToClient = new Stream(serverSocket, clientSocket,
                        String.format("Server(%s:%d) → Client", server.host, server.port));

                CompletableFuture.allOf(
                        CompletableFuture.runAsync(() -> clientToServer.forward(clientDetails)),
                        CompletableFuture.runAsync(() -> serverToClient.forward(clientDetails))
                ).join();
            } catch (IOException e) {
                LOG.error("Error connecting to server " + server.host + ":" + server.port, e);
                closeQuietly(clientSocket);
            }
        });
    }

    private SmppServerEntity getNextServer() {
        if (activeServers.isEmpty()) {
            return null;
        }
        return activeServers.get(Math.abs(currentServerIndex.getAndIncrement() % activeServers.size()));
    }

    private void send503ServiceUnavailable(Socket clientSocket) {
        try {
            OutputStream out = clientSocket.getOutputStream();
            String response = "HTTP/1.1 503 Service Unavailable\r\n\r\nNo active SMPP servers available.";
            out.write(response.getBytes());
            out.flush();
            closeQuietly(clientSocket);
        } catch (IOException e) {
            LOG.error("Error sending 503 response", e);
        }
    }

    @GET
    public Map<String, ClientInfo> getClientDetails() {
        return clientDetails;
    }

    @PreDestroy
    public void stop() {
        LOG.info("Shutting down load balancer...");
        running = false;
        closeQuietly(serverSocket);
        LOG.info("Load balancer stopped");
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing resource", e);
        }
    }

    private static class Stream {
        private final Socket source;
        private final Socket destination;
        private final String name;
        private final byte[] buffer = new byte[8192];
        private static final Logger LOG = Logger.getLogger(Stream.class);

        Stream(Socket source, Socket destination, String name) {
            this.source = source;
            this.destination = destination;
            this.name = name;
        }

        void forward(Map<String, ClientInfo> clientDetails) {
            try {
                InputStream in = source.getInputStream();
                OutputStream out = destination.getOutputStream();

                String clientIp = source.getInetAddress().getHostAddress();

                int bytes;
                while ((bytes = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytes);
                    out.flush();
                    LOG.debug(name + ": Forwarded " + bytes + " bytes");

                    // Increment message count
                    if (clientDetails.containsKey(clientIp)) {
                        clientDetails.get(clientIp).messagesSent++;
                    }
                }
            } catch (IOException e) {
                LOG.warn(name + ": Connection closed or failed");
            } finally {
                closeQuietly(source);
                closeQuietly(destination);
            }
        }
    }
}
