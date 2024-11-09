package org.alpha.clients;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.Address;
import org.alpha.utils.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SmppClient_01 {
    private static final Logger logger = LoggerFactory.getLogger(SmppClient.class);

    static public void main(String[] args) throws Exception {
        // Executor and monitor setup
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();

        DefaultSmppClient clientBootstrap = new DefaultSmppClient(executor, 1, monitorExecutor);

        // Custom session handler
        DefaultSmppSessionHandler sessionHandler = new ClientSmppSessionHandler();

        // Configuration for session
        SmppSessionConfiguration config = new SmppSessionConfiguration();
        config.setWindowSize(1);
        config.setName("client.beta.001");
        config.setType(SmppBindType.TRANSCEIVER);
        config.setHost(PropertiesLoader.properties.clientHost);
        config.setPort(PropertiesLoader.properties.clientPort);
        config.setConnectTimeout(PropertiesLoader.properties.clientConnectTimeout);
        config.setSystemId(PropertiesLoader.properties.clientSystemId);
        config.setPassword(PropertiesLoader.properties.clientPassword);
        config.getLoggingOptions().setLogBytes(true);
        config.setRequestExpiryTimeout(PropertiesLoader.properties.clientRequestExpiryTimeout);
        config.setWindowMonitorInterval(PropertiesLoader.properties.clientWindowMonitorInterval);
        config.setCountersEnabled(true);

        SmppSession session0 = null;

        try {
            // Bind session
            session0 = clientBootstrap.bind(config, sessionHandler);

            // Press to send an EnquireLink
            System.out.println("Press any key to send enquireLink #1");
            System.in.read();

            // Send enquireLink and handle response
            EnquireLinkResp enquireLinkResp1 = session0.enquireLink(new EnquireLink(), 10000);
            logger.info("enquire_link_resp #1: commandStatus [" + enquireLinkResp1.getCommandStatus() + "=" + enquireLinkResp1.getResultMessage() + "]");

            // Press to send another EnquireLink
            System.out.println("Press any key to send enquireLink #2");
            System.in.read();

            // Asynchronous enquireLink
            WindowFuture<Integer, PduRequest, PduResponse> future0 = session0.sendRequestPdu(new EnquireLink(), 10000, true);
            if (!future0.await()) {
                logger.error("Failed to receive enquire_link_resp within specified time");
            } else if (future0.isSuccess()) {
                EnquireLinkResp enquireLinkResp2 = (EnquireLinkResp) future0.getResponse();
                logger.info("enquire_link_resp #2: commandStatus [" + enquireLinkResp2.getCommandStatus() + "=" + enquireLinkResp2.getResultMessage() + "]");
            } else {
                logger.error("Failed to properly receive enquire_link_resp: " + future0.getCause());
            }

            // Press to send SubmitSm (SMS)
            System.out.println("Press any key to send submit #1");
            System.in.read();

            // New text messages
            sendMessage(session0, "Message 1 - Testing SMPP Client 1", "40404", "44555519205");
            sendMessage(session0, "Message 2 - Testing SMPP Client 2", "50505", "44555519206");
            sendMessage(session0, "Message 3 - SMPP Client 3 in Action", "60606", "44555519207");

            // Unbind and clean up
            System.out.println("Press any key to unbind and close session");
            System.in.read();
            session0.unbind(5000);
        } catch (Exception e) {
            logger.error("Error occurred", e);
        }

        if (session0 != null) {
            logger.info("Cleaning up session... (final counters)");
            if (session0.hasCounters()) {
                logger.info("tx-enquireLink: {}", session0.getCounters().getTxEnquireLink());
                logger.info("tx-submitSM: {}", session0.getCounters().getTxSubmitSM());
            }
            session0.destroy();
        }

        // Shutdown
        logger.info("Shutting down client bootstrap and executors...");
        clientBootstrap.destroy();
        executor.shutdown();
        monitorExecutor.shutdown();

        logger.info("Done. Exiting");
    }

    /**
     * Helper method to send messages with different content and addresses
     */
    private static void sendMessage(SmppSession session, String messageContent, String source, String destination) throws Exception {
        byte[] textBytes = CharsetUtil.encode(messageContent, CharsetUtil.CHARSET_UTF_8);

        SubmitSm submitSm = new SubmitSm();
        submitSm.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
        submitSm.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, source));
        submitSm.setDestAddress(new Address((byte) 0x01, (byte) 0x01, destination));
        submitSm.setShortMessage(textBytes);

        SubmitSmResp submitResp = session.submit(submitSm, 10000);

        logger.info("sendWindow.size: {}", session.getSendWindow().getSize());
    }

    /**
     * Custom session handler for processing PDU requests and responses.
     */
    public static class ClientSmppSessionHandler extends DefaultSmppSessionHandler {

        public ClientSmppSessionHandler() {
            super(logger);
        }

        @Override
        public void firePduRequestExpired(PduRequest pduRequest) {
            logger.warn("PDU request expired: {}", pduRequest);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            PduResponse response = pduRequest.createResponse();

            // Handle the message if it's a DeliverSm
            if (pduRequest instanceof DeliverSm deliverSm) {
                byte[] shortMessage = deliverSm.getShortMessage();
                String messageContent = CharsetUtil.decode(shortMessage, CharsetUtil.CHARSET_ISO_8859_1);
                logger.info("Received message from server: {}", messageContent);
            }

            return response;
        }
    }
}
