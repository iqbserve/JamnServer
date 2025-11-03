/* Authored by iqbserve.de */
package org.isa.ipc.sample;

import org.isa.ipc.JamnServer;
import org.isa.ipc.JamnWebContentProvider;
import org.isa.ipc.JamnWebContentProvider.DefaultFileEnricher;

/**
 * <pre>
 * Run the JamnServer with the JamnWebContentProvider.
 *  - browser call:  http://localhost:8099
 * </pre>
 */
public class WebContentProviderSampleApp {

    /**
     */
    public static void main(String[] args) {
        // create a Jamn server
        JamnServer lServer = new JamnServer();

        // create the provider with a webroot
        // no leading slash because relative path
        JamnWebContentProvider lWebContentProvider = new JamnWebContentProvider("src/test/resources/http/sample")
                .setConfig(lServer.getConfig())
                .setFileEnricher(new DefaultFileEnricher(new FileEnricherValueProvider()));
        // add it to server
        lServer.addContentProvider("WebContentProvider", lWebContentProvider);

        // start server
        lServer.start();

    }

}
