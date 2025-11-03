/* Authored by iqbserve.de */
package org.isa.ipc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import org.isa.ipc.JamnServer.UncheckedJamnServerException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * A basic Unit test.
 */
@DisplayName("Starting, Calling and Stopping a JamnServer")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JamnServerBasicTest {

    private static JamnServer Server;

    @Test
    @Order(1)
    void testCreateAndStartServer() throws UncheckedJamnServerException {
        Server = new JamnServer(8099);
        Server.start();

        assertTrue(Server.isRunning(), "Error Server start");
    }

    @Test
    @Order(2)
    void testGETRquest() throws Exception {
        // java jdk http client
        HttpClient lClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI("http://localhost:8099/")).build();

        HttpResponse<String> lResponse = lClient.send(lRequest, BodyHandlers.ofString());

        // HTTP 200 ok
        assertEquals(200, lResponse.statusCode(), "Error HTTP Status");

        // return blank-server.html
        assertTrue(lResponse.body().contains("No Content Provider installed"), "Error blank-server.html content");
    }

    @Test
    @Order(3)
    void testStopServer() {
        Server.stop();

        assertFalse(Server.isRunning());
    }

    @AfterAll
    static void shutDownServer() {
        if (Server.isRunning()) {
            Server.stop();
        }
    }

}
