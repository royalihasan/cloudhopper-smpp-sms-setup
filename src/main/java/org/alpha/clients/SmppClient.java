package org.alpha.clients;


import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.commons.util.windowing.WindowFuture;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SmppClient {
    private static final Logger logger = LoggerFactory.getLogger(SmppClient.class);

    static public void main(String[] args) throws Exception {

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        // to enable automatic expiration of requests, a second scheduled executor
        // is required which is what a monitor task will be executed with - this
        // is probably a thread pool that can be shared with between all client bootstraps
        ScheduledThreadPoolExecutor monitorExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, new ThreadFactory() {
            private AtomicInteger sequence = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("SmppClientSessionWindowMonitorPool-" + sequence.getAndIncrement());
                return t;
            }
        });


        DefaultSmppClient clientBootstrap = new DefaultSmppClient(Executors.newCachedThreadPool(), 1, monitorExecutor);

        //
        // setup configuration for a client session
        //
        DefaultSmppSessionHandler sessionHandler = new ClientSmppSessionHandler();

        SmppSessionConfiguration config0 = new SmppSessionConfiguration();
        config0.setWindowSize(1);
        config0.setName("Tester.Session.0");
        config0.setType(SmppBindType.TRANSCEIVER);
        config0.setHost("127.0.0.1");
        config0.setPort(2776);
        config0.setConnectTimeout(10000);
        config0.setSystemId("1234567890");
        config0.setPassword("password");
        config0.getLoggingOptions().setLogBytes(true);
        // to enable monitoring (request expiration)
        config0.setRequestExpiryTimeout(30000);
        config0.setWindowMonitorInterval(15000);
        config0.setCountersEnabled(true);

        //
        // create session, enquire link, submit an sms, close session
        //
        SmppSession session0 = null;

        try {
            // create session a session by having the bootstrap connect a
            // socket, send the bind request, and wait for a bind response
            session0 = clientBootstrap.bind(config0, sessionHandler);

            System.out.println("Press any key to send enquireLink #1");
            System.in.read();

            // demo of a "synchronous" enquireLink call - send it and wait for a response
            EnquireLinkResp enquireLinkResp1 = session0.enquireLink(new EnquireLink(), 10000);
            logger.info("enquire_link_resp #1: commandStatus [" + enquireLinkResp1.getCommandStatus() + "=" + enquireLinkResp1.getResultMessage() + "]");

            System.out.println("Press any key to send enquireLink #2");
            System.in.read();

            // demo of an "asynchronous" enquireLink call - send it, get a future,
            // and then optionally choose to pick when we wait for it
            WindowFuture<Integer, PduRequest, PduResponse> future0 = session0.sendRequestPdu(new EnquireLink(), 10000, true);
            if (!future0.await()) {
                logger.error("Failed to receive enquire_link_resp within specified time");
            } else if (future0.isSuccess()) {
                EnquireLinkResp enquireLinkResp2 = (EnquireLinkResp) future0.getResponse();
                logger.info("enquire_link_resp #2: commandStatus [" + enquireLinkResp2.getCommandStatus() + "=" + enquireLinkResp2.getResultMessage() + "]");
            } else {
                logger.error("Failed to properly receive enquire_link_resp: " + future0.getCause());
            }

            System.out.println("Press any key to send submit #1");
            System.in.read();

            String text160 = "\u20AC Hello , World";
            byte[] textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_UTF_8);

            SubmitSm submit0 = new SubmitSm();

            // add delivery receipt
            submit0.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);

            submit0.setSourceAddress(new Address((byte) 0x03, (byte) 0x00, "40404"));
            submit0.setDestAddress(new Address((byte) 0x01, (byte) 0x01, "44555519205"));
            submit0.setShortMessage(textBytes);

            SubmitSmResp submitResp = session0.submit(submit0, 10000);


            logger.info("sendWindow.size: {}", session0.getSendWindow().getSize());

            System.out.println("Press any key to unbind and close sessions");
            System.in.read();

            session0.unbind(5000);
        } catch (Exception e) {
            logger.error("", e);
        }

        if (session0 != null) {
            logger.info("Cleaning up session... (final counters)");
            if (session0.hasCounters()) {
                logger.info("tx-enquireLink: {}", session0.getCounters().getTxEnquireLink());
                logger.info("tx-submitSM: {}", session0.getCounters().getTxSubmitSM());
                logger.info("tx-deliverSM: {}", session0.getCounters().getTxDeliverSM());
                logger.info("tx-dataSM: {}", session0.getCounters().getTxDataSM());
                logger.info("rx-enquireLink: {}", session0.getCounters().getRxEnquireLink());
                logger.info("rx-submitSM: {}", session0.getCounters().getRxSubmitSM());
                logger.info("rx-deliverSM: {}", session0.getCounters().getRxDeliverSM());
                logger.info("rx-dataSM: {}", session0.getCounters().getRxDataSM());
            }

            session0.destroy();
            // alternatively, could call close(), get outstanding requests from
            // the sendWindow (if we wanted to retry them later), then call shutdown()
        }

        // this is required to not causing server to hang from non-daemon threads
        // this also makes sure all open Channels are closed to I *think*
        logger.info("Shutting down client bootstrap and executors...");
        clientBootstrap.destroy();
        executor.shutdownNow();
        monitorExecutor.shutdownNow();

        logger.info("Done. Exiting");
    }

    /**
     * Could either implement SmppSessionHandler or only override select methods
     * by extending a DefaultSmppSessionHandler.
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

            // do any logic here

            return response;
        }

    }

}