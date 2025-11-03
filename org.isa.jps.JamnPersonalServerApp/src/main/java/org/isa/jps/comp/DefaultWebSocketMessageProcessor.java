/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.ExprString;
import org.isa.ipc.JamnServer.ExprString.ValueProvider;
import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.ipc.JamnWebSocketProvider;
import org.isa.ipc.JamnWebSocketProvider.WsoMessageProcessor;
import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.CommonHelper;
import org.isa.jps.JamnPersonalServerApp.Config;
import org.isa.jps.JavaScriptProvider;
import org.isa.jps.JavaScriptProvider.JSCallContext;
import org.isa.jps.comp.ExtensionHandler.ExtensionCallContext;

/**
 * <pre>
 * Example of a server side websocket message listener to execute commands.
 * 
 * A user counterpart on the web client side is 
 *  - command.mjs.
 * </pre>
 */
public class DefaultWebSocketMessageProcessor implements WsoMessageProcessor {

    protected static final String LS = System.lineSeparator();
    protected static final Logger LOG = Logger.getLogger(DefaultWebSocketMessageProcessor.class.getName());
    protected static final CommonHelper Tool = new CommonHelper();

    protected static final String CMD_RUNJS = "runjs";
    protected static final String CMD_RUNEXT = "runext";
    protected static final String STATUS_SUCCESS = "success";
    protected static final String STATUS_ERROR = "error";
    protected static final String SERVER_GLOBAL_REF = "server.global";

    protected String headStartMark = "<";
    protected String headEndMark = ">";
    protected int maxHeadLen = 100;
    protected int logSnippetLen = 512;

    protected Config config;
    protected JsonToolWrapper json;
    protected AtomicBoolean isAvailable;

    protected JavaScriptProvider jsProvider;
    protected ExtensionHandler extensionHandler;
    protected JamnWebSocketProvider wsoProvider;

    // websocket messages are always utf8 encoded by spec
    protected Charset encoding = StandardCharsets.UTF_8;

    public DefaultWebSocketMessageProcessor(Config pConfig, JsonToolWrapper pJson, JamnWebSocketProvider pWsoProvider) {
        config = pConfig;
        json = pJson;
        wsoProvider = pWsoProvider;
        isAvailable = new AtomicBoolean(true);
    }

    /**
     * <pre>
     * Jamn uses a simple, universal string message format at the app level.
     * A message consists of
     * - an optional <header> max 100 byte marked by <...> start/end delimiter
     *   including a client sender reference 
     * - the body is expected to be json representing a WsoCommonMessage object
     * e.g. "<client_ref>{"field-name":"value" ...}" 
     * 
     * The method extracts the two parts from the given message byte array.
     * </pre>
     */
    protected String[] createMessageHeadAndBodyParts(byte[] pSrc, Integer pSearchAreaMaxLen) {
        String[] lParts = new String[2]; // header, body
        String lMsgSrc = "";
        int idx = -1;
        byte[] lBuffer;

        if (pSearchAreaMaxLen != null) {
            int len = pSrc.length < pSearchAreaMaxLen ? pSrc.length : pSearchAreaMaxLen;
            lBuffer = new byte[len];
            System.arraycopy(pSrc, 0, lBuffer, 0, len);
        } else {
            lBuffer = pSrc;
        }
        lMsgSrc = new String(lBuffer, encoding);

        if (lMsgSrc.startsWith(headStartMark) && lMsgSrc.length() > maxHeadLen
                && (idx = lMsgSrc.substring(0, maxHeadLen).indexOf(headEndMark)) != -1) {
            lParts[0] = lMsgSrc.substring(0, idx + 1);
            lParts[0] = lParts[0].replace(headStartMark, "").replace(headEndMark, ""); // header present
        }
        if (lParts[0] != null) {
            lParts[1] = lMsgSrc.substring(idx + 1, lMsgSrc.length());
        } else {
            lParts[0] = ""; // no header
            lParts[1] = lMsgSrc;
        }

        return lParts;
    }

