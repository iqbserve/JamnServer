/* Authored by iqbserve.de */
package org.isa.ipc;

import static org.isa.ipc.JamnServer.HttpHeader.FieldValue.APPLICATION_JSON;
import static org.isa.ipc.JamnServer.HttpHeader.FieldValue.TEXT_HTML;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.isa.ipc.sample.RudimentaryContentProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * <pre>
 * A rudimentary content provider Unit test.
 * 
 * To do a manually test start the sample/SampleJamnServerApp
 * and drop the file: 
 *  - browser-js-fetch-with-json-test.html
 *  or the address
 *  - http://localhost:8099/info
 * into a browser window
 * </pre>
 */
@DisplayName("Adding and Calling a simple content provider")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RudimentaryContentProviderTest {

    private static HttpClient Client;
    private static JamnServer Server;
    private static String ServerURL;

    private static String ExpectedJsonResponseMessage = RudimentaryContentProvider.TestJsonResponseMessage;
    private static String ExpectedHtmlTitleTag = "<title>JamnServer Info</title>";

    @BeforeAll
    static void setupEnvironment() throws Exception {
        // create standard Java SE HTTP Client
        Client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // create a JamnServer
        Server = new JamnServer(8099);
        // define a server base url variable for the tests
        // e.g. default: http://localhost:8099
        ServerURL = "http://localhost:" + Server.getConfig().getPort();

        // add the sample content provider
        Server.addContentProvider("ContentProvider", new RudimentaryContentProvider());

        // enable all CORS - simplification for manually browser js fetch via local html file
        Server.getConfig().setAllowAllCORSEnabled(true);

        Server.start();
    }

    @Test
    @Order(1)
    void testGETInfoRquest() throws Exception {

        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI(ServerURL + "/info"))
                .headers("Content-Type", TEXT_HTML)
                .GET().build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());

        assertEquals(200, lResponse.statusCode(), "Error HTTP Status");

        assertTrue(lResponse.body().contains(ExpectedHtmlTitleTag), "Error expected response");
    }

    @Test
    @Order(2)
    void testJsonPOSTRquest() throws Exception {
        String lMessage = "{\"user\": \"John\", \"message\": \"Hello Server\"}";

        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI(ServerURL + "/wsapi"))
                .headers("Content-Type", APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(lMessage)).build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());

        assertEquals(200, lResponse.statusCode(), "Error HTTP Status");

        assertEquals(ExpectedJsonResponseMessage, lResponse.body(), "Error expected response");
    }

    @AfterAll
    static void shutDownServer() {
        Server.stop();
    }
}
