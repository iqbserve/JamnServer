/* Authored by iqbserve.de */
package org.isa.ipc;

import org.isa.ipc.JamnServer.HttpHeader.Field;
import org.isa.ipc.JamnServer.HttpHeader.FieldValue;
import org.isa.ipc.JamnServer.HttpHeader.Status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.HttpHeader;
import org.isa.ipc.JamnServer.RequestMessage;
import org.isa.ipc.JamnServer.ResponseMessage;

/**
 * <pre>
 * A rudimentary WebSocket Provider implementation for the JamnServer.
 * 
 * The Provider is a one class wso "module" providing the basic protocol handling 
 * and interfaces to plug in customer messaging logic.
 *  
 * The WebSocketHandler class implements the main WebSocket logic and behavior.
 * in particular - the WebSocket "message format magic" - see: processWsoMessageRequests + encodeWsoMessage
 *   
 * In the present implementation there is also a WsoConnectionManager object involved
 * that holds a references to each established handler. 
 * How ever - since every handler represents a client connection
 * the manager is responsible for server-side communication to the client.
 *  
 * The "business logic" of a connection is implemented in a WsoMessageProcessor
 * associated with one WebSocket-connection-path (resp. handler) supporting n client connections.
 *  
 * Example:
 * JamnWebSocketProvider\src\test\..\..\sample\browser-js-websocket-call.html
 * </pre>
 */
public class JamnWebSocketProvider implements JamnServer.ContentProvider {

    // default websocket connection url: "ws://host:port/wsoapi"
    public static final String DefaultPath = "/wsoapi";

    protected static final String LS = System.lineSeparator();
    protected static Logger LOG = Logger.getLogger(JamnWebSocketProvider.class.getName());

    protected WsoConnectionManager connectionManager = new WsoConnectionManager();

    // a empty default access controller
    protected WsoAccessController accessCtrl = new WsoAccessController() {

        @Override
        public boolean isSupportedPath(String pPath, StringBuilder pMsg) {
            if (connectionPathNames.contains(pPath)) {
                return true;
            }
            pMsg.append("Unsupported path [").append(pPath).append("]");
            return false;
        }

        @Override
        public boolean isAccessGranted(Map<String, String> pRequestAttributes, StringBuilder pMsg) {
            return true;
        }
    };

    protected Set<String> connectionPathNames = new HashSet<>();
    protected ProviderAdapter providerAdapter = new ProviderAdapter();
    // limit client -> server payload data size
    protected long maxUpStreamPayloadSize = 65000;

    /**
     */
    public JamnWebSocketProvider() {
        addConnectionPath(DefaultPath);
    }

    /**
     * <pre>
     * WebSocket connections base on a one time, initial url path.
     * After a connection was established - there are NO pathnames involved any more.
     * 
     * How ever - it can still be useful to have different namespaces
     * </pre>
     */
    public JamnWebSocketProvider addConnectionPath(String pPath) {
        connectionPathNames.add(pPath);
        return this;
    }

    /**
     */
    public JamnWebSocketProvider setMaxUpStreamPayloadSize(long pSize) {
        maxUpStreamPayloadSize = pSize;
        return this;
    }

    /**
     */
    public JamnWebSocketProvider setAccessController(WsoAccessController pCtrl) {
        accessCtrl = pCtrl;
        return this;
    }

    /**
     */
    public void addMessageProcessor(WsoMessageProcessor pProcessor, String... pPath) {
        String lPath = (pPath != null && pPath.length == 1) ? pPath[0] : DefaultPath;
        if (connectionPathNames.contains(lPath)) {
            connectionManager.addMessageProcessor(pProcessor, lPath);
        } else {
            throw new UncheckedWebSocketException(
                    String.format("WebSocket Message Processor for unknown path [%s]", lPath));
        }
    }

    /**
     */
    public void sendMessageTo(String pConnectionId, byte[] pMessage) {
        if (connectionManager.isConnectionAvailable(pConnectionId)) {
            connectionManager.sendMessageFor(pConnectionId, pMessage);
        }
    }

    /**
     */
    public Set<String> getConnectionPathNames() {
        return connectionPathNames;
    }

