/* Authored by iqbserve.de */

package org.isa.ipc;

import org.isa.ipc.JamnServer.HttpHeader.Field;
import org.isa.ipc.JamnServer.HttpHeader.FieldValue;
import org.isa.ipc.JamnServer.HttpHeader.Status;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * <pre>
 * Just another micro node Server
 *
 * Jamn is an experimental, lightweight socket-based text data server
 * designed for smallness, independence and easy customization.
 *
 * The purpose is text data based interprocess communication.
 *
 * The structure consists of plugable components:
 * - server kernel with socket and multi-threaded connection setup
 * - request processor
 * - content provider
 *
 * All components can be easily changed or replaced.
 *
 * The default request processor relies on a basic HTTP subset.
 * It can read incoming HTTP Header/Body messages
 * and responds with an so fare appropriate HTTP message.
 * Plugable Content-Provider expand this capabilities.
 *
 * IMPORTANT:
 * How ever - Jamn IS NOT a HTTP/Web Server Implementation - this is NOT intended.
 * 
 * Jamn does NOT offer complete support for the HTTP protocol.
 * It just supports a subset - that is required and suitable for the cases
 *  - text data based network/interprocess communication with
 *    - REST like webservices
 *    - and WebSocket
 *  - and the ability to serve Browser based Applications (HTML/RIA)
 *
 * </pre>
 */
public class JamnServer {

    private static final Logger LOG = Logger.getLogger(JamnServer.class.getName());

    // JamnServer web id - just used for http header info
    public static final String JamnServerWebID = "JamnServer/0.0.1";

    // note page - to show that NO content provider is installed by default
    private static String BlankServerPage;

    static {
        try {
            InputStream lIn = JamnServer.class.getResourceAsStream("/blank-server.html");
            if (lIn != null) {
                BufferedReader lReader = new BufferedReader(new InputStreamReader(lIn));
                BlankServerPage = lReader.lines().collect(Collectors.joining("\n"));
            }
        } catch (RuntimeException e) {
            throw new UncheckedJamnServerException(e, "\nERROR - Jamn Server static initialization failed\n");
        }
    }

    public static final String LS = System.lineSeparator();
    public static final String LF = "\n";
    public static final String CRLF = "\r\n";
    public static final String WEBSOCKET_PROVIDER = "WebSocketProvider";
    public static final String SOCKET_IDTEXT = "socket.idtext";
    public static final String SOCKET_USAGE = "socket.usage";
    public static final String SOCKET_EXCEPTION = "socket.exception";
    public static final String REQUEST_HEADER_TEXT = "request.header.text";

    protected Config config = new Config();

    protected ServerThread serverThread = null;
    protected ServerSocket serverSocket = null;
    protected URI serverURI = null;
    protected ExecutorService requestExecutor = null;
    protected RequestProcessor requestProcessor = null;
    protected int clientSocketTimeout = 10000;

    public JamnServer() {
        // default port in config is: 8099
        initialize();
    }

    public JamnServer(int pPort) {
        getConfig().setPort(pPort);
        initialize();
    }

    public JamnServer(Properties pProps) {
        config = new Config(pProps);
        initialize();
    }

    /**
     * Just for running in development
     */
    public static void main(String[] pArgs) {
        JamnServer lServer = new JamnServer();
        // simplification for using local html files in a browser
        lServer.getConfig().setAllowAllCORSEnabled(true);
        lServer.start();
    }

    /**
     */
    protected void initialize() {
        requestProcessor = new HttpDefaultRequestProcessor(config);
    }

    /**
     */
    protected ServerSocket createServerSocket() throws IOException {
        int lPort = config.getPort();
        ServerSocket lSocket = null;

        try {
            if (!System.getProperty("javax.net.ssl.keyStore", "").isEmpty()
                    && !System.getProperty("javax.net.ssl.keyStorePassword", "").isEmpty()) {
                lSocket = SSLServerSocketFactory.getDefault().createServerSocket(lPort);
            } else {
                lSocket = ServerSocketFactory.getDefault().createServerSocket(lPort);
            }

            lSocket.setReuseAddress(true);
            if (lPort == 0) {
                config.setActualPort(lSocket.getLocalPort());
            }
        } catch (Exception e) {
            if (lSocket != null) {
                lSocket.close();
            }
            throw e;
        }
        return lSocket;
    }

    /**
     */
    protected void determineServerURI(ServerSocket pSocket) throws IOException {
        String scheme = "http";
        if (pSocket instanceof SSLServerSocket) {
            scheme = "https";
        }
        try {
            this.serverURI = new URI(scheme + "://localhost:" + config.getPort());
        } catch (URISyntaxException e) {
            throw new IOException("Error creating server URI", e);
        }
    }

    /**
     * Internal - create/setup and start kernel server thread and socket.
     */
    protected synchronized void startListening() throws IOException {
        if (isRunning()) {
            return;
        }

        if (requestExecutor == null || requestExecutor.isShutdown()) {
            requestExecutor = Executors.newFixedThreadPool(config.getWorkerNumber());
        }
        clientSocketTimeout = config.getClientSocketTimeout();
        serverSocket = createServerSocket();
        determineServerURI(serverSocket);

        serverThread = new ServerThread();
        serverThread.setName(getClass().getSimpleName() + " - on Port [" + config.getPort() + "]");
        serverThread.start();
    }

