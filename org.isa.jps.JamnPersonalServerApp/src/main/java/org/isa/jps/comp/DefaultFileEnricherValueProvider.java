/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.isa.ipc.JamnServer.ExprString.ValueProvider;
import org.isa.ipc.JamnWebContentProvider.WebFile;
import org.isa.jps.JamnPersonalServerApp.Config;

/**
 * <pre>
 * A rudimentary sample implementation for a web file enricher
 * e.g. to do server side html code component injection.
 * see sample: http/jsmod/html-components and system-infos.html 
 * injecting header html
 * </pre>
 */
public class DefaultFileEnricherValueProvider implements ValueProvider {

    protected Path appHome;
    protected Config config;
    protected String componentsRootPath;

    protected Properties values = new Properties();

    /**
     */
    public DefaultFileEnricherValueProvider(Path pAppHome, Config pConfig) {
        appHome = pAppHome;
        config = pConfig;

        if (config.getWebFileEnricherRoot().startsWith("/")) {
            // assume that a configured absolute path exists
            componentsRootPath = config.getWebFileEnricherRoot();
        } else {
            // ensure a relative web file root folder
            componentsRootPath = Path.of(appHome.toString(), config.getWebFileEnricherRoot())
                    .toString();
        }
    }

    /**
     */
    @Override
    public String getValueFor(String pKey, Object pCtx) {
        WebFile lWebFile = (WebFile) pCtx;
        Path lFilePath;
        String lValue = "NO VALUE FOUND for: " + pKey;

        // assume ".html" points to a html template
        // that gets injected into the requested file - see system-infos.html
        if (pKey.endsWith(".html")) {
            lFilePath = getComponentFilePathFor(pKey, lWebFile);
            lValue = getFileContent(lFilePath);
        } else if (values.containsKey(pKey)) {
            lValue = values.getProperty(pKey);
        }

        return lValue;
    }

    /**
     */
    public DefaultFileEnricherValueProvider addValue(String pKey, String pValue) {
        values.setProperty(pKey, pValue);
        return this;
    }

    /**
     */
    protected Path getComponentFilePathFor(String pName, WebFile lWebFile) {
        return Path.of(componentsRootPath, pName);
    }

    /**
     */
    protected String getFileContent(Path pFile) {
        String lContent = "";
        byte[] lBytes;
        try {
            lBytes = Files.readAllBytes(pFile);
            lContent = new String(lBytes);
        } catch (IOException e) {
            throw new RuntimeException(String.format("ERROR reading file enriching value [%s] [%s]", pFile, e));
        }
        return lContent;
    }
}