    /**
     * The JamnServer.ContentProvider Interface method.
     */
    @Override
    public void handleContentProcessing(RequestMessage pRequest, Socket pSocket, Map<String, String> pComData)
            throws IOException {
        WebSocketHandler lHandler = new WebSocketHandler(pRequest.getPath(), providerAdapter);
        lHandler.handleRequest(pRequest, pSocket, pComData);
    }

    @Override
    public void handleContentProcessing(RequestMessage pRequest, ResponseMessage pResponse) {
        throw new UnsupportedOperationException(
                "WebSocket Content Provider requires use of extended (..., pSocket, pComData) method");
    }

    /*********************************************************
     * <pre>
     * The Jamn WebSocket-Server implementations.
     * </pre>
     *********************************************************/
    /**
     * <pre>
     * The WsoConnectionManager holds the established connections to be identified by the ConnectionId.
     * 
     * A connection is represented by a WebSocketHandler=WsoConnection.
     * </pre>
     */
    private static class WsoConnectionManager {
        // connectionId -> connection
        protected Map<String, WsoConnection> openConnections = Collections.synchronizedMap(new HashMap<>());

        // path -> processor
        protected Map<String, WsoMessageProcessor> processorMap = Collections.synchronizedMap(new HashMap<>());

        /**
         */
        protected synchronized void connectionEstablished(String pConnectionId, WsoConnection pConnection) {
            openConnections.put(pConnectionId, pConnection);
        }

        /**
         */
        protected synchronized void connectionClosed(String pConnectionId) {
            openConnections.remove(pConnectionId);
            LOG.info(() -> String.format("Closed WebSocket connection [%s]", pConnectionId));
        }

        /**
         * <pre>
         * This method is called for every incoming client "message" read from a WebSocketConnection.
         * </pre>
         */
        protected void processMessageFor(String pConnectionId, byte[] pMessage) {
            WsoMessageProcessor lProcessor;
            WsoConnection lConnection = openConnections.getOrDefault(pConnectionId, null);

            if (lConnection != null) {
                lProcessor = processorMap.getOrDefault(lConnection.getPath(), null);
                if (lProcessor != null) {
                    byte[] lResponse = lProcessor.onMessage(pConnectionId, pMessage);
                    // if response available - send it back
                    if (lResponse != null && lResponse.length > 0) {
                        sendMessageFor(pConnectionId, lResponse);
                    }
                }
            }
        }

        /**
         * <pre>
         * </pre>
         */
        protected boolean processErrorFor(String pConnectionId, byte[] pMessage, Exception pExp) {
            AtomicBoolean lClose = new AtomicBoolean(false);
            WsoMessageProcessor lProcessor;
            WsoConnection lConnection = openConnections.getOrDefault(pConnectionId, null);

            if (pExp instanceof FatalWsoException) {
                lClose.set(true);
            }

            if (lConnection != null) {
                lProcessor = processorMap.getOrDefault(lConnection.getPath(), null);
                if (lProcessor != null) {
                    byte[] lResponse = lProcessor.onError(pConnectionId, pMessage, pExp, lClose);
                    // if response available - send it back
                    if (lResponse != null && lResponse.length > 0) {
                        sendMessageFor(pConnectionId, lResponse);
                    }
                }
            }
            return lClose.get();
        }

        /**
         * A WebSocket is a connection established at one single access-point-path but
         * shared by all clients. Insofar is a WebSocket also associated with one
         * Processor that implements it's behavior.
         */
        protected void addMessageProcessor(WsoMessageProcessor pProcessor, String pPath) {
            if (!processorMap.containsKey(pPath)) {
                processorMap.put(pPath, pProcessor);
            } else {
                throw new UncheckedWebSocketException(
                        String.format("WebSocket Message Processor already defined for path [%s]", pPath));
            }
        }

        /**
         * The method implements the way from the WebSocket server side - back to a
         * connected client.
         */
        protected void sendMessageFor(String pConnectionId, byte[] pMessage) {
            WsoConnection lCon = openConnections.getOrDefault(pConnectionId, null);
            if (lCon != null) {
                lCon.sendMessage(pMessage);
            }
        }

