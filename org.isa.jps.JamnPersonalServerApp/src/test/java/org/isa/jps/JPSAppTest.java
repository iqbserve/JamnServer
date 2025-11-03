/* Authored by iqbserve.de */
package org.isa.jps;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 */
@DisplayName("JPS App Test")
class JPSAppTest {

    private static Logger LOG = Logger.getLogger(JPSAppTest.class.getName());

    private static JamnPersonalServerApp ServerApp;
    private static JsonToolWrapper Json;

    @BeforeAll
    static void setupEnvironment() {
        ServerApp = JamnPersonalServerApp.getInstance();

        ServerApp.start(new String[] {
                "javascript.enabled=false",
                "cli.enabled=true"
        });
        assertTrue(ServerApp.isRunning(), "Test Server start FAILED");

        Json = ServerApp.getJsonTool();
        assertNotNull(Json, "Json init FAILED");
    }

    @AfterAll
    static void shutDownServer() {
        ServerApp.close();
        LOG.info("Test(s) finished");
    }

    @Test
    void testCliBlankCommand() {
        String lVal = ServerApp.getCli().execCmdBlank("list config");
        assertTrue(lVal.contains("javascript.enabled=false"), "Error Cli command");
    }

    @Test
    void testShellCall() {
        String lVal = String.join("\n", ServerApp.getOsIFace().fnc().shellCmd(new String[] { "dir" }, "", false, null));
        assertTrue(lVal.contains("jps.properties"), "Error Shell call");
    }

}
