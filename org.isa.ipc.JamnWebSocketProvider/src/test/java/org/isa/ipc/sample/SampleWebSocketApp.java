/* Authored by iqbserve.de */
package org.isa.ipc.sample;

import java.util.logging.Logger;

import org.isa.ipc.JamnServer;
import org.isa.ipc.JamnWebSocketProvider;
import org.isa.ipc.JamnWebSocketProvider.WsoMessageProcessor;

/**
 * Create and start a Jamn Server with a WebSocket Provider.
 * 
 * Use: test/resources/WebSocketTest.html in a browser for testing.
 * 
 */
public class SampleWebSocketApp {

    /**
     */
    public static void main(String[] args) {

        // create a JamnServer
        JamnServer lServer = new JamnServer();

        // create the JamnWebSocketProvider
        JamnWebSocketProvider lWebSocketProvider = new JamnWebSocketProvider();

        // add the Server-Side message processor for the WebSocket
        lWebSocketProvider.addMessageProcessor(new WsoMessageProcessor() {

            @Override
            public byte[] onMessage(String pConnectionId, byte[] pMessage) {
                String lMsg = new String(pMessage);
                Logger.getGlobal().info("Message received: " + lMsg);

                return ("ECHO: " + lMsg).getBytes();
            }
        });

        // add the provider to JamnServer
        lServer.addContentProvider("WebSocketProvider", lWebSocketProvider);

        lServer.start();
    }
}
