package org.alpha.clients;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.Address;
import org.alpha.utils.PropertiesLoader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmppClient {

    // Logger instance for logging events and errors
    private static final Logger logger = LoggerFactory.getLogger(SmppClient.class);

    public static void main(String[] args) throws Exception {

        // Executor service for managing concurrent tasks using lightweight virtual threads
        ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

        // ScheduledExecutorService for handling tasks with a delay (monitoring tasks)
        ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();

        // Creating an instance of the SmppClient with configured executors
        DefaultSmppClient clientBootstrap = new DefaultSmppClient(executor, 1, monitorExecutor);

        // Creating a custom session handler to process the SMPP messages
        DefaultSmppSessionHandler sessionHandler = new ClientSmppSessionHandler();
        // set properties_ config file name
        PropertiesLoader.init("application.properties");
        // Setting up the configuration for the SMPP session
        SmppSessionConfiguration config = new SmppSessionConfiguration();
        config.setWindowSize(1);  // Window size (how many requests to send before expecting a response)
        config.setName("client.alpha.000");  // Client name for identification
        config.setType(SmppBindType.TRANSCEIVER);  // Set the bind type (transceiver)
        config.setHost(PropertiesLoader.properties.clientHost);  // SMPP server host
        config.setPort(PropertiesLoader.properties.clientPort);  // SMPP server port
        config.setConnectTimeout(PropertiesLoader.properties.clientConnectTimeout);  // Connection timeout
        config.setSystemId(PropertiesLoader.properties.clientSystemId);  // System ID for authentication
        config.setPassword(PropertiesLoader.properties.clientPassword);  // Password for authentication
        config.getLoggingOptions().setLogBytes(true);  // Enable byte logging for debugging
        config.setRequestExpiryTimeout(PropertiesLoader.properties.clientRequestExpiryTimeout);  // Request expiry timeout
        config.setWindowMonitorInterval(PropertiesLoader.properties.clientWindowMonitorInterval);  // Window monitoring interval
        config.setCountersEnabled(true);  // Enable counters for tracking message statistics

        SmppSession session0 = null;

        try {
            // Establishing the SMPP session by binding the client to the server
            session0 = clientBootstrap.bind(config, sessionHandler);

            // Waiting for user input to send the first enquireLink message
            System.out.println("Press any key to send enquireLink #1");
            System.in.read();

            // Synchronous enquireLink request - sends a request and waits for a response
            EnquireLinkResp enquireLinkResp1 = session0.enquireLink(new EnquireLink(), 10000);
            logger.info("enquire_link_resp #1: commandStatus [" + enquireLinkResp1.getCommandStatus() + "=" + enquireLinkResp1.getResultMessage() + "]");

            // Waiting for user input to send the second enquireLink message
            System.out.println("Press any key to send enquireLink #2");
            System.in.read();

            // Asynchronous enquireLink request - sends the request and waits asynchronously for a response
            WindowFuture<Integer, PduRequest, PduResponse> future0 = session0.sendRequestPdu(new EnquireLink(), 10000, true);
            if (!future0.await()) {
                logger.error("Failed to receive enquire_link_resp within specified time");
            } else if (future0.isSuccess()) {
                EnquireLinkResp enquireLinkResp2 = (EnquireLinkResp) future0.getResponse();
                logger.info("enquire_link_resp #2: commandStatus [" + enquireLinkResp2.getCommandStatus() + "=" + enquireLinkResp2.getResultMessage() + "]");
            } else {
                logger.error("Failed to properly receive enquire_link_resp: " + future0.getCause());
            }

            // Waiting for user input to send the first submitSM message
            System.out.println("Press any key to send submit #1");
            System.in.read();

            // Sample text message to be sent
            String text160 = "Hello , world";
            byte[] textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_UTF_8);  // Encoding the message to bytes

            SubmitSm submit0 = new SubmitSm();
            submit0.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);  // Request a delivery receipt

            // Setting source and destination address for the SMS
            submit0.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
            submit0.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
            submit0.setShortMessage(textBytes);  // Adding the message bytes

            // Sending the SubmitSm message (SMS submission request)
            SubmitSmResp submitResp = session0.submit(submit0, 10000);

            // Logging the size of the send window
            logger.info("sendWindow.size: {}", session0.getSendWindow().getSize());

            // Waiting for user input to unbind the session
            System.out.println("Press any key to unbind and close sessions");
            System.in.read();

            // Unbinding the session and closing it
            session0.unbind(5000);
        } catch (Exception e) {
            logger.error("Error occurred", e);
        }

        // Cleaning up the session and logging final statistics if the session exists
        if (session0 != null) {
            logger.info("Cleaning up session... (final counters)");
            if (session0.hasCounters()) {
                // Logging the transaction counters for various message types
                logger.info("tx-enquireLink: {}", session0.getCounters().getTxEnquireLink());
                logger.info("tx-submitSM: {}", session0.getCounters().getTxSubmitSM());
                logger.info("tx-deliverSM: {}", session0.getCounters().getTxDeliverSM());
                logger.info("tx-dataSM: {}", session0.getCounters().getTxDataSM());
                logger.info("rx-enquireLink: {}", session0.getCounters().getRxEnquireLink());
                logger.info("rx-submitSM: {}", session0.getCounters().getRxSubmitSM());
                logger.info("rx-deliverSM: {}", session0.getCounters().getRxDeliverSM());
                logger.info("rx-dataSM: {}", session0.getCounters().getRxDataSM());
            }

            // Destroying the session after use
            session0.destroy();
        }

        // Shutting down client bootstrap and executor services
        logger.info("Shutting down client bootstrap and executors...");
        clientBootstrap.destroy();
        executor.shutdown();
        monitorExecutor.shutdown();

        logger.info("Done. Exiting");
    }

    /**
     * Custom session handler to process received and expired PDU requests.
     */
    public static class ClientSmppSessionHandler extends DefaultSmppSessionHandler {

        public ClientSmppSessionHandler() {
            super(logger);  // Passing the logger to the parent class
        }

        @Override
        public void firePduRequestExpired(PduRequest pduRequest) {
            // This method is invoked when a PDU request has expired
            logger.warn("PDU request expired: {}", pduRequest);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            // This method is invoked when a PDU request is received
            PduResponse response = pduRequest.createResponse();  // Creating a response for the PDU request

            try {
                if (pduRequest instanceof DeliverSm deliverSm) {
                    // If the PDU request is a DeliverSm (SMS delivery), decode and log the message
                    byte[] shortMessage = deliverSm.getShortMessage();
                    String messageContent = CharsetUtil.decode(shortMessage, CharsetUtil.CHARSET_ISO_8859_1);
                    System.out.println("========================================");
                    System.out.println("Received message from server: " + messageContent);
                    System.out.println("========================================");
                    logger.info("Received message from server: {}", messageContent);
                }
            } catch (Exception e) {
                // Handle any errors during the processing of the received PDU
                logger.error("Error processing received PDU", e);
            }

            return response;  // Returning the response for the PDU request
        }
    }
}
