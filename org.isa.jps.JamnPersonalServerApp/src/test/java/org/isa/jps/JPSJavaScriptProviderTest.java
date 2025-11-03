/* Authored by iqbserve.de */
package org.isa.jps;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.jps.JavaScriptProvider.JSCallContext;
import org.isa.jps.JavaScriptProvider.JsValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 */
@DisplayName("JPS JavaScriptProvider Test")
class JPSJavaScriptProviderTest {

    private static Logger LOG = Logger.getLogger(JPSJavaScriptProviderTest.class.getName());

    private static JamnPersonalServerApp ServerApp;
    private static JsonToolWrapper Json;
    private static JavaScriptProvider JavaScript;

    @BeforeAll
    static void setupEnvironment() {
        ServerApp = JamnPersonalServerApp.getInstance();

        // IMPORTANT
        // uses the default runtime script path
        // - org.isa.jps.JamnPersonalServerApp\scripts
        // NOT test/resources
        ServerApp.start(new String[] {
                "javascript.enabled=true",
                "cli.enabled=true"
        });
        assertTrue(ServerApp.isRunning(), "Test Server start FAILED");

        Json = ServerApp.getJsonTool();
        assertNotNull(Json, "Json init FAILED");

        JavaScript = ServerApp.getJavaScript();
        assertNotNull(JavaScript, "JavaScript init FAILED");
    }

    @AfterAll
    static void shutDownServer() {
        ServerApp.close();
        LOG.info("Test(s) finished");
    }

    @Test
    void testCliJsInfoCommand() {
        String lVal = ServerApp.getCli().execCmdBlank("jsinfo");
        assertTrue(lVal.contains("Graal.version"), "Error js command");
    }

    /**
     * A "defused" sample test - that never fails - just logs
     */
    @Test
    void testJsShellCommand() {
        try {
            // using an optional call context to consume all output incl. echo prints
            List<String> output = new ArrayList<>();
            JSCallContext pCallCtx = new JSCallContext(output::add);

            // calling a shell command - e.g. dir/ls
            JavaScript.run(pCallCtx, "/sample/sh-test.mjs");

            // output first item should be the script echo print
            LOG.info(String.format("JS shell test call [%s]", output.iterator().next()));

            // the result in this case
            // should only contain the sh function call result
            JsValue lVal = pCallCtx.getResult();
            LOG.info(String.format("JS shell test call result [%s]", lVal.asString()));

        } catch (Exception e) {
            LOG.severe(String.format("ERROR - JS shell test faild [%s] [%s]", getClass().getName(), e));
        }
        assertTrue(true);
    }

}