        /**
         */
        protected boolean isConnectionAvailable(String pConnectionId) {
            return openConnections.containsKey(pConnectionId);
        }

    }

    /**
     */
    public static class WebSocketConnectionRejectedException extends Exception {
        private static final long serialVersionUID = 1L;

        WebSocketConnectionRejectedException(String pMsg) {
            super(pMsg);
        }
    }

    /**
    */
    protected static class FatalWsoException extends IOException {
        private static final long serialVersionUID = 1L;

        protected FatalWsoException(String pMsg) {
            super(pMsg);
        }

        protected FatalWsoException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

    /**
     */
    public static class UncheckedWebSocketException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UncheckedWebSocketException(String pMsg) {
            super(pMsg);
        }

        UncheckedWebSocketException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

    /**
     */
    protected class ProviderAdapter {
        protected WsoConnectionManager getWsoConnectionManager() {
            return connectionManager;
        }

        protected WsoAccessController getWsoAccessController() {
            return accessCtrl;
        }

        protected long getMaxUpStreamPayloadSize() {
            return maxUpStreamPayloadSize;
        }
    }

    /**
     * <pre>
     * The handler implements the wso protocol level.
     * It is responsible for 
     *  - doing the handshake
     *  - reading message bytes and create wso packet frames
     *  - encoding and sending message bytes
     * </pre>
     */
    protected static class WebSocketHandler implements WsoConnection {

        protected String connectionId = "";
        protected String initUrlPath = "";
        protected OutputStream outStream;
        protected WsoAccessController accessCtrl;
        protected WsoConnectionManager connectionManager;
        protected long maxUpStreamPayloadSize;

        protected WebSocketHandler() {
        }

        public WebSocketHandler(String pInitUrlPath, ProviderAdapter pAdapter) {
            this();
            initUrlPath = pInitUrlPath;
            accessCtrl = pAdapter.getWsoAccessController();
            connectionManager = pAdapter.getWsoConnectionManager();
            maxUpStreamPayloadSize = pAdapter.getMaxUpStreamPayloadSize();
        }

        /**
         */
        @Override
        public String geConnectiontId() {
            return connectionId;
        }

        @Override
        public String getPath() {
            return initUrlPath;
        }

        @Override
        public void sendMessage(byte[] pMessage) {
            if (outStream != null) {
                try {
                    byte[] encodedBytes = encodeWsoMessage(pMessage);

                    outStream.write(encodedBytes);
                    outStream.flush();
                } catch (IOException e) {
                    throw new UncheckedWebSocketException(String.format("WebSocket send message error: [%s]",
                            geConnectiontId()), e);
                }
            }
        }

        /**
         * Interface method for (default)request processor.
         */
        protected void handleRequest(RequestMessage pRequest, Socket pSocket, Map<String, String> pComData)
                throws IOException {
            // setting a marker for the top level server socket thread
            pComData.put(JamnServer.WEBSOCKET_PROVIDER, "");

            outStream = pSocket.getOutputStream();
            // create a unique connectionId
            connectionId = initUrlPath + " - " + Integer.toHexString(pSocket.hashCode()) + "-"
                    + pSocket.toString();

            try {
                // check accessibility
                StringBuilder lErrorMsg = new StringBuilder();
                if (!accessCtrl.isSupportedPath(initUrlPath, lErrorMsg)
                        || !accessCtrl.isAccessGranted(pRequest.header().getAttributes(), lErrorMsg)) {
                    throw new WebSocketConnectionRejectedException(
                            String.format("WebSocket connection rejected [%s] [%s] [%s]", getPath(), lErrorMsg,
                                    connectionId));
                }

                processWsoHandshake(connectionId, pRequest, pComData);

                // from here io is websocket specific
                // and NO longer bound to the http protocol

                // the processing blocks reading the InStream until connection is closed
                // every read is considered as a "message"
                // and is forwarded/published to the ConnectionManager for processing
                pSocket.setSoTimeout(0);
                processWsoMessageRequests(pSocket.getInputStream());

                // returning from reading lInStream
                // means the stream returned -1, end of stream and closed
                outStream.flush();
                outStream.close();

            } catch (Exception e) {
                throw new UncheckedWebSocketException(String.format("WebSocket request handling error: [%s]",
                        connectionId), e);
            } finally {
                // remove connection from the ConnectionManager
                connectionManager.connectionClosed(connectionId);
                try {
                    pSocket.close();
                } catch (IOException e) {
                    LOG.severe(() -> String.format("Finally closing WebSocket failed [%s] [%s]", e.getMessage(),
                            connectionId));
                }
            }
        }