    /**
     * The WsoMessageProcessor Interface
     */
    @Override
    public byte[] onMessage(String pConnectionId, byte[] pMessage) {
        String lJsonResponse = "";
        String[] lParts = new String[2];
        WsoCommonMessage lRequestMessage;
        WsoCommonMessage lResponseMsg = null;
        int lSnipLen = logSnippetLen;

        try {
            lParts = createMessageHeadAndBodyParts(pMessage, null);

            // log infos and a snippet of the body payload
            lSnipLen = lParts[1].length() > logSnippetLen ? logSnippetLen : lParts[1].length();
            String text = String.format("WebSocket Message received: id[%s] - head[%s] - snippet[%s ...]",
                    pConnectionId,
                    lParts[0],
                    lParts[1].substring(0, lSnipLen));

            LOG.info(() -> text);

            lRequestMessage = json.toObject(lParts[1], WsoCommonMessage.class);
            lResponseMsg = onMessage(pConnectionId, lRequestMessage);
        } catch (Exception e) {
            lResponseMsg = new WsoCommonMessage(lParts[0]);
            lResponseMsg.setError(e.getMessage());
            lResponseMsg.setStatus(STATUS_ERROR);
        }

        if (lResponseMsg != null) {
            lJsonResponse = json.toString(lResponseMsg);
        }
        return lJsonResponse.getBytes(encoding);
    }

    /**
     */
    @Override
    public byte[] onError(String pConnectionId, byte[] pMessage, Exception pExp, AtomicBoolean pClose) {
        String[] lParts = createMessageHeadAndBodyParts(pMessage, 1024);

        // if no client reference is available - set to global
        // and close wso connection
        String lRef = lParts[0].isEmpty() ? SERVER_GLOBAL_REF : lParts[0];
        if (SERVER_GLOBAL_REF.equals(lRef)) {
            pClose.set(true);
        }

        String t1 = pClose.get() ? "Fatal WSO Error" : "WSO Error";
        String t2 = pClose.get() ? "Connection will be closed!" : "";
        String lError = String.format("%s [%s] %s%s", t1, pExp.getMessage(), LS, t2);
        LOG.severe(() -> String.format("%s - ref[%s]", lError, lRef));

        WsoCommonMessage lResponseMsg = new WsoCommonMessage(lRef)
                .setStatus(STATUS_ERROR)
                .setError(lError);
        String lJsonResponse = json.toString(lResponseMsg);

        return lJsonResponse.getBytes(encoding);
    }

    /**
     * The internal processing implementation
     */
    protected WsoCommonMessage onMessage(String pConnectionId, WsoCommonMessage pRequestMsg) {
        WsoCommonMessage lResponseMsg = new WsoCommonMessage(pRequestMsg.getReference());

        // limits message processing to - ONE message at ONE time
        if (isAvailable.compareAndSet(true, false)) {
            try {
                if (CMD_RUNJS.equalsIgnoreCase(pRequestMsg.getCommand())) {
                    runJSCommand(pConnectionId, pRequestMsg);
                    lResponseMsg.setStatus(STATUS_SUCCESS);
                } else if (CMD_RUNEXT.equalsIgnoreCase(pRequestMsg.getCommand())) {
                    runExtCommand(pConnectionId, pRequestMsg);
                    lResponseMsg.setStatus(STATUS_SUCCESS);
                } else {
                    throw new UncheckedWsoProcessorException(
                            String.format("Unsupported command [%s]", pRequestMsg.getCommand()));
                }
            } catch (Exception e) {
                lResponseMsg.setStatus(STATUS_ERROR);
                lResponseMsg.setError(String.format("ERROR processing wso command request [%s] [%s]%s%s", pRequestMsg,
                        pConnectionId,
                        LS, Tool.getStackTraceFrom(e)));
                LOG.severe(lResponseMsg.getError());
            } finally {
                isAvailable.set(true);
            }
        }

        return lResponseMsg;
    }

    /**
     */
    protected String[] parseArgsFrom(String pArgsSrc, Map<String, String> pMsgData) {
        String[] lArgs = Tool.parseCommandLine(pArgsSrc);

        //create an ExprString value provider
        ValueProvider lProvider = (String pKey, Object pCtx) -> {
            String value = pMsgData.getOrDefault(pKey, "");
            //also accept indices
            if (value.isEmpty() && Character.isDigit(pKey.trim().charAt(0))) {
                int idx = Integer.parseInt(pKey.trim().substring(0, 1)) - 1;
                String[] data = pMsgData.values().toArray(new String[] {});
                if (idx >= 0 && idx < data.length) {
                    value = data[idx];
                }
            }
            return value;
        };

        //replace ${name} expressions with data from the message e.g. file content
        for (int i = 0; i < lArgs.length; i++) {
            lArgs[i] = ExprString.applyValues(lArgs[i], lProvider);
        }
        return lArgs;
    }