    /**
     * Internal - stop/close kernel server thread and socket.
     */
    protected synchronized void stopListening() {
        if (isRunning()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // OK this is specified
            }
        }
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.shutdown();
        }
        if (requestExecutor != null) {
            requestExecutor.shutdownNow();
        }
    }

    /**
     */
    public synchronized void start() {
        String lErrorInfo = "";
        try {
            startListening();
            LOG.info(() -> new StringBuilder(LS)
                    .append("Server Configuration:").append(LS)
                    .append(config).append(LS)
                    .append(LS)
                    .append("JamnServer Instance STARTED:").append(LS)
                    .append(" ").append(serverURI.toString()).append(" - ").append(serverSocket.toString())
                    .append(LS)
                    .append(config.isAllowAllCORSEnabled() ? LS + "IMPORTANT - Global ALLOW ALL CORS is enabled!" + LS
                            : "")
                    .toString());
        } catch (Exception e) {
            if (e instanceof BindException) {
                lErrorInfo = String.format("Probably ALREADY RUNNING SERVER on port [%s]", getConfig().getPort());
                LOG.severe(String.format("%s %s%s", lErrorInfo, LS, getStackTraceFrom(e)));
            } else {
                LOG.severe(String.format("ERROR starting JamnServer: [%s]%s%s", e, LS,
                        getStackTraceFrom(e)));
                lErrorInfo = e.getMessage();
            }
            stop();
            throw new UncheckedJamnServerException(String.format("JamnServer start failed [%s]", lErrorInfo));
        }
    }

    /**
     */
    public synchronized void stop() {
        boolean wasRunning = isRunning();
        stopListening();
        if (wasRunning) {
            LOG.info(LS + "JamnServer STOPPED");
        }
    }

    /**
     */
    public URI getURI() {
        return serverURI;
    }

    /**
     */
    public boolean isRunning() {
        return (serverSocket != null && !serverSocket.isClosed());
    }

    /**
     */
    public Config getConfig() {
        return config;
    }

    /*********************************************************
     * <pre>
     * The server plugin interfaces for customer extensions and provider.
     * </pre>
     *********************************************************/

    /**
     */
    public JamnServer addContentProvider(String pId, ContentProvider pProvider) {
        requestProcessor.addContentProvider(pId, pProvider);
        return this;
    }

    /**
     */
    public JamnServer setContentProviderDispatcher(ContentProviderDispatcher pProviderDispatcher) {
        requestProcessor.setContentProviderDispatcher(pProviderDispatcher);
        return this;
    }

    /**
     */
    public void setMessagePreprocessor(RequestMessagePreprocessor pProcessor) {
        requestProcessor.setMessagePreprocessor(pProcessor);
    }

    /*********************************************************
     * <pre>
     * The Server socket listener Thread.
     * It starts via requestExecutor a worker thread for every incoming connection
     * and delegates the client socket to a central requestProcessor.
     * </pre>
     *********************************************************/
    /**
     */
    protected class ServerThread extends Thread {
        private volatile boolean work = true;

        public synchronized void shutdown() {
            work = false;
        }

        @Override
        public void run() {

            try {
                ServerSocket lServerSocket = serverSocket; // keep local

                while (work && lServerSocket != null && !lServerSocket.isClosed()) {

                    final Socket lClientSocket = lServerSocket.accept();

                    // start request execution in its own thread
                    requestExecutor.execute(() -> {
                        Map<String, String> lComData = new HashMap<>(5);
                        long start = System.currentTimeMillis();
                        try {
                            try {
                                lClientSocket.setSoTimeout(clientSocketTimeout);
                                lClientSocket.setTcpNoDelay(true);
                                // delegate the concrete request handling
                                requestProcessor.handleRequest(lClientSocket, lComData);

                            } finally {
                                try {
                                    if (!(lClientSocket instanceof SSLSocket)) {
                                        lClientSocket.shutdownOutput(); // first step only output
                                    }
                                } finally {
                                    lClientSocket.close();
                                    LOG.fine(() -> String.format("%s %s %s %s %s",
                                            lComData.getOrDefault(SOCKET_IDTEXT, "unknown"),
                                            "closed [" + (System.currentTimeMillis() - start) + "]",
                                            "usage [" + lComData.getOrDefault(SOCKET_USAGE, "") + "]",
                                            "exp [" + lComData.getOrDefault(SOCKET_EXCEPTION, "") + "]",
                                            Thread.currentThread().getName()));
                                }
                            }
                        } catch (IOException e) {
                            // nothing to do
                        }
                    });
                }
            } catch (IOException e) {
                // nothing to do
            } finally {
                LOG.fine(() -> String.format("ServerThread finished: %s", Thread.currentThread().getName()));
            }
        }
    }

    /*********************************************************
     * <pre>
     * The central processing interfaces and default implementations.
     * Chain: ServerThread -> RequestProcessor -> ContentProvider
     * </pre>
     *********************************************************/
    /**
     * <pre>
     * The Interface used by the RequestProcessor to create the data content for a request.
     * Or to delegate a protocol upgrade to a specific handler e.g. WebSocket.
     * </pre>
     */
    public static interface ContentProvider {

        /**
         * Standard Interface for Content Provider
         */
        void handleContentProcessing(RequestMessage pRequest, ResponseMessage pResponse);

        /**
         * Extended Interface for Provider like WebSocket
         */
        default void handleContentProcessing(RequestMessage pRequest, Socket pSocket, Map<String, String> pComData)
                throws IOException {
            throw new UnsupportedOperationException("Call to ContentProvider unimplemented default interface method");
        }

    }

    /**
     * <pre>
     * The Interface used by the server socket thread to delegate 
     * the protocol specific processing of incoming client connection/requests.
     * </pre>
     */
    public static interface RequestProcessor {

        /**
         * The central interface method called from the socket based main thread.
         *
         * @param pSocket
         * @param pComData - internal communication data
         * @throws IOException
         */
        void handleRequest(Socket pSocket, Map<String, String> pComData) throws IOException;

        /**
         * The interface to set the content provider that creates the use case specific
         * response content.
         */
        void addContentProvider(String pId, ContentProvider pProvider);

        /**
         */
        default void setContentProviderDispatcher(ContentProviderDispatcher pProviderDispatcher) {
        }

        /**
         */
        default void setMessagePreprocessor(RequestMessagePreprocessor pPocessor) {
        }

    }

    /**
     * <pre>
     * Jamn server supports multiple content providers.
     * If there are more than one provider,
     * the dispatcher decides which request is delegated to which provider.
     * </pre>
     */
    public static interface ContentProviderDispatcher {
        /**
         */
        String getContentProviderIDFor(RequestMessage pRequest);
    }

    /**
     * <pre>
     * IMPORTANT Preamble: 
     * 
     * The Jamn Server initially offers NO security components or implementations
     * !!! and is therefore "COMPLETELY INSECURE" !!!
     * Security mechanisms must be added individually.
     * 
     * On the first level by using SSL socket connections.
     * 
     * On the request processing level - individual components are required 
     * that correspond to the security standards and needs of the user.
     *
     * The MessagePreprocessor serves as the general interface 
     * to implement and plug in any need that has to be processed before serving any content.
     *
     * </pre>
     */
    public static interface RequestMessagePreprocessor {
        /**
         */
        void processRequest(RequestMessage pRequest, ResponseMessage pResponse)
                throws IOException, SecurityException;
    }

    /**
     * <pre>
     * A wrapper interface for JSON tools.
     * </pre>
     */
    public static interface JsonToolWrapper {
        /**
         */
        public <T> T toObject(String pSrc, Class<T> pType) throws UncheckedJsonException;

        /**
         */
        public String toString(Object pObj) throws UncheckedJsonException;

        /**
         */
        public default Object getNativeTool() {
            return null;
        }

        /**
         */
        public default String prettify(String pJsonInput) {
            return pJsonInput;
        }
    }

    /**
     * <pre>
     * The RequestProcessor is the interface called by the socket layer 
     * when a request is received - and thus the entry point for processing.
     *  - handleRequest(...)
     * 
     * The Default-RequestProcessor implements the basic JamnServer http layer
     * but it uses EMPTY provider and pre processing.
     * The processor just reads data from the underlying socket
     * and tries to interpret/transform it into a http message consisting of a http-header and body.
     * 
     * ALL further processing functionality and logic has to be implemented by providers etc.
     * 
     * The processor also branches to an upgrade handler in case of a Web-Socket connection request.
     * If a Web-Socket connection can then be established
     * the further client/server communication is completely split off.
     * 
     * </pre>
     */
    public static class HttpDefaultRequestProcessor implements RequestProcessor {
        protected Config config;
        protected String encoding = StandardCharsets.UTF_8.name();
        protected boolean keepAliveEnabled = false;

        /**
         */
        public HttpDefaultRequestProcessor(Config pConfig) {
            this.config = pConfig;
            this.encoding = config.getEncoding();
            this.keepAliveEnabled = config.isConnectionKeepAlive();
        }

        // the available ContentProvider
        protected Map<String, ContentProvider> contentProviderMap = new HashMap<>();

        // the interface to a first level processing of request messages
        protected RequestMessagePreprocessor messagePreprocessor = (RequestMessage pRequest,
                ResponseMessage pResponse) -> LOG
                        .warning(() -> "WARNING - EMPTY DEFAULT MessagePreprocessor active");

        // an empty default provider dispatcher
        protected ContentProviderDispatcher contentDispatcher = (RequestMessage pRequest) -> {
            LOG.warning(() -> String.format(
                    "WARNING - The empty DEFAULT ContentProvider-Dispatcher was invoked although there are [%s] provider registered.%s",
                    contentProviderMap.size(), LS));
            return null;
        };

        // empty default content provider
        // just returning the blank server page at root
        // or status SC_204_NO_CONTENT
        protected ContentProvider defaultContentProvider = (RequestMessage pRequest, ResponseMessage pResponse) -> {
            LOG.warning(() -> "Request to EMPTY Default Content Provider");

            pResponse.setStatus(Status.SC_204_NO_CONTENT);
            try {
                if ("/".equals(pRequest.getPath())) {
                    pResponse.setContentType(FieldValue.TEXT_HTML);
                    pResponse.writeToContent(BlankServerPage.getBytes());
                    pResponse.setStatus(Status.SC_200_OK);
                }
            } catch (IOException e) {
                // ignore in empty default implementation
            }
        };

        /**
         * <pre>
         * The actual top level request handling implementation.
         * </pre>
         */
        @Override
        public void handleRequest(Socket pSocket, Map<String, String> pComData) throws IOException {

            String socketIDText = String.format("ClientSocket [%s]", pSocket.hashCode());
            pComData.put(SOCKET_IDTEXT, socketIDText);

            InputStream lInStream = new BufferedInputStream(pSocket.getInputStream(), getInitialBufferSizeFor("in"));
            OutputStream lOutStream = new BufferedOutputStream(pSocket.getOutputStream(),
                    getInitialBufferSizeFor("out"));

            RequestMessage lRequest = null;
            ResponseMessage lResponse = null;
            ContentProvider lContentProvider = null;

            boolean keepAlive = false;
            // a usage counter for debugging purpose
            int usage = 0;
            try {
                LOG.fine(() -> String.format("%s %s %s %s", socketIDText, "opened", pSocket.toString(),
                        Thread.currentThread().getName()));

                do {
                    keepAlive = false;
                    lResponse = new ResponseMessage(lOutStream, new HttpHeader()
                            .setContentType(FieldValue.TEXT_PLAIN)
                            .setContentLength("0")).addContextData(socketIDText);

                    String lHeaderText = readHeader(lInStream);
                    lResponse.contextData.add(lHeaderText);

                    lRequest = new RequestMessage(newHeader(lHeaderText));
                    lRequest.setBody(readBody(lInStream, lRequest.getContentLength(), lRequest.getEncoding()));

                    // comfort method restricted to localhost access
                    if (config.isAllowAllCORSEnabled() && HttpHeader.isLocalhost(lRequest.header().getHost())) {
                        HttpHeader.setAllowAllCORSFor(lResponse.header());
                    }
                    // interface to call any protocol or app specific processing
                    // before content providing
                    // this may trigger an immediate response
                    messagePreprocessor.processRequest(lRequest, lResponse);

                    if (lResponse.isNotProcessed()) {
                        // route request to the required content provider
                        // check for WebSocket upgrade request
                        if (lRequest.header().isWebSocket()) {
                            // explicit switch to WebSocket processing
                            pComData.put(JamnServer.REQUEST_HEADER_TEXT, lHeaderText);
                            lContentProvider = getContentProvider(WEBSOCKET_PROVIDER);
                            lContentProvider.handleContentProcessing(lRequest, pSocket, pComData);
                        } else {
                            keepAlive = checkForKeepAliveConnection(lRequest, lResponse);

                            // create and send the response content
                            lContentProvider = getContentProviderFor(lRequest);
                            lContentProvider.handleContentProcessing(lRequest, lResponse);
                            if (lResponse.isNotProcessed()) {
                                lResponse.send();
                            }
                            usage++;
                        }
                    }
                    // if keep-alive loop until socket timeout
                } while (keepAlive && keepAliveEnabled);
            } catch (InterruptedIOException e) {
                pComData.put(SOCKET_EXCEPTION, e.getMessage());
                interruptCleanUp(socketIDText, lInStream, lOutStream);
            } catch (SecurityException se) {
                // send 403 for any security exception
                lResponse.sendStatus(Status.SC_403_FORBIDDEN);
            } catch (Exception e) {
                LOG.severe(() -> String.format("%s Request handling internal ERROR: %s %s %s", socketIDText, e, LS,
                        getStackTraceFrom(e)));
                // send 500 for any other exception
                lResponse.sendStatus(Status.SC_500_INTERNAL_ERROR);
            } finally {
                lResponse.close();
                pComData.put(SOCKET_USAGE, String.valueOf(usage));
            }
        }

        /**
        */
        @Override
        public void setMessagePreprocessor(RequestMessagePreprocessor pHandler) {
            messagePreprocessor = pHandler;
        }

        /**
         */
        @Override
        public void setContentProviderDispatcher(ContentProviderDispatcher pProviderDispatcher) {
            contentDispatcher = pProviderDispatcher;
        }

        /**
         */
        @Override
        public void addContentProvider(String pId, ContentProvider pProvider) {
            contentProviderMap.put(pId, pProvider);
        }

        /**
         */
        protected ContentProvider getContentProviderFor(RequestMessage pRequest) {
            if (contentProviderMap.isEmpty()) {
                return defaultContentProvider;
            }
            // to avoid the need for a dispatcher for only one provider
            if (contentProviderMap.size() == 1) {
                return contentProviderMap.values().iterator().next();
            }
            // else use dispatcher
            String providerId = contentDispatcher.getContentProviderIDFor(pRequest);
            return contentProviderMap.getOrDefault(providerId, defaultContentProvider);
        }

        /**
         */
        protected ContentProvider getContentProvider(String pType) {
            return contentProviderMap.getOrDefault(pType, defaultContentProvider);
        }

        /**
         */
        protected String readHeader(InputStream pInStream) throws IOException {
            byte[] lBytes;
            int lByte = 0;
            int headerEndFlag = 0;

            ByteArrayOutputStream lByteBuffer = new ByteArrayOutputStream();
            do {
                if ((lByte = pInStream.read()) == -1) {
                    break; // end of stream, because pIn.available() may ? not
                           // be reliable enough
                }

                if (lByte == 13) { // CR
                    lByteBuffer.write(lByte);
                    if ((lByte = pInStream.read()) == 10) { // LF
                        lByteBuffer.write(lByte);
                        headerEndFlag++;
                    } else if (lByte != -1) {
                        lByteBuffer.write(lByte);
                    }
                } else {
                    lByteBuffer.write(lByte);
                    if (headerEndFlag == 1) {
                        headerEndFlag--;
                    }
                }
            } while (headerEndFlag < 2 && pInStream.available() > 0);

            lBytes = lByteBuffer.toByteArray();
            return new String(lBytes, this.encoding).trim();
        }

        /**
         */
        protected HttpHeader newHeader(String pHeaderText) {
            return new HttpHeader(Collections.unmodifiableMap(parseHttpHeader(pHeaderText)));
        }

        /**
         * <pre>
         * Tries to blocking read the request body from the socket InputStream.
         * </pre>
         */
        protected String readBody(InputStream pInStream, int pContentLength, String pEncoding) throws IOException {
            ByteArrayOutputStream lByteBuffer = new ByteArrayOutputStream();
            int lByte = 0;
            int lActual = 0;
            int lAvailable = 0;

            if (pContentLength > 0) {
                while ((lAvailable = pInStream.available()) > 0 || lActual < pContentLength) {
                    lActual++;
                    lByte = pInStream.read();
                    if (lByte == -1) {
                        break;
                    }
                    lByteBuffer.write(lByte);
                }
                if (lActual != pContentLength || lAvailable > 0) {
                    String msg = String.format("Http body read: actual [%s] header [%s] available [%s]", lActual,
                            pContentLength,
                            lAvailable);
                    LOG.warning(() -> msg);
                }
            }
            return new String(lByteBuffer.toByteArray(), pEncoding);
        }

        /**
         * Parse HTTP header lines to key/value pairs. This method includes the http
         * status line as "self defined attributes" (path, method, version etc.) in the
         * map.
         */
        protected Map<String, String> parseHttpHeader(String pHeader) {
            Map<String, String> lFields = new LinkedHashMap<>();
            String[] lLines = pHeader.split("\\n");
            StringBuilder lVal;

            for (int i = 0; i < lLines.length; i++) {
                String[] lParts = null;
                String lLine = lLines[i];

                if (i == 0) {
                    lFields.putAll(parseHttpHeaderStatusLine(lLine));
                } else if (lLine.contains(":")) {
                    lParts = lLine.split(":");
                    if (lParts.length == 2) {
                        lFields.put(lParts[0].trim(), lParts[1].trim());
                    } else if (lParts.length > 2) {
                        lVal = new StringBuilder(lParts[1].trim());
                        for (int k = 1; k + 1 < lParts.length; k++) {
                            lVal.append(":").append(lParts[k + 1].trim());
                        }
                        lFields.put(lParts[0].trim(), lVal.toString());
                    }
                }
            }
            return lFields;
        }

        /**
         * Parse header first line = status line.
         */
        protected Map<String, String> parseHttpHeaderStatusLine(String pStatusLine) {
            Map<String, String> lFields = new LinkedHashMap<>();

            // parse status line to "self defined attributes"
            String[] lParts = pStatusLine.split(" ");
            String[] lSubParts = null;
            if (lParts.length > 0) {
                for (int i = 0; i < lParts.length; i++) {
                    if (i == 0) {
                        // always bring method names to Upper Case
                        lFields.put(Field.HTTP_METHOD, lParts[i].trim().toUpperCase());
                    } else if (lParts[i].trim().toUpperCase().contains(Field.HTTP_VERSION_MARK)) {
                        lSubParts = lParts[i].trim().split("/");
                        if (lSubParts.length == 2) {
                            lFields.put(Field.HTTP_VERSION, lSubParts[1].trim());
                        }
                    } else if (lParts[i].trim().contains("/")) {
                        lFields.put(Field.HTTP_PATH, lParts[i].trim());
                    }
                }
            }
            return lFields;
        }

        /**
         */
        protected int getInitialBufferSizeFor(String pType) {
            return "in".equalsIgnoreCase(pType) ? 4 * 1024 : 8 * 1024;
        }

        /**
         */
        protected boolean checkForKeepAliveConnection(RequestMessage pRequest, ResponseMessage pResponse) {

            if (pRequest.header().hasConnectionKeepAlive() && keepAliveEnabled) {
                pResponse.header().setConnectionKeepAlive();
                return true;
            } else {
                pResponse.header().setConnectionClose();
            }
            return false;
        }

        /**
        */
        protected void interruptCleanUp(String pIDText, InputStream pIn, OutputStream pOut) {
            try {
                pIn.close();
            } catch (Exception e) {
                LOG.warning(String.format("%s ERROR closing input after timeout [%s]", pIDText, e.toString()));
            }
            try {
                pOut.close();
            } catch (Exception e) {
                LOG.warning(String.format("%s ERROR closing output after timeout [%s]", pIDText, e.toString()));
            }
        }
    }

    /**
     * <pre>
     * The class encapsulates HTTP header information.
     * In particular, it provides a selection of constants for status codes and header fields.
     * In addition, it serves as a wrapper around a map with key/value pairs for header fields.
     * </pre>
     */
    public static class HttpHeader {

        /**
         */
        public static String getHttpStatusStringFor(Object pNr) {
            String lNr = String.valueOf(pNr).trim();
            if (Status.TEXT.containsKey(lNr)) {
                StringBuilder lStatus = new StringBuilder(lNr);
                lStatus.append(" ").append(Status.TEXT.get(lNr));
                return lStatus.toString();
            }
            return lNr;
        }

        public static HttpHeader setAllowAllCORSFor(HttpHeader pHeader) {
            pHeader.set(Field.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            pHeader.set(Field.ACCESS_CONTROL_ALLOW_METHODS, "*");
            pHeader.set(Field.ACCESS_CONTROL_ALLOW_HEADERS, "*");
            return pHeader;
        }

        public static boolean isLocalhost(String pHost) {
            String host = pHost.trim().toLowerCase();
            return (host.startsWith("localhost") || host.startsWith("127.0.0.1"));
        }

        protected String encoding = StandardCharsets.UTF_8.name();

        protected String[] statusline = new String[] { Field.HTTP_1_0, "" };

        protected Map<String, String> fieldMap = new LinkedHashMap<>();
        protected List<String> setCookies = null;

        public HttpHeader() {
            set(Field.SERVER, JamnServerWebID);
        }

        /**
         */
        public HttpHeader(Map<String, String> pAttributes) {
            fieldMap = pAttributes;
        }

        // the magic websocket uid to accept a connection request
        public static final String MAGIC_WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        /**
         * HTTP status codes.
         */
        public static class Status {
            protected Status() {
            }

            public static final String SC_101_SWITCH_PROTOCOLS = "101";
            public static final String SC_200_OK = "200";
            public static final String SC_204_NO_CONTENT = "204";
            public static final String SC_400_BAD_REQUEST = "400";
            public static final String SC_403_FORBIDDEN = "403";
            public static final String SC_404_NOT_FOUND = "404";
            public static final String SC_405_METHOD_NOT_ALLOWED = "405";
            public static final String SC_408_TIMEOUT = "408";
            public static final String SC_500_INTERNAL_ERROR = "500";

            public static final Map<String, String> TEXT;
            static {
                Map<String, String> lMap = new HashMap<>();
                lMap.put("101", "Switching Protocols");
                lMap.put("200", "OK");
                lMap.put("201", "Created");
                lMap.put("204", "No Content");
                lMap.put("400", "Bad Request");
                lMap.put("403", "Forbidden");
                lMap.put("404", "Not found");
                lMap.put("405", "Method Not Allowed");
                lMap.put("406", "Not Acceptable");
                lMap.put("408", "Request Timeout");
                lMap.put("411", "Length Required");
                lMap.put("500", "Internal Server Error");
                lMap.put("503", "Service Unavailable");
                TEXT = Collections.unmodifiableMap(lMap);
            }
        }

        /**
         * HTTP header field identifier.
         */
        public static class Field {
            protected Field() {
            }

            public static final String HTTP_1_0 = "HTTP/1.0";
            public static final String HTTP_1_1 = "HTTP/1.1";

            // statusline attributes
            public static final String HTTP_METHOD = "http-method";
            public static final String HTTP_PATH = "http-path";
            public static final String HTTP_STATUS = "http-status";
            public static final String HTTP_VERSION = "http-version";
            public static final String HTTP_VERSION_MARK = "HTTP/";

            // header field attributes
            public static final String SERVER = "Server";
            public static final String CONTENT_LENGTH = "Content-Length";
            public static final String CONTENT_TYPE = "Content-Type";
            public static final String CONNECTION = "Connection";
            public static final String HOST = "Host";
            public static final String ORIGIN = "Origin";
            public static final String UPGRADE = "Upgrade";
            public static final String SET_COOKIE = "Set-Cookie";
            public static final String COOKIE = "Cookie";

            public static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
            public static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";

            public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
            public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
            public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

            public static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
            public static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
            public static final String SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
            public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
            public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
            public static final String SEC_FETCH_MODE = "Sec-Fetch-Mode";
            public static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

            public static final String AUTHORIZATION = "Authorization";
            public static final String BEARER = "Bearer";

        }

        /**
         * HTTP header field values.
         */
        public static class FieldValue {
            protected FieldValue() {
            }

            public static final String CLOSE = "close";
            public static final String KEEP_ALIVE = "keep-alive";
            public static final String UPGRADE = "Upgrade";
            public static final String KEEP_ALIVE_UPGRADE = "keep-alive, Upgrade";
            public static final String WEBSOCKET = "websocket";
            public static final String TEXT = "text/";
            public static final String TEXT_PLAIN = "text/plain";
            public static final String TEXT_XML = "text/xml";
            public static final String TEXT_HTML = "text/html";
            public static final String TEXT_CSS = "text/css";
            public static final String TEXT_JS = "text/javascript";
            public static final String APPLICATION_JSON = "application/json";
            public static final String IMAGE = "image/";
            public static final String IMAGE_PNG = "image/png";
            public static final String IMAGE_X_ICON = "image/x-icon";
            public static final String IMAGE_SVG_XML = "image/svg+xml";
        }

        /**
         */
        protected static boolean equalsOrContains(String pAttributeVal, String pVal) {
            return (pAttributeVal.equalsIgnoreCase(pVal) || pAttributeVal.toLowerCase().contains(pVal.toLowerCase()));
        }

        /**
         */
        protected StringBuilder createHeader() {
            StringBuilder lHeader = new StringBuilder(String.join(" ", statusline));
            for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
                lHeader.append(CRLF).append(entry.getKey()).append(": ").append(entry.getValue());
            }

            if (setCookies != null && !setCookies.isEmpty()) {
                for (String entry : setCookies) {
                    lHeader.append(CRLF).append(Field.SET_COOKIE).append(": ").append(entry);
                }
            }

            lHeader.append(CRLF).append(CRLF);
            return lHeader;
        }

        /**
         */
        @Override
        public String toString() {
            return createHeader().toString();
        }

        /**
         */
        public byte[] toMessageBytes(String pEncoding) throws IOException {
            return createHeader().toString().getBytes(pEncoding);
        }

        /**
         */
        public boolean has(String pKey, String pVal) {
            return equalsOrContains(fieldMap.getOrDefault(pKey, ""), pVal);
        }

        /**
         */
        public HttpHeader set(String pKey, String pVal) {
            fieldMap.put(pKey, pVal);
            return this;
        }

        /**
         */
        public String get(String pKey, String... pDefault) {
            return fieldMap.getOrDefault(pKey, pDefault.length > 0 ? pDefault[0] : "");
        }

        /**
         */
        public HttpHeader setEncoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /**
         */
        public String getEncoding() {
            return encoding;
        }

        /**
         */
        public HttpHeader setHttpVersion(String pVal) {
            statusline[0] = pVal;
            return this;
        }

        /**
         */
        public HttpHeader setHttpStatus(Object pVal) {
            String lStatus = getHttpStatusStringFor(pVal);
            if (!lStatus.isEmpty()) {
                statusline[1] = lStatus;
            }
            return this;
        }

        /**
         */
        public HttpHeader applyAttributes(Map<String, String> pAttributes) {
            fieldMap.putAll(pAttributes);
            return this;
        }

        /**
         */
        public Map<String, String> getAttributes() {
            return this.fieldMap;
        }

        /**
         */
        public String getContentType() {
            return get(Field.CONTENT_TYPE);
        }

        /**
         */
        public int getContentLength() {
            return Integer.valueOf(get(Field.CONTENT_LENGTH, "0"));
        }

        /**
         */
        public String getWebSocketKey() {
            return get(Field.SEC_WEBSOCKET_KEY);
        }

        /**
         */
        public boolean hasConnectionKeepAlive() {
            return has(Field.CONNECTION, FieldValue.KEEP_ALIVE);
        }

        /**
         */
        public HttpHeader setConnectionKeepAlive() {
            return set(Field.CONNECTION, FieldValue.KEEP_ALIVE);
        }

        /**
         */
        public HttpHeader setConnectionClose() {
            return set(Field.CONNECTION, FieldValue.CLOSE);
        }

        /**
         */
        public boolean hasContentType(String pVal) {
            return has(Field.CONTENT_TYPE, pVal);
        }

        /**
         */
        public HttpHeader setContentType(String pVal) {
            return set(Field.CONTENT_TYPE, pVal);
        }

        /**
         */
        public HttpHeader setContentLength(String pVal) {
            return set(Field.CONTENT_LENGTH, pVal);
        }

        /**
         */
        public HttpHeader setContentLength(int pVal) {
            return setContentLength(String.valueOf(pVal));
        }

        /**
         */
        public HttpHeader setConnection(String pVal) {
            return set(Field.CONNECTION, pVal);
        }

        /**
         */
        public HttpHeader setUpgrade(String pVal) {
            return set(Field.UPGRADE, pVal);
        }

        /**
         */
        public HttpHeader setServer(String pVal) {
            return set(Field.SERVER, pVal);
        }

        /**
         */
        public HttpHeader addSetCookie(String pVal) {
            getSetCookies().add(pVal);
            return this;
        }

        /**
         */
        public List<String> getSetCookies() {
            if (setCookies == null) {
                setCookies = new ArrayList<>();
            }
            return setCookies;
        }

        /**
         */
        public String getCookie() {
            return fieldMap.getOrDefault(Field.COOKIE, "");
        }

        /**
         */
        public List<String> getCookieAsList() {
            return Arrays.asList(fieldMap.getOrDefault(Field.COOKIE, "").split(";"));
        }

        /**
         */
        public String getMethod() {
            return fieldMap.getOrDefault(Field.HTTP_METHOD, "");
        }

        /**
         */
        public String getPath() {
            return fieldMap.getOrDefault(Field.HTTP_PATH, "");
        }

        /**
         */
        public String getHost() {
            return fieldMap.getOrDefault(Field.HOST, "");
        }

        /**
         */
        public String getOrigin() {
            return fieldMap.getOrDefault(Field.ORIGIN, "");
        }

        /**
         */
        public String getAuthorization() {
            return fieldMap.getOrDefault(Field.AUTHORIZATION, "");
        }

        /**
         */
        public String getAuthorizationBearer() {
            String lVal = getAuthorization();
            if (lVal.startsWith(Field.BEARER)) {
                String[] lToken = lVal.split(" ");
                return lToken.length == 2 ? lToken[1].trim() : "";
            }
            return "";
        }

        /**
         */
        public boolean isWebSocket() {
            return !getWebSocketKey().isEmpty();
        }

        /**
         */
        public boolean isMethod(String pType) {
            return getMethod().equalsIgnoreCase(pType);
        }

    }

    /**
     * <pre>
     * </pre>
     */
    public static class RequestMessage {
        protected HttpHeader httpHeader = null;
        protected String bodyContent = "";

        public RequestMessage(HttpHeader pHeader) {
            httpHeader = pHeader;
        }

        /**
         */
        public HttpHeader header() {
            return httpHeader;
        }

        /**
         */
        public String body() {
            return bodyContent;
        }

        /**
         */
        public void setBody(String pBody) {
            bodyContent = pBody;
        }

        /**
         */
        public int getContentLength() {
            return httpHeader.getContentLength();
        }

        /**
         */
        public String getContentType() {
            return httpHeader.getContentType();
        }

        /**
         */
        public String getEncoding() {
            return httpHeader.getEncoding();
        }

        /**
         */
        public String getPath() {
            return httpHeader.getPath();
        }

        /**
         */
        public String getMethod() {
            return httpHeader.getMethod();
        }

        /**
         */
        public boolean isMethod(String pVal) {
            return httpHeader.getMethod().equalsIgnoreCase(pVal);
        }

        /**
         */
        public boolean hasContentType(String pType) {
            return httpHeader.hasContentType(pType);
        }
    }

    /**
     * <pre>
     * </pre>
     */
    public static class ResponseMessage {
        protected List<String> contextData = new ArrayList<>();
        protected HttpHeader httpHeader = new HttpHeader();
        protected OutputStream outStream;
        protected ByteArrayOutputStream contentBuffer;
        protected String statusNr = "";
        protected boolean isProcessed = false;

        protected String encoding = StandardCharsets.UTF_8.name();

        public ResponseMessage(OutputStream pOutStream) {
            outStream = pOutStream;
        }

        public ResponseMessage(OutputStream pOutStream, HttpHeader pHeader) {
            outStream = pOutStream;
            httpHeader = pHeader;
        }

        protected ByteArrayOutputStream getContentBuffer() {
            if (contentBuffer == null) {
                contentBuffer = new ByteArrayOutputStream();
            }
            return contentBuffer;
        }

        public ResponseMessage addContextData(String pData) {
            contextData.add(pData);
            return this;
        }

        /**
         */
        public HttpHeader header() {
            return httpHeader;
        }

        /**
         */
        public ResponseMessage setStatus(String pVal) {
            statusNr = pVal;
            httpHeader.setHttpStatus(statusNr);
            return this;
        }

        /**
         */
        public String getStatus() {
            return statusNr;
        }

        /**
         */
        public void setProcessed() {
            isProcessed = true;
        }

        /**
         */
        public boolean isNotProcessed() {
            return !isProcessed;
        }

        /**
         */
        public ResponseMessage setContentType(String pVal) {
            httpHeader.setContentType(pVal);
            return this;
        }

        /**
         */
        public String getContentType() {
            return httpHeader.getContentType();
        }

        /**
         */
        public void writeToContent(byte[] pContent) throws IOException {
            getContentBuffer().write(pContent);
        }

        /**
         */
        public void send() throws IOException {
            writeOutResponse(outStream, getContentBuffer().toByteArray());
        }

        /**
         */
        public void sendStatus(String pStatus) throws IOException {
            setStatus(pStatus);
            writeOutResponse(outStream, null);
        }

        /**
         */
        public void close() throws IOException {
            outStream.flush();
        }

        /**
         * @throws IOException
         */
        protected void writeOutResponse(OutputStream pOut, byte[] pBody) throws IOException {
            byte[] lMessageBytes = createMessageBytesWithBody(pBody);

            LOG.fine(this::requestSummary);
            contextData.add(0, "<-- ALREADY SENT -->");
            pOut.write(lMessageBytes);
            pOut.flush();
        }

        /**
         */
        protected String requestSummary() {
            String lSocketId = !contextData.isEmpty() ? contextData.remove(0) : "";
            StringBuilder lText = new StringBuilder(LS);
            lText.append("<-- Request --> ").append(lSocketId).append(" - ").append(Thread.currentThread().getName())
                    .append(LS)
                    .append(String.join(LS, contextData)).append(LS)
                    .append("<-- Response -->").append(LS)
                    .append(httpHeader.toString().trim()).append(LS);
            return lText.toString();
        }

        /**
         * @throws IOException
         */
        protected byte[] createMessageBytesWithBody(byte[] pBody) throws IOException {
            ByteArrayOutputStream lMessage = new ByteArrayOutputStream();

            int lBodyLen = (pBody != null) ? pBody.length : 0;
            if (lBodyLen > 0) {
                httpHeader.setContentLength(lBodyLen);
            }

            byte[] lHeader = httpHeader.toMessageBytes(encoding);
            lMessage.write(lHeader);
            if (lBodyLen > 0) {
                lMessage.write(pBody);
            }
            return lMessage.toByteArray();
        }
    }

    /*********************************************************
     * <pre>
     * A properties configuration  class.
     * </pre>
     *********************************************************/
    /**
     */
    public static class Config {

        public static final String HTTP_ALLOW_ALL_CORS_ENABLED = "http.allow.all.cors.enabled";
        public static final String CLIENT_SOCKET_TIMEOUT = "client.socket.timeout";
        public static final String CONNECTION_KEEP_ALIVE = "connection.keep.alive";

        public static final String DEFAULT_CONFIG = String.join(LF,
                "##",
                "## " + JamnServerWebID + " Config Properties",
                "##", "",
                "#Server port", "port=8099", "",
                "#Max worker threads", "worker=5", "",
                "#Socket timeout in millis", "client.socket.timeout=500", "",
                "#Use Connection:keep-alive header", "connection.keep.alive=true", "",
                "#Encoding", "encoding=" + StandardCharsets.UTF_8.name(), "",
                "#A Global Cross origin flag\n#if=true ALL cors requests are allowed",
                HTTP_ALLOW_ALL_CORS_ENABLED + "=false", "");

        protected Properties props = new Properties();

        public Config() {
            this(DEFAULT_CONFIG);
        }

        public Config(String pDef) {
            this(buildPropertiesFrom(pDef));
        }

        public Config(Properties pProps) {
            props.putAll(pProps);
        }

        /**
         */
        public int getPort() {
            return Integer.valueOf(props.getProperty("port", "8099"));
        }

        /**
         */
        public Config setPort(int pPort) {
            props.setProperty("port", String.valueOf(pPort));
            return this;
        }

        /**
         */
        public int getActualPort() {
            return Integer.valueOf(props.getProperty("actual.port", "-1"));
        }

        /**
         */
        public Config setActualPort(int pPort) {
            props.setProperty("actual.port", String.valueOf(pPort));
            return this;
        }

        /**
         */
        public String getEncoding() {
            return props.getProperty("encoding", StandardCharsets.UTF_8.name());
        }

        /**
         */
        public int getWorkerNumber() {
            return Integer.valueOf(props.getProperty("worker", "5"));
        }

        /**
         */
        public int getClientSocketTimeout() {
            return Integer.valueOf(props.getProperty(CLIENT_SOCKET_TIMEOUT, "10000"));
        }

        /**
         */
        public boolean isAllowAllCORSEnabled() {
            return Boolean.parseBoolean(props.getProperty(HTTP_ALLOW_ALL_CORS_ENABLED, "false"));
        }

        /**
         */
        public void setAllowAllCORSEnabled(boolean pVal) {
            props.setProperty(HTTP_ALLOW_ALL_CORS_ENABLED, String.valueOf(pVal));
        }

        /**
         */
        public boolean isConnectionKeepAlive() {
            return Boolean.parseBoolean(props.getProperty(CONNECTION_KEEP_ALIVE, "false"));
        }

        /**
         */
        @Override
        public String toString() {
            return props.toString();
        }

        /**
         */
        public Config set(String pKey, String pVal) {
            props.setProperty(pKey, pVal);
            return this;
        }

        /**
         */
        public static Properties buildPropertiesFrom(String pDef) {
            Properties lProps = new Properties();
            try {
                lProps.load(new StringReader(pDef));
            } catch (IOException e) {
                LOG.severe(String.format("ERROR parsing config to properties string [%s] [%s]", pDef, e));
                throw new UncheckedJamnServerException("Config properties creation/initialization error");
            }
            return lProps;
        }
    }

    /**
     * <pre>
     * A simple class implementing template strings that include variable expressions.
     *
     * e.g. new ExprString(new HashMap<>())
     *          .put("visitor", "John")
     *          .put("me", "Andreas")
     *          .applyTo("Hello ${visitor} I'am ${me}");
     * result: "Hello John I'am Andreas"
     * </pre>
     */
    public static class ExprString {
        protected static String PatternStart = "${";
        protected static String PatternEnd = "}";
        // matches expressions like ${ name } accepting leading/ending whitespaces
        // BUT throwing RuntimeException - if name contains whitespaces
        protected static Pattern ExprPattern = Pattern.compile("\\$\\{\\s*([^\\s}]+)\\s*\\}");

        protected Map<String, String> values = null;
        protected ValueProvider provider = (String pKey, Object pCtx) -> "unknown";

        /**
         */
        public static String applyValues(String pTemplate, ValueProvider pProvider) {
            return applyValues(pTemplate, pProvider, null);
        }

        /**
         */
        public static String applyValues(String pTemplate, ValueProvider pProvider, Object pCtx) {
            StringBuilder lResult = new StringBuilder();
            String lPart = "";
            String lName = "";
            String lValue = "";
            Matcher lMatcher = ExprPattern.matcher(pTemplate);

            int lCurrentPos = 0;
            while (lMatcher.find()) {
                lPart = pTemplate.substring(lCurrentPos, lMatcher.start());
                lName = lMatcher.group().replace(PatternStart, "").replace(PatternEnd, "").trim();
                if (lName.contains(" ")) {
                    throw new UncheckedExprStringException(
                            String.format("ExprString contains whitespace(s) [%s]", lName));
                }
                lValue = pProvider.getValueFor(lName, pCtx);
                lResult.append(lPart).append(lValue);
                lCurrentPos = lMatcher.end();
            }
            if (lCurrentPos < pTemplate.length()) {
                lPart = pTemplate.substring(lCurrentPos, pTemplate.length());
                lResult.append(lPart);
            }
            return lResult.toString();
        }

        /**
         */
        protected ExprString() {
        }

        /**
         */
        public ExprString(ValueProvider pProvider) {
            provider = pProvider;
        }

        /**
         */
        public ExprString(Map<String, String> pMap) {
            values = pMap;
            provider = (String pKey, Object pCtx) -> values.getOrDefault(pKey, "");
        }

        /**
         */
        public ExprString put(String pKey, String pVal) {
            values.put(pKey, pVal);
            return this;
        }

        /**
         */
        public String applyTo(String pTemplate) {
            return applyTo(pTemplate, null);
        }

        /**
         */
        public String applyTo(String pTemplate, Object pCtx) {
            return applyValues(pTemplate, this.provider, pCtx);
        }

        /**
         * The Value Provider provides the values for the expression substitution.
         */
        public static interface ValueProvider {
            String getValueFor(String pKey, Object pCtx);
        }

        /**
        */
        public static class UncheckedExprStringException extends RuntimeException {
            private static final long serialVersionUID = 1L;

            public UncheckedExprStringException(String pMsg) {
                super(pMsg);
            }
        }
    }

    /*********************************************************
     * <pre>
     * Common public static helper methods.
     * </pre>
     *********************************************************/
    /**
     */
    public static String getStackTraceFrom(Throwable t) {
        if (t instanceof InvocationTargetException te) {
            t = te.getTargetException();
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    /*********************************************************
     * <pre>
     * Jamn Exceptions.
     * </pre>
     *********************************************************/
    /**
     */
    public static class UncheckedJamnServerException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedJamnServerException(Throwable pCause, String pMsg) {
            super(pMsg, pCause);
        }

        public UncheckedJamnServerException(String pMsg) {
            super(pMsg);
        }

    }

    /**
     */
    public static class UncheckedJsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public static final String TOOBJ_ERROR = "JSON: string parse to object error";
        public static final String TOJSON_ERROR = "JSON: object write as json string error";
        public static final String PRETTIFY_ERROR = "JSON: prettiying json string error";

        public UncheckedJsonException(String pMsg, Throwable pCause) {
            super(pMsg, pCause);
        }

        public UncheckedJsonException(String pMsg) {
            super(pMsg);
        }

    }

}