        /**
         */
        protected String createWebSocketAcceptKey(String pRequestKey)
                throws NoSuchAlgorithmException {
            String lKey = pRequestKey + HttpHeader.MAGIC_WEBSOCKET_GUID;
            byte[] lSha1 = MessageDigest.getInstance("SHA-1").digest(lKey.getBytes(StandardCharsets.UTF_8));
            lKey = Base64.getEncoder().encodeToString(lSha1);
            return lKey;
        }

        /**
         */
        protected void processWsoHandshake(String pConnectionId, RequestMessage pRequest,
                Map<String, String> pComData)
                throws IOException, NoSuchAlgorithmException {

            ResponseMessage lHandshakeResponse = new ResponseMessage(outStream);
            lHandshakeResponse.header()
                    .setHttpVersion(Field.HTTP_1_1)
                    .setHttpStatus(Status.SC_101_SWITCH_PROTOCOLS)
                    .setConnection(FieldValue.UPGRADE)
                    .setUpgrade(FieldValue.WEBSOCKET)
                    .set(Field.SEC_WEBSOCKET_ACCEPT, createWebSocketAcceptKey(pRequest.header().getWebSocketKey()));

            lHandshakeResponse
                    .addContextData(pComData.get(JamnServer.SOCKET_IDTEXT))
                    .addContextData(pConnectionId)
                    .addContextData(pComData.get(JamnServer.REQUEST_HEADER_TEXT));

            try {
                lHandshakeResponse.send();

                // register this connection at the WsoConnectionManager
                connectionManager.connectionEstablished(pConnectionId, this);

                LOG.info(() -> String.format("%sWebSocket connection established [%s]%s", LS, pConnectionId, LS));
            } catch (Exception e) {
                lHandshakeResponse.header().setHttpStatus(Status.SC_500_INTERNAL_ERROR);
                lHandshakeResponse.send();
                throw e;
            }
        }

        /**
         * <pre>
         * The websocket listening loop.
         * The provider supports basic wso frames with
         *  - text messages
         *  - the close opcode
         *  - and fragmented messages
         *    starting fragmentation when the fin byte = false
         *    stopping fragmentation when the fin byte gets = true again
         * </pre>
         */
        protected void processWsoMessageRequests(InputStream pInStream) throws IOException {

            WsoFrame lFrame = new WsoFrame(WsoFrame.EmptyPacket);
            WsoFrame lFragmentedFrame = null;
            byte[] lPacket;
            int lReadPacketLength;
            boolean run = true;

            while (run) {
                try {
                    lPacket = new byte[1024];
                    lReadPacketLength = pInStream.read(lPacket);

                    lFrame = new WsoFrame(lReadPacketLength, lPacket);
                    lFrame.decodeHeader();
                    LOG.fine(lFrame.getDescription());

                    if (lFrame.hasOpcode(Opcode.CLOSE)) {
                        lPacket = encodeWsoMessage(lPacket);
                        outStream.write(lPacket);
                        outStream.flush();
                    } else {
                        // try to read all still missing message bytes
                        lFrame.completePayload(pInStream, maxUpStreamPayloadSize);
                        if (lFrame.isFin()) {
                            if (lFragmentedFrame != null) {
                                // stopping fragmentation
                                lFragmentedFrame.addFragment(lFrame);
                                lFrame = lFragmentedFrame;
                                lFragmentedFrame = null;
                            }
                            // hand over a complete websocket message for processing
                            connectionManager.processMessageFor(connectionId, lFrame.getPayloadData());
                        } else {
                            // starting fragment processing
                            if (lFragmentedFrame == null) {
                                lFragmentedFrame = lFrame;
                            } else {
                                lFragmentedFrame.addFragment(lFrame);
                            }
                        }
                    }
                } catch (SocketException se) {
                    run = false;
                } catch (Exception e) {
                    run = !connectionManager.processErrorFor(connectionId, lFrame.getAvailablePacketDataOnError(), e);
                }
            }
        }

