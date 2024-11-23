package org.alpha;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.SmppProcessingException;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;

@ApplicationScoped
public class SmppServerQuarkusApp {
    private static final Logger logger = LoggerFactory.getLogger(SmppServerQuarkusApp.class);

    private DefaultSmppServer smppServer;

    @ConfigProperty(name = "smpp.server.name", defaultValue = "Server-000")
    String serverName;

    @ConfigProperty(name = "smpp.port", defaultValue = "2775")
    int port;

    @ConfigProperty(name = "smpp.max-connections", defaultValue = "10")
    int maxConnectionSize;

    @ConfigProperty(name = "smpp.request-expiry-timeout", defaultValue = "30000")
    long defaultRequestExpiryTimeout;

    @ConfigProperty(name = "smpp.window-monitor-interval", defaultValue = "15000")
    long defaultWindowMonitorInterval;

    @ConfigProperty(name = "smpp.window-size", defaultValue = "50")
    int defaultWindowSize;

    @ConfigProperty(name = "smpp.window-wait-timeout", defaultValue = "30000")
    long defaultWindowWaitTimeout;

    @ConfigProperty(name = "smpp.non-blocking-sockets", defaultValue = "true")
    boolean nonBlockingSocketsEnabled;

    @ConfigProperty(name = "smpp.session-counters", defaultValue = "true")
    boolean sessionCountersEnabled;

    @ConfigProperty(name = "smpp.jmx-enabled", defaultValue = "false")
    boolean jmxEnabled;

    public void onStart(@Observes StartupEvent event) {
        startServer();
    }

    @PreDestroy
    public void onStop() {
        stopServer();
    }

    public void startServer() {
        try {
            var executor = Executors.newVirtualThreadPerTaskExecutor();

            SmppServerConfiguration configuration = new SmppServerConfiguration();
            configuration.setPort(port);
            configuration.setMaxConnectionSize(maxConnectionSize);
            configuration.setDefaultRequestExpiryTimeout(defaultRequestExpiryTimeout);
            configuration.setDefaultWindowMonitorInterval(defaultWindowMonitorInterval);
            configuration.setDefaultWindowSize(defaultWindowSize);
            configuration.setDefaultWindowWaitTimeout(defaultWindowWaitTimeout);
            configuration.setNonBlockingSocketsEnabled(nonBlockingSocketsEnabled);
            configuration.setDefaultSessionCountersEnabled(sessionCountersEnabled);
            configuration.setJmxEnabled(jmxEnabled);
            configuration.setSystemId(serverName);

            DefaultSmppServerHandler serverHandler = new DefaultSmppServerHandler(serverName);
            smppServer = new DefaultSmppServer(configuration, serverHandler, executor);

            logger.info("[{}] Starting SMPP server on port {}...", serverName, port);
            smppServer.start();
            logger.info("[{}] SMPP server started successfully!", serverName);

        } catch (Exception e) {
            logger.error("[{}] Failed to start SMPP server: {}", serverName, e.getMessage(), e);
        }
    }

    public void stopServer() {
        if (smppServer != null) {
            try {
                smppServer.stop();
                logger.info("[{}] SMPP server stopped", serverName);
            } catch (Exception e) {
                logger.error("[{}] Error stopping SMPP server: {}", serverName, e.getMessage(), e);
            }
        }
    }

    public static class DefaultSmppServerHandler implements SmppServerHandler {
        private final String serverName;
        private static final Logger logger = LoggerFactory.getLogger(DefaultSmppServerHandler.class);

        public DefaultSmppServerHandler(String serverName) {
            this.serverName = serverName;
            logger.info("[{}] Initializing server handler", serverName);
        }

        @Override
        public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, BaseBind bindRequest) throws SmppProcessingException {
            sessionConfiguration.setName(serverName);
            sessionConfiguration.setSystemId(serverName);
            logger.info("[{}] Session bind requested for systemId: {}", serverName, sessionConfiguration.getSystemId());
        }

        @Override
        public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException {
            logger.info("[{}] Session created with ID: {}", serverName, sessionId);
            session.serverReady(new TestSmppSessionHandler(session, serverName));
        }

        @Override
        public void sessionDestroyed(Long sessionId, SmppServerSession session) {
            logger.info("[{}] Session destroyed: {}", serverName, sessionId);
            session.destroy();
        }
    }

    public static class TestSmppSessionHandler extends DefaultSmppSessionHandler {
        private final WeakReference<SmppSession> sessionRef;
        private final String serverName;
        private static final Logger logger = LoggerFactory.getLogger(TestSmppSessionHandler.class);

        public TestSmppSessionHandler(SmppSession session, String serverName) {
            super();
            this.sessionRef = new WeakReference<>(session);
            this.serverName = serverName;
            logger.info("[{}] Initializing session handler", serverName);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            SmppSession session = sessionRef.get();

            if (pduRequest instanceof SubmitSm submitSm) {
                String messageContent = CharsetUtil.decode(submitSm.getShortMessage(), CharsetUtil.CHARSET_ISO_8859_1);
                logger.info("[{}] Received message: {}", serverName, messageContent);

                if (session != null) {
                    try {
                        // Send initial response
                        String responseMessage = String.format("%s: Bye, World", serverName);
                        DeliverSm deliver = new DeliverSm();
                        deliver.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, serverName));
                        deliver.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
                        deliver.setShortMessage(CharsetUtil.encode(responseMessage, CharsetUtil.CHARSET_ISO_8859_1));

                        session.sendRequestPdu(deliver, 10000, false);
                        logger.info("[{}] Sent response: {}", serverName, responseMessage);

                        // Send DLR
                        String dlrMessage = String.format("id:%s-%d sub:001 dlvrd:001 submit date:%s done date:%s stat:DELIVRD err:000",
                                serverName,
                                System.currentTimeMillis() % 10000,
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd")),
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd")));

                        DeliverSm dlr = new DeliverSm();
                        dlr.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, serverName));
                        dlr.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
                        dlr.setShortMessage(CharsetUtil.encode(dlrMessage, CharsetUtil.CHARSET_ISO_8859_1));
                        dlr.setEsmClass((byte) (0x04 | 0x00));

                        session.sendRequestPdu(dlr, 10000, false);
                        logger.info("[{}] Sent delivery report: {}", serverName, dlrMessage);

                    } catch (Exception e) {
                        logger.error("[{}] Error processing message: {}", serverName, e.getMessage(), e);
                    }
                } else {
                    logger.warn("[{}] Cannot process message - session is null", serverName);
                }
            }

            return pduRequest.createResponse();
        }
    }
}