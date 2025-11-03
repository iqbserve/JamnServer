/* Authored by iqbserve.de */
package org.isa.ipc.sample;

import java.util.logging.Logger;

import org.isa.ipc.JamnServer;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * A file enricher value-provider sample to show a server side templating mechanism.
 * </pre>
 */
public class FileEnricherValueProvider implements JamnServer.ExprString.ValueProvider {

    protected static final String LS = System.lineSeparator();
    protected static Logger LOG = Logger.getLogger(FileEnricherValueProvider.class.getName());
    protected static ObjectMapper JSON = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

    @Override
    public String getValueFor(String pKey, Object pCtx) {
        try {
            if ("app.title".equals(pKey)) {
                return "JamnWeb Sample";
            }
            if ("server.info".equals(pKey)) {
                ServerInfo lInfo = new ServerInfo()
                        .setName("JamnServer - Sample WebContent App")
                        .setVersion("0.0.1-SNAPSHOT")
                        .setDescription(
                                "A simple Web Content Provider sample app.")
                        .addLink("app.scm",
                                "https://github.com/iqbserve/JamnServer/blob/master/org.isa.ipc.JamnWebContentProvider");
                return JSON.writeValueAsString(lInfo);
            }
        } catch (Exception e) {
            LOG.severe(() -> String.format("ERROR retrieving template value for: [%s] in context [%s] %s%s%s", pKey,
                    pCtx, e, LS,
                    getStackTraceFrom(e)));
        }

        LOG.fine(() -> String.format("No template value for: [%s] in context [%s]", pKey, pCtx));
        return "";
    }

    /**
     */
    private static String getStackTraceFrom(Throwable t) {
        return JamnServer.getStackTraceFrom(t);
    }

}