        /**
         * Create a server->client websocket frame package with message payload data.
         * No masking, always text.
         * inspired by works like
         * https://stackoverflow.com/questions/43163592/standalone-websocket-server-without-jee-application-server
         */
        protected byte[] encodeWsoMessage(byte[] pMessageData) {

            int payloadLen = pMessageData.length;
            int headerLen = 2; // minimum wso message 2 bytes
            byte[] headerBytes = new byte[10];

            // byte-1: - fin=true, opcode=text
            headerBytes[0] = (byte) (0b10000000 | (byte) Opcode.TEXT.getCode()); // => 10000001 = 0x81

            // byte-2: payload len
            if (payloadLen <= 125) {
                headerBytes[1] = (byte) payloadLen;
            } else if (payloadLen >= 126 && payloadLen <= 65535) {
                headerBytes[1] = (byte) 126;
                int len = payloadLen;
                headerBytes[2] = (byte) ((len >> 8) & ((byte) 255 & 0xff));
                headerBytes[3] = (byte) (len & (byte) 255);
                headerLen = 4;
            } else {
                headerBytes[1] = (byte) 127;
                // org - int len = rawData.length
                long len = payloadLen; // note an int is not big enough in java
                headerBytes[2] = (byte) ((len >> 56) & ((byte) 255 & 0xff));
                headerBytes[3] = (byte) ((len >> 48) & ((byte) 255 & 0xff));
                headerBytes[4] = (byte) ((len >> 40) & ((byte) 255 & 0xff));
                headerBytes[5] = (byte) ((len >> 32) & ((byte) 255 & 0xff));
                headerBytes[6] = (byte) ((len >> 24) & ((byte) 255 & 0xff));
                headerBytes[7] = (byte) ((len >> 16) & ((byte) 255 & 0xff));
                headerBytes[8] = (byte) ((len >> 8) & ((byte) 255 & 0xff));
                headerBytes[9] = (byte) (len & ((byte) 255 & 0xff));
                headerLen = 10;
            }

            int packetLength = headerLen + payloadLen;
            byte[] framePacket = new byte[packetLength];

            System.arraycopy(headerBytes, 0, framePacket, 0, headerLen);
            System.arraycopy(pMessageData, 0, framePacket, headerLen, payloadLen);

            return framePacket;
        }

        /**
         */
        protected enum Opcode {
            CONTINUATION(0x0),
            TEXT(0x1),
            BINARY(0x2),
            CLOSE(0x8),
            PING(0x9),
            PONG(0xA);

            private final int code;

            Opcode(int code) {
                this.code = code;
            }

            public int getCode() {
                return code;
            }

            public static Opcode fromCode(int code) {
                for (Opcode op : values()) {
                    if (op.getCode() == code) {
                        return op;
                    }
                }
                throw new IllegalArgumentException("Unknown opcode: " + code);
            }
        }

        /**
         * <pre>
         * A basic implementation of wso frame data structure.
         * Decoding and keeping wso header and payload byte data.
         * </pre>
         */
        protected static class WsoFrame {
            protected static final byte[] EmptyPacket = new byte[] { (byte) 0x81, 0x0 };

            private int readPacketLength;
            private byte[] packet;
            private byte[] maskingKey;
            private ByteArrayOutputStream fragments = null;

            private Opcode opcode;
            private boolean isMasked;
            private int payloadLength;
            private int headerOffset;

            public WsoFrame(byte[] pPacket) throws IOException {
                this(pPacket.length, pPacket);
            }

