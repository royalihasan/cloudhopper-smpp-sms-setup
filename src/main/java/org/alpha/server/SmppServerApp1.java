package org.alpha.server;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.SmppProcessingException;
import org.alpha.utils.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;

public class SmppServerApp1 {
    private static final Logger logger = LoggerFactory.getLogger(SmppServerApp1.class);

    /**
     * The main method to start the SMPP server and handle its lifecycle.
     *
     * @param args Command-line arguments (not used in this case)
     * @throws Exception If any error occurs during SMPP server startup or operation
     */
    public static void main(String[] args) throws Exception {
        // Use virtual threads for better scalability and efficiency
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        // Configure SMPP server
        SmppServerConfiguration configuration = new SmppServerConfiguration();
        PropertiesLoader.init("application1.properties");
        // Setting up various SMPP server parameters
        configuration.setPort(PropertiesLoader.properties.port);  // Port to listen on
        configuration.setMaxConnectionSize(PropertiesLoader.properties.maxConnectionSize);  // Max concurrent connections
        configuration.setDefaultRequestExpiryTimeout(PropertiesLoader.properties.defaultRequestExpiryTimeout);  // Timeout for requests
        configuration.setDefaultWindowMonitorInterval(PropertiesLoader.properties.defaultWindowMonitorInterval);  // Monitor window interval
        configuration.setDefaultWindowSize(PropertiesLoader.properties.defaultWindowSize);  // Window size for connections
        configuration.setDefaultWindowWaitTimeout(PropertiesLoader.properties.defaultWindowWaitTimeout);  // Wait time for window operations
        configuration.setNonBlockingSocketsEnabled(PropertiesLoader.properties.nonBlockingSocketsEnabled);  // Enable non-blocking sockets
        configuration.setDefaultSessionCountersEnabled(PropertiesLoader.properties.sessionCountersEnabled);  // Enable session counters
        configuration.setJmxEnabled(PropertiesLoader.properties.jmxEnabled);  // Enable JMX monitoring

        // Initialize the SMPP server with the custom handler and the executor for threading
        DefaultSmppServer smppServer = new DefaultSmppServer(configuration, new DefaultSmppServerHandler(), executor);

        // Start the SMPP server and log the event
        logger.info("Starting SMPP server-1 ... on port " + PropertiesLoader.properties.port);
        smppServer.start();
        logger.info("SMPP server started");

        // Wait for the user to press any key to stop the server
        System.out.println("Press any key to stop server");
        System.in.read();

        // Stop the SMPP server and log the event
        logger.info("Stopping SMPP server...");
        smppServer.stop();
        logger.info("SMPP server stopped");

        // Log server counters for monitoring
        logger.info("Server counters: {}", smppServer.getCounters());
    }

    /**
     * Handler for SMPP server events like session creation, binding, and destruction.
     */
    public static class DefaultSmppServerHandler implements SmppServerHandler {

        /**
         * Handles an incoming session bind request. Sets the session name.
         *
         * @param sessionId            The unique session ID
         * @param sessionConfiguration The session configuration parameters
         * @param bindRequest          The bind request sent by the client
         * @throws SmppProcessingException If there is an error in processing the bind request
         */
        @Override
        public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, final BaseBind bindRequest) throws SmppProcessingException {
            // Set the session name based on the system ID
            sessionConfiguration.setName("Application.SMPP." + sessionConfiguration.getSystemId());
        }

        /**
         * Called when a new session is created. Initializes the session handler.
         *
         * @param sessionId            The unique session ID
         * @param session              The SMPP server session created
         * @param preparedBindResponse The bind response to send to the client
         * @throws SmppProcessingException If there is an error in session creation
         */
        @Override
        public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException {
            logger.info("Session created: {}", session);
            // Attach a custom session handler to manage the session
            session.serverReady(new TestSmppSessionHandler(session));
        }

        /**
         * Called when an existing session is destroyed. Cleans up session resources.
         *
         * @param sessionId The unique session ID
         * @param session   The SMPP server session that is being destroyed
         */
        @Override
        public void sessionDestroyed(Long sessionId, SmppServerSession session) {
            logger.info("Session destroyed: {}", session);
            // Log final session statistics
            if (session.hasCounters()) {
                logger.info("Final session rx-submitSM: {}", session.getCounters().getRxSubmitSM());
            }
            session.destroy();  // Destroy the session
        }
    }

    /**
     * Custom session handler to process and respond to PDU requests for each session.
     */
    public static class TestSmppSessionHandler extends DefaultSmppSessionHandler {
        private final WeakReference<SmppSession> sessionRef;

        /**
         * Constructor that takes a session reference.
         *
         * @param session The SMPP session associated with this handler
         */
        public TestSmppSessionHandler(SmppSession session) {
            this.sessionRef = new WeakReference<>(session);
        }

        /**
         * Handles the incoming PDU request (SubmitSm).
         * If the message content is "Hello, world", it sends a "Bye, World" message as a response.
         *
         * @param pduRequest The PDU request received from the client
         * @return The PDU response to be sent back to the client
         */
        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            SmppSession session = sessionRef.get();  // Get the session from the weak reference

            // Check if the PDU request is of type SubmitSm (short message submission)
            if (pduRequest instanceof SubmitSm submitSm) {
                // Extract the short message content from the request
                String messageContent = CharsetUtil.decode(submitSm.getShortMessage(), CharsetUtil.CHARSET_ISO_8859_1);
                logger.info("Message received from client: {}", messageContent);

                // If the message content is "Hello, world", send a response back
                if (session != null) {
                    try {
                        // Create a DeliverSm PDU to send a response back to the client
                        DeliverSm deliver = new DeliverSm();
                        deliver.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
                        deliver.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
                        deliver.setShortMessage(CharsetUtil.encode("Server-1 : Bye , World", CharsetUtil.CHARSET_ISO_8859_1));

                        // Send the response PDU to the client
                        session.sendRequestPdu(deliver, 10000, false);
                        logger.info("Server-1: Response sent to client: Bye , World");
                    } catch (Exception e) {
                        logger.error("Error sending response to client", e);
                    }
                }
            }

            return pduRequest.createResponse();  // Return the response PDU
        }
    }
}
