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
import java.util.concurrent.Executors;

@ApplicationScoped
public class SmppServerQuarkusApp {

    private static final Logger logger = LoggerFactory.getLogger(SmppServerQuarkusApp.class);

    private DefaultSmppServer smppServer;

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

    public void startServer() {
        try {
            var executor = Executors.newVirtualThreadPerTaskExecutor();

            // Configuration for SMPP server
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

            smppServer = new DefaultSmppServer(configuration, new DefaultSmppServerHandler(), executor);
            logger.info("Starting SMPP server on port {}...", port);
            smppServer.start();
            logger.info("SMPP server started successfully!");
        } catch (Exception e) {
            logger.error("Failed to start SMPP server", e);
        }
    }

    public void stopServer() {
        if (smppServer != null) {
            try {
                smppServer.stop();
                logger.info("SMPP server stopped.");
            } catch (Exception e) {
                logger.error("Error stopping SMPP server", e);
            }
        }
    }

    public static class DefaultSmppServerHandler implements SmppServerHandler {
        @Override
        public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, BaseBind bindRequest) throws SmppProcessingException {
            sessionConfiguration.setName("Application.SMPP." + sessionConfiguration.getSystemId());
        }

        @Override
        public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException {
            logger.info("Session created: {}", session);
            session.serverReady(new TestSmppSessionHandler(session));
        }

        @Override
        public void sessionDestroyed(Long sessionId, SmppServerSession session) {
            logger.info("Session destroyed: {}", session);
            session.destroy();
        }
    }

    public static class TestSmppSessionHandler extends DefaultSmppSessionHandler {
        private final WeakReference<SmppSession> sessionRef;

        private String serverName;

        public TestSmppSessionHandler(SmppSession session) {
            this.sessionRef = new WeakReference<>(session);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            SmppSession session = sessionRef.get();

            if (pduRequest instanceof SubmitSm submitSm) {
                String messageContent = CharsetUtil.decode(submitSm.getShortMessage(), CharsetUtil.CHARSET_ISO_8859_1);
                logger.info("Message received from client: {}", messageContent);

                if (session != null) {
                    try {
                        // Send initial response
                        DeliverSm deliver = new DeliverSm();
                        deliver.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
                        deliver.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
                        deliver.setShortMessage(CharsetUtil.encode("Server-1 : Bye , World", CharsetUtil.CHARSET_ISO_8859_1));
                        session.sendRequestPdu(deliver, 10000, false);
                        logger.info( serverName +": Response sent to client: Bye , World");

                        // Send Delivery Report (DLR)
                        DeliverSm dlr = new DeliverSm();
                        dlr.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
                        dlr.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));

                        // Construct DLR message with status
                        String dlrMessage = "id:12345 sub:001 dlvrd:001 submit date:230101 done date:230101 stat:DELIVRD err:000";
                        dlr.setShortMessage(CharsetUtil.encode(dlrMessage, CharsetUtil.CHARSET_ISO_8859_1));

                        // Set additional PDU parameters to indicate it's a delivery report
                        dlr.setEsmClass((byte) (0x04 | 0x00)); // Set the esm_class to indicate delivery receipt

                        session.sendRequestPdu(dlr, 10000, false);
                        logger.info("Server-1: Delivery Report sent to client");
                    } catch (Exception e) {
                        logger.error("Error sending response to client", e);
                    }
                }
            }

            return pduRequest.createResponse();
        }
    }
}
