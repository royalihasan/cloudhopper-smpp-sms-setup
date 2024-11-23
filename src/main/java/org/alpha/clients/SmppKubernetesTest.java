package org.alpha.clients;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.Address;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmppKubernetesTest {
    private static final Logger logger = LoggerFactory.getLogger(SmppKubernetesTest.class);

    public static void main(String[] args) {

        String lbIp = "127.0.0.1";

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        DefaultSmppClient clientBootstrap = new DefaultSmppClient(executor, 1, monitorExecutor);
        DefaultSmppSessionHandler sessionHandler = new ClientSmppSessionHandler();

        SmppSessionConfiguration config = new SmppSessionConfiguration();
        config.setWindowSize(1);
        config.setName("k8s.test.client");
        config.setType(SmppBindType.TRANSCEIVER);
        config.setHost(lbIp);  // Using LoadBalancer IP
        config.setPort(2775);
        config.setConnectTimeout(10000);
        config.setSystemId("your_system_id");  // Use your system ID
        config.setPassword("your_password");    // Use your password
        config.setRequestExpiryTimeout(30000);
        config.setWindowMonitorInterval(15000);
        config.setCountersEnabled(true);

        SmppSession session = null;

        try {
            logger.info("Connecting to SMPP server at {}:2775", lbIp);
            session = clientBootstrap.bind(config, sessionHandler);
            logger.info("Successfully connected to SMPP server!");

            // Test 1: EnquireLink
            logger.info("Sending EnquireLink...");
            EnquireLinkResp enquireLinkResp = session.enquireLink(new EnquireLink(), 10000);
            logger.info("EnquireLink Response: status={}, message={}",
                    enquireLinkResp.getCommandStatus(),
                    enquireLinkResp.getResultMessage());

            // Test 2: Submit SMS
            logger.info("Sending test SMS...");
            String testMessage = "Test message from K8s SMPP client";
            byte[] textBytes = CharsetUtil.encode(testMessage, CharsetUtil.CHARSET_UTF_8);

            SubmitSm submit = new SubmitSm();
            submit.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
            submit.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "Test"));
            submit.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "1234567890"));
            submit.setShortMessage(textBytes);

            SubmitSmResp submitResp = session.submit(submit, 10000);
            logger.info("SMS Submit Response: status={}, messageId={}",
                    submitResp.getCommandStatus(),
                    submitResp.getMessageId());

            // Print session stats
            if (session.hasCounters()) {
                logger.info("Session Statistics:");
                logger.info("TX EnquireLink: {}", session.getCounters().getTxEnquireLink());
                logger.info("RX EnquireLink: {}", session.getCounters().getRxEnquireLink());
                logger.info("TX SubmitSM: {}", session.getCounters().getTxSubmitSM());
                logger.info("RX SubmitSM: {}", session.getCounters().getRxSubmitSM());
            }

            // Clean disconnect
            logger.info("Unbinding session...");
            session.unbind(5000);

        } catch (Exception e) {
            logger.error("Error during SMPP test", e);
        } finally {
            if (session != null) {
                session.destroy();
            }
            clientBootstrap.destroy();
            executor.shutdown();
            monitorExecutor.shutdown();
        }
    }

    public static class ClientSmppSessionHandler extends DefaultSmppSessionHandler {
        public ClientSmppSessionHandler() {
            super(LoggerFactory.getLogger(ClientSmppSessionHandler.class));
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            PduResponse response = pduRequest.createResponse();

            if (pduRequest instanceof DeliverSm deliverSm) {
                try {
                    byte[] shortMessage = deliverSm.getShortMessage();
                    String messageContent = CharsetUtil.decode(shortMessage, CharsetUtil.CHARSET_UTF_8);
                    logger.info("Received delivery: {}", messageContent);
                } catch (Exception e) {
                    logger.error("Error processing delivery", e);
                }
            }

            return response;
        }
    }
}