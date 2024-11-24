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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmppClientDLU {
    private static final Logger logger = LoggerFactory.getLogger(SmppClientDLU.class);

    public static void main(String[] args) throws Exception {
        ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();

        DefaultSmppClient clientBootstrap = new DefaultSmppClient(executor, 1, monitorExecutor);

        ClientSmppSessionHandler sessionHandler = new ClientSmppSessionHandler();
        PropertiesLoader.init("application0.properties");

        SmppSessionConfiguration config = new SmppSessionConfiguration();
        config.setWindowSize(1);
        config.setName("client.alpha.000");
        config.setType(SmppBindType.TRANSCEIVER);
        config.setHost(PropertiesLoader.properties.clientHost);
        config.setPort(3000);
        config.setConnectTimeout(PropertiesLoader.properties.clientConnectTimeout);
        config.setSystemId(PropertiesLoader.properties.clientSystemId);
        config.setPassword(PropertiesLoader.properties.clientPassword);
        config.getLoggingOptions().setLogBytes(true);
        config.setRequestExpiryTimeout(PropertiesLoader.properties.clientRequestExpiryTimeout);
        config.setWindowMonitorInterval(PropertiesLoader.properties.clientWindowMonitorInterval);
        config.setCountersEnabled(true);

        SmppSession session0 = null;

        try {
            session0 = clientBootstrap.bind(config, sessionHandler);

            System.out.println("Press any key to send submit #1");
            System.in.read();

            String text160 = "Hello , world";
            byte[] textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_UTF_8);

            SubmitSm submit0 = new SubmitSm();
            // Request delivery receipt
            submit0.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);

            submit0.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
            submit0.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
            submit0.setShortMessage(textBytes);

            SubmitSmResp submitResp = session0.submit(submit0, 10000);

            System.out.println("Press any key to unbind and close sessions");
            System.in.read();

            session0.unbind(5000);
        } catch (Exception e) {
            logger.error("Error occurred", e);
        }

        // Rest of the existing cleanup code remains the same
    }

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

            try {
                if (pduRequest instanceof DeliverSm deliverSm) {
                    byte[] shortMessage = deliverSm.getShortMessage();
                    String messageContent = CharsetUtil.decode(shortMessage, CharsetUtil.CHARSET_ISO_8859_1);

                    // Check if this is a delivery report
                    if ((deliverSm.getEsmClass() & 0x04) == 0x04) {
                        Map<String, String> dlrDetails = parseDlr(messageContent);

                        System.out.println("========================================");
                        System.out.println("Delivery Report Received:");
                        dlrDetails.forEach((key, value) ->
                                System.out.println(key + ": " + value)
                        );
                        System.out.println("========================================");

                        logger.info("Parsed Delivery Report: {}", dlrDetails);
                    } else {
                        // Regular message handling
                        System.out.println("Received message: " + messageContent);
                        logger.info("Received message: {}", messageContent);
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing received PDU", e);
            }

            return response;
        }

        // Parse DLR string into a map of key-value pairs
        private Map<String, String> parseDlr(String dlrMessage) {
            Map<String, String> dlrDetails = new HashMap<>();
            String[] parts = dlrMessage.split(" ");

            for (String part : parts) {
                String[] keyValue = part.split(":");
                if (keyValue.length == 2) {
                    dlrDetails.put(keyValue[0], keyValue[1]);
                }
            }

            return dlrDetails;
        }
    }
}