            public WsoFrame(int pReadLen, byte[] pPacket) throws IOException {
                // WS minimal frame = 2 bytes
                if (pReadLen < 2) {
                    throw new FatalWsoException(
                            String.format("Invalid frame: insufficient packet length [%s]", pReadLen));
                }
                packet = new byte[pReadLen];
                System.arraycopy(pPacket, 0, packet, 0, pReadLen);
                readPacketLength = pReadLen;
            }

            /**
             */
            public boolean isFin() {
                // byte0 - bit 0
                return (packet[0] & 0b10000000) != 0;
            }

            /**
             */
            public boolean hasOpcode(Opcode pCode) {
                return opcode == pCode;
            }

            /**
             */
            public void decodeHeader() throws IOException {
                decodeRSV();
                decodeOpcode();
                decodeIsMasked();
                decodePayloadLength();
                decodeMask();
            }

            /**
             */
            protected void decodeRSV() {
                // byte0 - bit 1-3
                // RSV bits are ignored, could be checked: (byte0 & 0b01110000) != 0
            }

            /**
             */
            protected void decodeOpcode() {
                // byte0 - bit 4-7
                int value = packet[0] & 0b00001111;
                opcode = Opcode.fromCode(value);
            }

            /**
             */
            protected void decodeIsMasked() {
                // byte1 - bit 0
                isMasked = (packet[1] & 0b10000000) != 0;
            }

            /**
             */
            protected void decodePayloadLength() throws IOException {
                // byte1 - bit 1-7
                payloadLength = packet[1] & 0b01111111;

                // Start reading after the first two bytes
                headerOffset = 2;

                // Extended Payload Length (if any)
                if (payloadLength == 126) {
                    if (readPacketLength < 4) {
                        throw new FatalWsoException(
                                "Invalid frame: insufficient bytes for 16-bit payload length.");
                    }
                    payloadLength = ((packet[headerOffset] & 0xFF) << 8) | (packet[headerOffset + 1] & 0xFF);
                    headerOffset += 2;
                } else if (payloadLength == 127) {
                    if (readPacketLength < 10) {
                        throw new FatalWsoException(
                                "Invalid frame: insufficient bytes for 64-bit payload length.");
                    }
                    payloadLength = 0;
                    for (int i = 0; i < 8; i++) {
                        payloadLength = (payloadLength << 8) | (packet[headerOffset + i] & 0xFF);
                    }
                    headerOffset += 8;
                }
            }

            /**
             */
            protected void decodeMask() throws IOException {
                if (isMasked) {
                    if (packet.length < headerOffset + 4) {
                        throw new FatalWsoException("Invalid frame insufficient bytes for masking key.");
                    }
                    maskingKey = Arrays.copyOfRange(packet, headerOffset, headerOffset + 4);
                    headerOffset += 4;
                }
            }

            public String getDescription() {
                String ls = System.lineSeparator();
                StringBuilder lBuilder = new StringBuilder(String.format("Wso Frame [%s]%s", this.hashCode(), ls));
                lBuilder.append("Fin: ").append(this.isFin()).append(ls);
                lBuilder.append("Opcode: ").append(this.opcode).append(ls);
                lBuilder.append(String.format("Packet read len[%s], Head len[%s], Data len[%s]", readPacketLength,
                        headerOffset, readPacketLength - headerOffset)).append(ls);
                lBuilder.append("Payload length: ").append(payloadLength).append(ls);
                if (hasFragments()) {
                    lBuilder.append("Fragments size: ").append(fragments.size()).append(ls);
                }

                return lBuilder.toString();
            }

            /**
             */
            public boolean hasFragments() {
                return (fragments != null && fragments.size() > 0);
            }

            /**
             */
            public void addFragment(WsoFrame pFrame) throws IOException {
                if (fragments == null) {
                    fragments = new ByteArrayOutputStream();
                }
                fragments.write(pFrame.getPayloadData());
            }

