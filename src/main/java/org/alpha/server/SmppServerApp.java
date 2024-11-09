package org.alpha.server;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.SmppProcessingException;
import org.alpha.utils.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;


public class SmppServerApp {
    private static final Logger logger = LoggerFactory.getLogger(SmppServerApp.class);

    public static void main(String[] args) throws Exception {
        // Use virtual threads for better scalability
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        SmppServerConfiguration configuration = new SmppServerConfiguration();


        // Configure SMPP server settings
        configuration.setPort(PropertiesLoader.get("smpp.server.port", Integer.class));
        configuration.setMaxConnectionSize(PropertiesLoader.get("smpp.server.maxConnectionSize", Integer.class));
        configuration.setDefaultRequestExpiryTimeout(PropertiesLoader.get("smpp.server.defaultRequestExpiryTimeout", Long.class));
        configuration.setDefaultWindowMonitorInterval(PropertiesLoader.get("smpp.server.defaultWindowMonitorInterval", Long.class));
        configuration.setDefaultWindowSize(PropertiesLoader.get("smpp.server.defaultWindowSize", Integer.class));
        configuration.setDefaultWindowWaitTimeout(PropertiesLoader.get("smpp.server.defaultWindowWaitTimeout", Long.class));
        configuration.setNonBlockingSocketsEnabled(PropertiesLoader.get("smpp.server.nonBlockingSocketsEnabled", Boolean.class));
        configuration.setDefaultSessionCountersEnabled(PropertiesLoader.get("smpp.server.sessionCountersEnabled", Boolean.class));
        configuration.setJmxEnabled(PropertiesLoader.get("smpp.server.jmxEnabled", Boolean.class));


        // Initialize the SMPP server with custom handler and executor
        DefaultSmppServer smppServer = new DefaultSmppServer(configuration, new DefaultSmppServerHandler(), executor);

        logger.info("Starting SMPP server...");
        smppServer.start();
        logger.info("SMPP server started");

        System.out.println("Press any key to stop server");
        System.in.read();

        logger.info("Stopping SMPP server...");
        smppServer.stop();
        logger.info("SMPP server stopped");

        logger.info("Server counters: {}", smppServer.getCounters());
    }

    // Handler for SMPP server events
    public static class DefaultSmppServerHandler implements SmppServerHandler {

        @Override
        public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, final BaseBind bindRequest) throws SmppProcessingException {
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
            if (session.hasCounters()) {
                logger.info("Final session rx-submitSM: {}", session.getCounters().getRxSubmitSM());
            }
            session.destroy();
        }
    }

    // Handler for individual SMPP session requests
    public static class TestSmppSessionHandler extends DefaultSmppSessionHandler {
        private final WeakReference<SmppSession> sessionRef;

        public TestSmppSessionHandler(SmppSession session) {
            this.sessionRef = new WeakReference<>(session);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            // Check if the request is a SubmitSm (common for sending messages)
            if (pduRequest instanceof SubmitSm submitSm) {
                // Extract message content
                String messageContent = CharsetUtil.decode(submitSm.getShortMessage(), CharsetUtil.CHARSET_ISO_8859_1);
                logger.info("Message received from client: {}", messageContent);
            }
            SmppSession session = sessionRef.get();
            logger.info("Session received: {}", session);
            return pduRequest.createResponse();  // Send basic response for received requests
        }
    }
}
