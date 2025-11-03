/* Authored by iqbserve.de */
package org.isa.ipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.isa.ipc.JamnWebContentProvider.DefaultFileEnricher;
import org.isa.ipc.sample.FileEnricherValueProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JamnWebContentProvider Unit test.
 */
@DisplayName("Jamn Server WebContentProvider Test")
class WebContentProviderTest {

    private static HttpClient Client;
    private static JamnServer Server;
    private static String ServerURL;

    @BeforeAll
    static void setupEnvironment() {
        // create standard Java SE HTTP Client
        Client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // create a JamnServer
        Server = new JamnServer(8099);
        // define a server base url variable for the tests
        // e.g. default: http://localhost:8099
        ServerURL = "http://localhost:" + Server.getConfig().getPort();

        // create the JamnWebContentProvider with a webroot
        // no leading slash because relative path
        JamnWebContentProvider lWebContentProvider = new JamnWebContentProvider("src/test/resources/http/sample")
                .setConfig(Server.getConfig())
                .setFileEnricher(new DefaultFileEnricher(new FileEnricherValueProvider()));
        // add to server
        Server.addContentProvider("WebContentProvider", lWebContentProvider);

        // start server
        Server.start();
        assertTrue(Server.isRunning(), "Error Test Server start");
    }

    @AfterAll
    static void shutDownServer() {
        Server.stop();
    }

    @Test
    void testRoot() throws Exception {
        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI(ServerURL + "/"))
                .headers("Content-Type", "text/html").GET().build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());

        assertEquals(200, lResponse.statusCode(), "Error HTTP Status");
    }

}
