/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.jps.JamnPersonalServerApp.Config;
import org.isa.jps.comp.ChildProcessManager.CPComData;

/**
 * <pre>
 * The ChildProcessor implements the behavior of a VM Child Process created by the Server.
 * 
 * After startup initializing - the child connects to the server via web socket.
 * So the child is a Client to the Server.
 * 
 * In the next step the client enters a main loop waiting for "messages" from the server.
 * 
 * </pre>
 * 
 */
public class ChildProcessor {

    protected static Logger LOG = Logger.getLogger(ChildProcessor.class.getName());

    protected Config config;
    protected WebSocket wsClient;
    protected String childId;
    protected String socketUrl;
    protected JsonToolWrapper json;

    protected MessageHandler messageHandler;

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private CountDownLatch openEventLatch;

    public ChildProcessor(Config pConfig, JsonToolWrapper pJsonTool) {
        config = pConfig;
        childId = pConfig.getJPSChildId();
        socketUrl = config.getJPSParentUrl();
        json = pJsonTool;

        messageHandler = new DefaultMessageHandler(this);
    }

    // a message buffer to decouple message receiving from processing
    protected LinkedBlockingQueue<String> inputMessageQueue = new LinkedBlockingQueue<>();

    /**
     */
    protected synchronized boolean acceptMessage(String pMsg) {
        return inputMessageQueue.offer(pMsg);
    }

    /**
     */
    public static interface MessageHandler {
        /**
         * The method is called in the main loop after receiving a message.
         */
        public void processMessage(String pJsonMsg);

        public void processMessage(CPComData pMsg);
    }

    /**
     */
    public static class DefaultMessageHandler implements MessageHandler {

        protected String childId;
        protected ChildProcessor processor;
        protected JsonToolWrapper json;

        public DefaultMessageHandler(ChildProcessor pProcessor) {
            processor = pProcessor;
            childId = pProcessor.getChildId();
            json = pProcessor.getJson();
        }

        @Override
        public void processMessage(String pJsonMsg) {
            if (pJsonMsg != null) {
                CPComData lMsgData = json.toObject(pJsonMsg, CPComData.class);
                processMessage(lMsgData);
            }
        }

        @Override
        public void processMessage(CPComData pMsg) {
            if (pMsg.hasInfo()) {
                LOG.info(() -> String.format("JPS Child Process Info [%s] [%s]", pMsg.getInfo(), childId));
            }

            if (pMsg.hasInfo(ChildProcessManager.HANDSHAKE_RESPONSE)) {
                LOG.info(() -> String.format("Child Process Handshake received [%s]", childId));
            } else if (pMsg.isCmd()) {
                LOG.info(() -> String.format("Child Process Command received [%s] [%s]", pMsg.getCommand(),
                        childId));

                if (pMsg.isCmd(ChildProcessManager.CMD_CLOSE)) {
                    processor.stop();
                }
            }
        }
    }

    /**
     * The child process main loop.
     */
    public void start() {
        isRunning.set(true);

        Thread lChildThread = new Thread(() -> {
            try {
                String lMessage = "";
                // ensure client connection
                if (openEventLatch.await(10, TimeUnit.SECONDS)) {
                    LOG.info(() -> String.format("Child Process startet and listening for messages [%s]", childId));

                    // start the central message receiving loop
                    while (isRunning.get()) {
                        // blocks idle until a message is available
                        lMessage = inputMessageQueue.take();
                        messageHandler.processMessage(lMessage);
                    }

                } else {
                    // else don't start working
                    throw new UncheckedChildProcessException(
                            String.format("ERROR starting Child Process. No Parent connection. [%s]", childId));
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                throw new UncheckedChildProcessException("ERROR executing Child Process", e);
            } finally {
                stop();
            }
        });

        lChildThread.setName(String.format("JPS Child Thread [%s]", childId));
        lChildThread.start();
    }

    /**
     */
    public synchronized void connect() {
        openEventLatch = new CountDownLatch(1);
        wsClient = HttpClient
                .newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(socketUrl), new WSEventListener())
                .join();

        // send this child id to the parent
        // to associate this connection with this child
        wsClient.sendText(json.toString(new CPComData(childId).setInfo(ChildProcessManager.HANDSHAKE_REQUEST)), true);
    }

    /**
     */
    public synchronized void stop() {
        isRunning.set(false);
        if (wsClient != null) {
            wsClient.abort();
        }
    }

    /**
     */
    public boolean isConnected() {
        return (wsClient != null && !wsClient.isInputClosed());
    }

    /**
     */
    public String getChildId() {
        return childId;
    }

    /**
     */
    public JsonToolWrapper getJson() {
        return json;
    }

    /**
     */
    public void send(CPComData pMsg) {
        wsClient.sendText(json.toString(pMsg), true);
    }

    /**
     */
    public void setMessageHandler(MessageHandler pHandler) {
        messageHandler = pHandler;
    }

    /***************************************************************************
     ***************************************************************************/
    /**
     */
    private class WSEventListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            LOG.info(() -> String.format("Child Process WebSocket connection opened [%s]", childId));
            // unlock the connection latch
            openEventLatch.countDown();
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            acceptMessage(data.toString());
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
    }

    /**
     */
    public static class UncheckedChildProcessException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UncheckedChildProcessException(String pMsg) {
            super(pMsg);
        }

        UncheckedChildProcessException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }
}