            /**
             */
            public void completePayload(InputStream pInStream, long pMaxPayloadSize) throws IOException {

                int totalPacketLength = headerOffset + payloadLength;
                int remainingBytes = totalPacketLength - readPacketLength;
                int readLen = 0;
                int attempts = 0;
                byte[] buffer;

                if (totalPacketLength > pMaxPayloadSize) {
                    throw new FatalWsoException(
                            String.format("Max message size exceeded: [%s] > [%s]", totalPacketLength,
                                    pMaxPayloadSize));
                }
                if (remainingBytes > 0) {
                    buffer = new byte[totalPacketLength];
                    System.arraycopy(packet, 0, buffer, 0, readPacketLength);

                    while (readLen != -1 && readLen < remainingBytes) {
                        attempts++;
                        readLen += pInStream.read(buffer, readPacketLength + readLen, remainingBytes - readLen);
                    }

                    if (readLen == remainingBytes && buffer.length == totalPacketLength) {
                        readPacketLength = totalPacketLength;
                        packet = buffer;
                    } else {
                        throw new FatalWsoException(String.format(
                                "Content differnce: readLen[%s], remainingBytes[%s], buffer.length[%s], totalPacketLength[%s], attempts[%s]%s",
                                readLen, remainingBytes, buffer.length, totalPacketLength, attempts,
                                System.lineSeparator() + this.getDescription()));
                    }
                }
            }

            /**
             */
            public byte[] getPayloadData() throws IOException {
                if (packet.length < headerOffset + payloadLength) {
                    throw new FatalWsoException(
                            String.format("Invalid wso packet: len[%s] < headerOffset[%s] + payloadLength[%s]",
                                    packet.length, headerOffset, payloadLength));
                }

                byte[] payloadData = new byte[payloadLength];
                System.arraycopy(packet, headerOffset, payloadData, 0, payloadLength);

                // Unmask payload
                if (isMasked) {
                    for (int i = 0; i < payloadData.length; i++) {
                        payloadData[i] = (byte) (payloadData[i] ^ maskingKey[i % 4]);
                    }
                }

                if (hasFragments()) {
                    byte[] fragmentBytes = fragments.toByteArray();
                    byte[] buffer = new byte[payloadData.length + fragmentBytes.length];
                    System.arraycopy(payloadData, 0, buffer, 0, payloadData.length);
                    System.arraycopy(fragmentBytes, 0, buffer, payloadData.length, fragmentBytes.length);
                    payloadData = buffer;
                }

                return payloadData;
            }

            /**
             * <pre>
             * Try to get the first chunk of already read package bytes
             * to get some more informations for error handling.
             * </pre>
             */
            public byte[] getAvailablePacketDataOnError() {

                byte[] payloadData = new byte[packet.length];
                try {
                    System.arraycopy(packet, headerOffset, payloadData, 0, packet.length - headerOffset);

                    // Unmask payload
                    if (isMasked) {
                        for (int i = 0; i < payloadData.length; i++) {
                            payloadData[i] = (byte) (payloadData[i] ^ maskingKey[i % 4]);
                        }
                    }
                } catch (Exception e) {
                    // nothing to do
                }

                return payloadData;
            }

        }
    }

    /**
     * <pre>
     * The Processor defines the message listener on the Server Side.
     * The contract is byte data - in and out.
     * A concrete WsoMessageProcessor Implementation is responsible for anything else.
     * </pre>
     */
    public static interface WsoMessageProcessor {

        /**
         */
        public byte[] onMessage(String pConnectionId, byte[] pMessage);

        /**
         */
        public default byte[] onError(String pConnectionId, byte[] pMessage, Exception pExp, AtomicBoolean pClose) {
            return new byte[0];
        }

    }

    /**
     * <pre>
     * </pre>
     */
    public static interface WsoConnection {

        /**
         */
        public String geConnectiontId();

        /**
         * The initial url connection path.
         */
        public String getPath();

        /**
         * Send data to the client that established the connection.
         */
        public void sendMessage(byte[] pMessage);

    }

    /**
     * <pre>
     * A rudimentary "security" interface.
     * </pre>
     */
    public static interface WsoAccessController {
        /**
         */
        public boolean isSupportedPath(String pPath, StringBuilder pMsg);

        /**
         */
        public boolean isAccessGranted(Map<String, String> pRequestAttributes, StringBuilder pMsg);

    }

    /**
     */
    protected static String getStackTraceFrom(Throwable t) {
        return JamnServer.getStackTraceFrom(t);
    }

}
