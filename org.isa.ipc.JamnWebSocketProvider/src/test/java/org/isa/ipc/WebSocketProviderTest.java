/* Authored by iqbserve.de */
package org.isa.ipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * WebSocketProviderTest Unit test.
 */
@DisplayName("Jamn Server WebSocket Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebSocketProviderTest {

    private static Logger LOG = Logger.getGlobal();

    private static JamnServer Server;
    private static WebSocket WSClient;

    private static volatile String Event = "";
    private static volatile String Data = "";

    // a latch to synchronize sending and receiving a message on the client side
    private static volatile CountDownLatch Latch = new CountDownLatch(1);

    // block max 2 sec. until current Latch got unlocked
    private static void await() throws InterruptedException {
        Latch.await(2, TimeUnit.SECONDS);
    }

    @BeforeAll
    static void setupEnvironment() {
        // create a JamnServer
        Server = new JamnServer(8099);

        // create the provider
        JamnWebSocketProvider lWebSocketProvider = new JamnWebSocketProvider();

        // add a Server-Side message processor for the WebSocket
        lWebSocketProvider.addMessageProcessor((String pConnectionId, byte[] pMessage) -> {
            String lMsg = new String(pMessage);
            LOG.info("Request received: " + lMsg);

            return ("ECHO: " + lMsg).getBytes();
        });

        // add the provider to the server
        Server.addContentProvider("WebSocketProvider", lWebSocketProvider);
        // start server
        Server.start();
        assertTrue(Server.isRunning(), "Test Server start FAILED");

        /**
         * <pre>
         * Create a JavaSE websocket "client" for testing
         * that is basically a "java.net.http.WebSocket" with a listener
         * </pre>
         */

        // websocket url: "ws://localhost:8099/wsoapi"
        String lSocketUrl = "ws://localhost:" + Server.getConfig().getPort() + JamnWebSocketProvider.DefaultPath;

        WSClient = HttpClient
                .newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(lSocketUrl), new WSEventListener())
                .join();

        assertNotNull(WSClient, "WebSocket instantiation FAILED");
    }

    /***************************************************************************/

    @AfterEach
    void newLatch() {
        // new latch after each test
        Latch = new CountDownLatch(1);
    }

    @Test
    @Order(1)
    void testStartup() throws InterruptedException {
        // the event is generated during above WSClient instantiation/connection
        await();
        assertEquals("onOpen", Event, "Socket startup FAILED");
    }

    @Test
    @Order(2)
    void testSendText() throws InterruptedException, ExecutionException {
        // send text to server
        WSClient.sendText("Hello WebSocket", true).get();
        await();
        // check response data
        assertEquals("ECHO: Hello WebSocket", Data, "Socket call FAILED");
    }

    @AfterAll
    static void shutDownSocketAndServer() {
        WSClient.abort();
        Server.stop();
    }

    /***************************************************************************
     ***************************************************************************/
    private static class WSEventListener implements WebSocket.Listener {

        private void doEvent(String pName, String pData) {
            Event = pName;
            Data = pData;
            // unlock the current latch
            Latch.countDown();
            LOG.info(String.join(" ", "Event:", Event, "Data:", Data));
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            doEvent("onOpen", "");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            doEvent("onText", data.toString());
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
    }
}
