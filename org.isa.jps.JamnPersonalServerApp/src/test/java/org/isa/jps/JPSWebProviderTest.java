/* Authored by iqbserve.de */
package org.isa.jps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.jps.comp.DefaultWebServices.ShellRequest;
import org.isa.jps.comp.DefaultWebServices.ShellResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 */
@DisplayName("JPS WebProvider Test")
class JPSWebProviderTest {

    private static Logger LOG = Logger.getLogger(JPSWebProviderTest.class.getName());

    private static JamnPersonalServerApp ServerApp;
    private static String ServerURL;
    private static HttpClient Client;
    private static JsonToolWrapper Json;

    @BeforeAll
    static void setupEnvironment() {
        ServerApp = JamnPersonalServerApp.getInstance();
        ServerApp.start(new String[] {});
        assertTrue(ServerApp.isRunning(), "Test Server start FAILED");

        Json = ServerApp.getJsonTool();
        ServerURL = "http://localhost:" + ServerApp.getConfig().getPort();

        Client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    }

    @AfterAll
    static void shutDownServer() {
        ServerApp.close();
        LOG.info("Test(s) finished");
    }

    @Test
    void testHttpWebRoot() throws Exception {
        HttpRequest lRequest = HttpRequest.newBuilder().uri(new URI(ServerURL + "/"))
                .headers("Content-Type", "text/html").GET().build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());

        assertEquals(200, lResponse.statusCode(), "Error HTTP Status");
    }

    @Test
    void testShellWebService() throws Exception {
        ShellRequest lShellRequest = new ShellRequest("dir").setWorkingDir("/");
        String lData = Json.toString(lShellRequest);

        HttpRequest lRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(lData))
                .uri(new URI(ServerURL + "/webapi/service/shell-cmd"))
                .headers("Content-Type", "application/json")
                .build();

        HttpResponse<String> lResponse = Client.send(lRequest, BodyHandlers.ofString());
        assertEquals(200, lResponse.statusCode(), "Error http status");

        lData = lResponse.body();
        ShellResponse lShellResponse = Json.toObject(lData, ShellResponse.class);
        LOG.info(lShellResponse.getOutput().iterator().next());
    }
}