    /**
     */
    protected void runJSCommand(String pConnectionId, WsoCommonMessage pRequestMsg) {

        WsoCommonMessage lOutputMsg = new WsoCommonMessage(pRequestMsg.getReference());

        JSCallContext lCallCtx = new JSCallContext((String output) -> {
            lOutputMsg.setBodydata(output);
            String lJsonMsg = json.toString(lOutputMsg);
            wsoProvider.sendMessageTo(pConnectionId, lJsonMsg.getBytes(encoding));
        });
        js().run(lCallCtx, pRequestMsg.getFunctionModule(), parseArgsFrom(pRequestMsg.getArgsSrc(), pRequestMsg.getAttachments()));
        if (lCallCtx.getResult() != null && !lCallCtx.getResult().isEmpty() && lCallCtx.getOutputConsumer() != null) {
            String lResultPrint = Tool.formatCommandReturn(lCallCtx.getResult());
            lCallCtx.getOutputConsumer().accept(lResultPrint);
        }
    }

    /**
     */
    protected void runExtCommand(String pConnectionId, WsoCommonMessage pRequestMsg) {

        WsoCommonMessage lOutputMsg = new WsoCommonMessage(pRequestMsg.getReference());

        ExtensionCallContext lCallCtx = new ExtensionCallContext((String output) -> {
            lOutputMsg.setBodydata(output);
            String lJsonMsg = json.toString(lOutputMsg);
            wsoProvider.sendMessageTo(pConnectionId, lJsonMsg.getBytes(encoding));
        });
        ext().run(lCallCtx, pRequestMsg.getFunctionModule(), parseArgsFrom(pRequestMsg.getArgsSrc(), pRequestMsg.getAttachments()));
        if (lCallCtx.getResult() != null && !lCallCtx.getResult().isEmpty() && lCallCtx.getOutputConsumer() != null) {
            String lResultPrint = Tool.formatCommandReturn(lCallCtx.getResult());
            lCallCtx.getOutputConsumer().accept(lResultPrint);
        }
    }

    /**
     */
    protected ExtensionHandler ext() {
        if (extensionHandler == null) {
            extensionHandler = JamnPersonalServerApp.getInstance().getExtensionHandler();
            if (extensionHandler == null) {
                throw new UncheckedWsoProcessorException("ERROR Extension Handler NOT available");
            }
        }
        return extensionHandler;
    }

    /**
     */
    protected JavaScriptProvider js() {
        if (jsProvider == null) {
            jsProvider = JamnPersonalServerApp.getInstance().getJavaScript();
            if (jsProvider == null) {
                throw new UncheckedWsoProcessorException("ERROR JavaScript NOT available");
            }
        }
        return jsProvider;
    }

    /**
     * A simple general message data structure for web socket communication.
     */
    public static class WsoCommonMessage {

        //header data
        protected String reference = "";
        protected String command = "";
        protected String functionModule = "";
        protected String argsSrc = "";
        protected String status = "";
        protected String error = "";
        //payload
        protected String bodydata = "";
        protected Map<String, String> attachments = new LinkedHashMap<>();

        public WsoCommonMessage() {
        }

        public WsoCommonMessage(String reference) {
            this.reference = reference;
        }

        @Override
        public String toString() {
            return String.join(", ", reference, command, functionModule);
        }

        public String getReference() {
            return reference;
        }

        public String getBodydata() {
            return bodydata;
        }

        public String getCommand() {
            return command;
        }

        public String getArgsSrc() {
            return argsSrc;
        }

        public String getFunctionModule() {
            return functionModule;
        }

        public String getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public Map<String, String> getAttachments() {
            return attachments;
        }

        public WsoCommonMessage setReference(String reference) {
            this.reference = reference;
            return this;
        }

        public WsoCommonMessage setBodydata(String textdata) {
            this.bodydata = textdata;
            return this;
        }

        public WsoCommonMessage setCommand(String command) {
            this.command = command;
            return this;
        }

        public WsoCommonMessage setStatus(String status) {
            this.status = status;
            return this;
        }

        public WsoCommonMessage setError(String error) {
            this.error = error;
            return this;
        }

        public WsoCommonMessage addAttachment(String pKey, String pVal) {
            this.attachments.put(pKey, pVal);
            return this;
        }
    }

    /**
     */
    public static class UncheckedWsoProcessorException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UncheckedWsoProcessorException(String pMsg) {
            super(pMsg);
        }

        UncheckedWsoProcessorException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

}
