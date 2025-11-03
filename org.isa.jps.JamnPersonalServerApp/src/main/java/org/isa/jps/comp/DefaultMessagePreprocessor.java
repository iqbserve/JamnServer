package org.isa.jps.comp;

import org.isa.ipc.JamnServer.HttpHeader.Field;
import org.isa.ipc.JamnServer.HttpHeader.Status;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.RequestMessagePreprocessor;
import org.isa.ipc.JamnServer;
import org.isa.ipc.JamnServer.RequestMessage;
import org.isa.ipc.JamnServer.ResponseMessage;
import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.AppEvent;
import org.isa.jps.JamnPersonalServerApp.Config;
import org.isa.jps.JamnPersonalServerApp.ServerAppNotificationListener;

/**
 * <pre>
 * </pre>
 */
public class DefaultMessagePreprocessor implements RequestMessagePreprocessor, ServerAppNotificationListener {

    protected static final String LS = System.lineSeparator();
    protected static final Logger LOG = Logger.getLogger(DefaultMessagePreprocessor.class.getName());

    protected JamnServer.JsonToolWrapper json;
    protected Config appConfig;
    protected URI serverURI = null;

    protected HttpPreprocessConfig httpConfig;

    /**
     */
    public DefaultMessagePreprocessor(Config pAppConfig, JamnServer.JsonToolWrapper pJsonTool) throws IOException {
        appConfig = pAppConfig;
        json = pJsonTool;
        initialize();
    }

    /**
     */
    protected void initialize() throws IOException {
        JamnPersonalServerApp lApp = JamnPersonalServerApp.getInstance();

        String lJsonSource = lApp.loadOrCreateConfigFile("http.preprocess.json",
                json.toString(new HttpPreprocessConfig()));
        httpConfig = json.toObject(lJsonSource, HttpPreprocessConfig.class);

        lApp.addNotificationListener(this);
    }

    /**
     */
    @Override
    public void onServerAppEvent(AppEvent pEvent) {
        if (pEvent.msg().equals("started")) {
            serverURI = pEvent.app().getServer().getURI();
            if (httpConfig.cors.allowOrigin.contains("localhost")) {
                httpConfig.cors.allowOrigin.remove("localhost");
                httpConfig.cors.allowOrigin.add(serverURI.toString());
            }
        }
    }

    /**
     */
    @Override
    public void processRequest(RequestMessage pRequest, ResponseMessage pResponse)
            throws IOException, SecurityException {

        handleCors(pRequest, pResponse);
        handleCookies(pRequest, pResponse);
    }

    /**
     */
    protected void handleCors(RequestMessage pRequest, ResponseMessage pResponse) throws IOException {
        String reqOrigin = pRequest.header().getOrigin();
        if (!reqOrigin.isEmpty() && httpConfig.cors.allowOrigin.contains(reqOrigin)) {
            pResponse.header().set(Field.ACCESS_CONTROL_ALLOW_ORIGIN, reqOrigin);
            if (pRequest.isMethod("OPTIONS")) {
                pResponse.header().set(Field.ACCESS_CONTROL_ALLOW_METHODS, httpConfig.cors.allowMethods);
                pResponse.header().set(Field.ACCESS_CONTROL_ALLOW_HEADERS, httpConfig.cors.allowHeaders);
                pResponse.setProcessed();
                pResponse.sendStatus(Status.SC_204_NO_CONTENT);
            }
        }
    }

    /**
     */
    protected void handleCookies(RequestMessage pRequest, ResponseMessage pResponse) {
        if (pRequest.header().getCookie().isEmpty()) {
            String lJamnCookie = "jamn.server=" + UUID.randomUUID().toString();
            lJamnCookie = String.join(";", new String[]{lJamnCookie, "Secure", "HttpOnly"});
            pResponse.header().addSetCookie(lJamnCookie);
        }
    }

    /**
     */
    protected static class HttpPreprocessConfig {
        public CorsConfig cors = new CorsConfig();

        protected static class CorsConfig {
            public Set<String> allowOrigin = new HashSet<>();
            public String allowMethods = "";
            public String allowHeaders = "";
        }
    }
}
