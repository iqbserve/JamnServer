/* Authored by iqbserve.de */
package org.isa.ipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.ipc.JamnServer.UncheckedJsonException;
import org.isa.ipc.sample.web.api.SampleWebApiServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JamnWebServiceProvider Unit test.
 */
@DisplayName("Jamn Server WebServiceProvider Test")
class WebServiceProviderTest {

    private static HttpClient Client;
    private static JamnServer Server;
    private static String ServerURL;

    // JSON Tool
    private static JsonToolWrapper Jack = new JamnServer.JsonToolWrapper() {
        private final ObjectMapper jack = new ObjectMapper()
                .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
                .setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);

        @Override
        public <T> T toObject(String pSrc, Class<T> pType) throws UncheckedJsonException {
            try {
                return jack.readValue(pSrc, pType);
            } catch (JsonProcessingException e) {
                throw new UncheckedJsonException(UncheckedJsonException.TOOBJ_ERROR, e);
            }
        }

        @Override
        public String toString(Object pObj) {
            try {
                return jack.writeValueAsString(pObj);
            } catch (JsonProcessingException e) {
                throw new UncheckedJsonException(UncheckedJsonException.TOJSON_ERROR, e);
            }
        }
    };

    @BeforeAll
    static void setupEnvironment() throws Exception {
        // create standard Java SE HTTP Client
        Client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // create a JamnServer
        Server = new JamnServer(8099);
        // define a server base url variable for the tests
        // e.g. default: http://localhost:8099
        ServerURL = "http://localhost:" + Server.getConfig().getPort();

        // create the WebService provider
        JamnWebServiceProvider lWebServiceProvider = new JamnWebServiceProvider()
                .setJsonTool(Jack)
                // register the Web-API Services
                .registerServices(SampleWebApiServices.class);

        // add the provider to the server
        Server.addContentProvider("WebServiceProvider", lWebServiceProvider);
        // start server
        Server.start();
        assertTrue(Server.isRunning(), "Test Server start FAILED");
    }

    @AfterAll
    static void shutDownServer() {
        Server.stop();
    }

    @Test
    void testApiEcho() throws Exception {
        String lMessage = "Client message for JamnServer API";

        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI(ServerURL + "/api/echo"))
                .headers("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(lMessage)).build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());

        assertEquals(200, lResponse.statusCode(), "HTTP Status");
        assertEquals("ECHO: " + lMessage, lResponse.body(), "HTTP Body");
    }

    @Test
    void testGETApiAboutErrorNotFound() throws Exception {
        // Error Case - test for unknown service path
        // status: 404 - Not found
        // log: WebService API Error: [Unsupported WebService Path [/about]]
        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI(ServerURL + "/about"))
                .headers("Content-Type", "application/json").GET().build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());
        assertEquals(404, lResponse.statusCode(), "HTTP Status");
    }

    @Test
    void testGETApiAbout() throws Exception {
        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI(ServerURL + "/api/about"))
                .headers("Content-Type", "application/json").GET().build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());

        assertEquals(200, lResponse.statusCode(), "HTTP Status");

        SampleWebApiServices.AboutResponse lAbout = Jack.toObject(lResponse.body(),
                SampleWebApiServices.AboutResponse.class);
        assertEquals("0.0.1", lAbout.version, "AboutResponse.version");
    }
}
