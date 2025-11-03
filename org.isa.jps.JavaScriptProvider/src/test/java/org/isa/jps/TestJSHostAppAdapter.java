/* Authored by iqbserve.de */
package org.isa.jps;

import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import org.isa.jps.JavaScriptProvider.JSCallContext;
import org.isa.jps.JavaScriptProvider.JavaScriptHostApp;
import org.isa.jps.JavaScriptProvider.JavaScriptHostAppAdapter;

/**
 * 
 */
public class TestJSHostAppAdapter implements JavaScriptHostAppAdapter {

    @Override
    public JavaScriptHostApp newHostApp(JSCallContext pCallCtx) {
        return new HostApp(pCallCtx);
    }

    /*******************************************************************************
     * Host App implementation
     *******************************************************************************/
    /**
     */
    protected class HostApp implements JavaScriptHostApp {
        protected JSCallContext callCtx;

        protected HostApp(JSCallContext pCallCtx) {
            callCtx = pCallCtx;
        }

        @Override
        public String ls() {
            return System.lineSeparator();
        }

        @Override
        public void echo(String pText) {
            System.out.println(pText);
        }

        /**
         */
        @Override
        public String path(String pPath, String... pParts) {
            return Paths.get(pPath, pParts).toString();
        }

        /**
         */
        @Override
        public String homePath(String... pParts) {
            return "";
        }

        /**
         */
        @Override
        public String workspacePath(String... pParts) {
            return "";
        }

        @Override
        public List<String> shellCmd(String pCmdLine, String pWorkingDir, Consumer<String> pOutputConsumer) {
            return null;
        }

        @Override
        public void createJSCliCommand(String pName, String pSource, String pArgsDescr, String pDescrText) {
        }

        @Override
        public boolean isOnUnix() {
            return false;
        }
    }

}
