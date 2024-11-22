package org.alpha.server;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.SmppProcessingException;
import org.alpha.utils.DeliveryReport;
import org.alpha.utils.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.Executors;

public class SmppServerAppDLU {
    private static final Logger logger = LoggerFactory.getLogger(SmppServerAppDLU.class);

    public static void main(String[] args) throws Exception {
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        SmppServerConfiguration configuration = new SmppServerConfiguration();
        PropertiesLoader.init("application1.properties");
        configuration.setPort(PropertiesLoader.properties.port);
        configuration.setMaxConnectionSize(PropertiesLoader.properties.maxConnectionSize);
        configuration.setDefaultRequestExpiryTimeout(PropertiesLoader.properties.defaultRequestExpiryTimeout);
        configuration.setDefaultWindowMonitorInterval(PropertiesLoader.properties.defaultWindowMonitorInterval);
        configuration.setDefaultWindowSize(PropertiesLoader.properties.defaultWindowSize);
        configuration.setDefaultWindowWaitTimeout(PropertiesLoader.properties.defaultWindowWaitTimeout);
        configuration.setNonBlockingSocketsEnabled(PropertiesLoader.properties.nonBlockingSocketsEnabled);
        configuration.setDefaultSessionCountersEnabled(PropertiesLoader.properties.sessionCountersEnabled);
        configuration.setJmxEnabled(PropertiesLoader.properties.jmxEnabled);

        DefaultSmppServer smppServer = new DefaultSmppServer(configuration, new DefaultSmppServerHandler(), executor);

        logger.info("Starting SMPP server-1 ... on port " + PropertiesLoader.properties.port);
        smppServer.start();
        logger.info("SMPP server started");

        System.out.println("Press any key to stop server");
        System.in.read();

        logger.info("Stopping SMPP server...");
        smppServer.stop();
        logger.info("SMPP server stopped");
        logger.info("Server counters: {}", smppServer.getCounters());
    }

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

    public static class TestSmppSessionHandler extends DefaultSmppSessionHandler {
        private final WeakReference<SmppSession> sessionRef;

        public TestSmppSessionHandler(SmppSession session) {
            this.sessionRef = new WeakReference<>(session);
        }

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            SmppSession session = sessionRef.get();

            if (pduRequest instanceof SubmitSm submitSm) {
                // Generate a unique message ID
                String messageId = UUID.randomUUID().toString().substring(0, 8);

                // Create a Delivery Report object
                DeliveryReport dlr = new DeliveryReport(messageId, 1,  // submitted parts
                        1,  // delivered parts
                        LocalDateTime.now(),  // submit date
                        LocalDateTime.now(),  // done date
                        DeliveryReport.DeliveryStatus.DELIVRD,  // status
                        0   // error code
                );

                if (session != null) {
                    try {
                        // Send initial response message
                        DeliverSm deliver = new DeliverSm();
                        deliver.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
                        deliver.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
                        deliver.setShortMessage(CharsetUtil.encode("Server-1 : Bye , World", CharsetUtil.CHARSET_ISO_8859_1));
                        session.sendRequestPdu(deliver, 10000, false);

                        // Send Delivery Report as serialized object
                        DeliverSm dlrMessage = new DeliverSm();
                        dlrMessage.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
                        dlrMessage.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));

                        // Serialize DLR object to string for transmission
                        String serializedDlr = convertDlrToString(dlr);
                        dlrMessage.setShortMessage(CharsetUtil.encode(serializedDlr, CharsetUtil.CHARSET_ISO_8859_1));

                        // Set esm_class to indicate it's a delivery receipt
                        dlrMessage.setEsmClass((byte) (0x04 | 0x00));

                        session.sendRequestPdu(dlrMessage, 10000, false);

                        logger.info("Delivery Report sent: {}", dlr);
                    } catch (Exception e) {
                        logger.error("Error sending response", e);
                    }
                }
            }

            return pduRequest.createResponse();
        }

        // Convert DLR object to a serializable string format
        private String convertDlrToString(DeliveryReport dlr) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");

            return String.format("id:%s sub:%03d dlvrd:%03d submit date:%s done date:%s stat:%s err:%03d", dlr.getMessageId(), dlr.getSubmittedParts(), dlr.getDeliveredParts(), dlr.getSubmitDate().format(formatter), dlr.getDoneDate().format(formatter), dlr.getStatus().getDescription(), dlr.getErrorCode());
        }


    }
}
