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
import com.cloudhopper.smpp.tlv.Tlv;
import org.alpha.utils.PropertiesLoader;

import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchSmppClient {
    private static final Logger logger = LoggerFactory.getLogger(BatchSmppClient.class);
    private static final int BATCH_SIZE = 1000; // Number of messages to send in a batch
    private static final int CONCURRENT_REQUESTS = 100; // Number of concurrent requests to handle
    private static final int REQUEST_TIMEOUT = 100000; // Timeout for each message submission (in milliseconds)
    private static final int MAX_SHORT_MESSAGE_LENGTH = 255; // Maximum length for a short message in SMPP

    public static void main(String[] args) throws Exception {
        // Executor for task submission
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Scheduled executor for monitoring tasks
        ScheduledExecutorService monitorExecutor = Executors.newScheduledThreadPool(1);

        // Create SMPP client with the specified configuration
        DefaultSmppClient clientBootstrap = new DefaultSmppClient(executor, CONCURRENT_REQUESTS, monitorExecutor);

        // Create and configure SMPP session
        SmppSessionConfiguration config = createSessionConfig();
        DefaultSmppSessionHandler sessionHandler = new BatchClientSmppSessionHandler();

        SmppSession session = null;
        List<Future<SubmitSmResp>> futures = new ArrayList<>();

        try {
            // Bind to SMPP server
            session = clientBootstrap.bind(config, sessionHandler);
            logger.info("SMPP session established successfully");

            // Send batch of messages
            sendBatchMessages(session, futures);

            // Process responses for the sent messages
            processResponses(futures);
        } catch (Exception e) {
            logger.error("Error in batch processing", e);
        } finally {
            // Cleanup resources
            cleanup(session, clientBootstrap, executor, monitorExecutor);
        }
    }

    private static SmppSessionConfiguration createSessionConfig() {
        // Initialize properties loader with configuration file
        PropertiesLoader.init("application0.properties");

        // Set up SMPP session configuration
        SmppSessionConfiguration config = new SmppSessionConfiguration();
        config.setWindowSize(CONCURRENT_REQUESTS); // Set window size (max concurrent requests)
        config.setName("batch.client.alpha"); // Name for the client
        config.setType(SmppBindType.TRANSCEIVER); // Bind type (bi-directional communication)
        config.setHost(PropertiesLoader.properties.clientHost); // SMPP server host
        config.setPort(PropertiesLoader.properties.clientPort); // SMPP server port
        config.setConnectTimeout(PropertiesLoader.properties.clientConnectTimeout); // Connection timeout
        config.setSystemId(PropertiesLoader.properties.clientSystemId); // System ID for SMPP session
        config.setPassword(PropertiesLoader.properties.clientPassword); // Password for SMPP session
        config.setRequestExpiryTimeout(PropertiesLoader.properties.clientRequestExpiryTimeout); // Request expiry timeout
        config.setWindowMonitorInterval(PropertiesLoader.properties.clientWindowMonitorInterval); // Window monitor interval
        config.setCountersEnabled(true); // Enable counters for message tracking
        return config;
    }

    private static void sendBatchMessages(SmppSession session, List<Future<SubmitSmResp>> futures) {
        // Generate message template
        String messageTemplate = generateMessageTemplate();

        // Executor for handling message submissions concurrently
        ExecutorService submissionExecutor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        // Loop to send the batch of messages
        for (int i = 0; i < BATCH_SIZE; i++) {
            final int messageIndex = i;
            // Submit message sending task to executor
            Future<SubmitSmResp> future = submissionExecutor.submit(() -> {
                try {
                    // Create a unique message for each iteration
                    String messageText = String.format("%s - Message #%d - ID: %s",
                            messageTemplate, messageIndex, UUID.randomUUID().toString());

                    // Send the message and return the response
                    return sendMessage(session, messageText);
                } catch (Exception e) {
                    logger.error("Error sending message " + messageIndex, e);
                    throw e;
                }
            });
            futures.add(future); // Add future to list for later processing
        }

        // Shutdown executor after submitting all tasks
        submissionExecutor.shutdown();
        try {
            // Wait for all tasks to finish (with a timeout)
            submissionExecutor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.error("Submission executor interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private static SubmitSmResp sendMessage(SmppSession session, String messageText) throws Exception {
        // Encode message text to byte array using UTF-8
        byte[] textBytes = CharsetUtil.encode(messageText, CharsetUtil.CHARSET_UTF_8);

        // Create a new SubmitSm (Submit Short Message) PDU
        SubmitSm submit = new SubmitSm();
        submit.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED); // Request delivery receipt
        submit.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404")); // Source address (sender)
        submit.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205")); // Destination address (receiver)

        // Handle message length appropriately based on short message limit
        if (textBytes.length <= MAX_SHORT_MESSAGE_LENGTH) {
            // For short messages, set the message content directly
            submit.setShortMessage(textBytes);
        } else {
            // For longer messages, set payload and clear shortMessage
            submit.setShortMessage(new byte[0]);
            submit.addOptionalParameter(new Tlv(SmppConstants.TAG_MESSAGE_PAYLOAD, textBytes));
        }

        // Set data coding to indicate UTF-8 encoding
        submit.setDataCoding((byte) 0x08);

        // Submit the message and return the response
        return session.submit(submit, REQUEST_TIMEOUT);
    }

    private static String generateMessageTemplate() {
        // Template for the batch message
        return "This is a test message for batch processing. "
                + "Testing long message handling with proper payload configuration. "
                + "Each message will be uniquely identified.";
    }

    private static void processResponses(List<Future<SubmitSmResp>> futures) {
        int successful = 0; // Counter for successful messages
        int failed = 0; // Counter for failed messages

        // Loop through the responses
        for (int i = 0; i < futures.size(); i++) {
            try {
                // Get the response for each future (message submission)
                SubmitSmResp resp = futures.get(i).get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);

                // Check if the submission was successful
                if (resp.getCommandStatus() == SmppConstants.STATUS_OK) {
                    successful++;
                    // Log every 100 successful messages
                    if (successful % 100 == 0) {
                        logger.info("Successfully sent {} messages", successful);
                    }
                } else {
                    failed++;
                    // Log failure with status code
                    logger.warn("Message {} failed with status: {}", i, resp.getCommandStatus());
                }
            } catch (Exception e) {
                failed++;
                // Log failure in response retrieval
                logger.error("Failed to get response for message " + i, e);
            }
        }

        // Log summary of batch processing
        logger.info("Batch processing completed. Successful: {}, Failed: {}", successful, failed);
    }

    private static void cleanup(SmppSession session, DefaultSmppClient clientBootstrap,
                                ExecutorService executor, ScheduledExecutorService monitorExecutor) {
        // Cleanup session resources
        if (session != null) {
            try {
                // Unbind the session (close the connection)
                session.unbind(5000);

                // Log session statistics if available
                if (session.hasCounters()) {
                    logger.info("Final Statistics:");
                    logger.info("Submitted Messages: {}", session.getCounters().getTxSubmitSM());
                    // Uncomment for successful responses count if needed
                    // logger.info("Successful Responses: {}", session.getCounters().getRxDataSM());
                }
                session.destroy(); // Destroy session after unbinding
            } catch (Exception e) {
                logger.error("Error during session cleanup", e);
            }
        }

        // Destroy the client bootstrap and shut down executors
        clientBootstrap.destroy();
        executor.shutdown();
        monitorExecutor.shutdown();
        logger.info("Cleanup completed");
    }

    // Custom handler for SMPP session events
    private static class BatchClientSmppSessionHandler extends DefaultSmppSessionHandler {
        public BatchClientSmppSessionHandler() {
            super(logger);
        }

        // Handle expired PDU requests
        @Override
        public void firePduRequestExpired(PduRequest pduRequest) {
            logger.warn("PDU request expired: {}", pduRequest);
        }
    }
}
