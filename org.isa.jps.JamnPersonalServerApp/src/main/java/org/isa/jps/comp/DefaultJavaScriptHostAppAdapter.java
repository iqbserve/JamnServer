/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.Config;
import org.isa.jps.JavaScriptProvider;
import org.isa.jps.JavaScriptProvider.JSCallContext;
import org.isa.jps.JavaScriptProvider.JavaScriptHostApp;
import org.isa.jps.JavaScriptProvider.JavaScriptHostAppAdapter;

/**
 * <pre>
 * Example of a JavaScript Host App Adapter Interface. 
 * 
 * The adapter is used as a mediator between the PersonalServerApp and the JavaScript Provider.
 * It provides the JavaScript-HostApp implementation that is visible in js scripts.
 * 
 * The general cardinality is: 1-JsCall => 1-callContext - 1-hostApp - 1-jsEvalContext
 * </pre>
 */
public class DefaultJavaScriptHostAppAdapter implements JavaScriptHostAppAdapter {

    protected static Logger LOG = Logger.getLogger(DefaultJavaScriptHostAppAdapter.class.getName());
    protected static final String LS = System.lineSeparator();

    protected JavaScriptProvider javaScript;
    protected CommandLineInterface cli;
    protected OperatingSystemInterface osIFace;
    protected Path appHome;
    protected Config appConfig;

    /**
     */
    public DefaultJavaScriptHostAppAdapter(JavaScriptProvider pJavaScript, JamnPersonalServerApp pApp) {
        javaScript = pJavaScript;

        appHome = pApp.getHomePath("");
        appConfig = pApp.getConfig();
        cli = pApp.getCli();
        osIFace = pApp.getOsIFace();
    }

    /*******************************************************************************
     * Public interface
     *******************************************************************************/
    
    /**
     * Create HostApp instances to be injected in the JavaScript evaluation context.
     */
    @Override
    public JavaScriptHostApp newHostApp(JSCallContext pCallCtx) {
        return new HostApp(pCallCtx);
    }

    /*******************************************************************************
     * Host App implementation
     *******************************************************************************/
    /**
     * <pre>
     * The host app object is the app specific 
     * global visible Java-Object in a JavaScript.
     * </pre>
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

        /**
         */
        @Override
        public boolean isOnUnix() {
            return osIFace.isOnUnix();
        }

        /**
         */
        @Override
        public void echo(String pText) {
            if (callCtx.getOutputConsumer() != null) {
                callCtx.getOutputConsumer().accept(pText);
            }
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
            return Paths.get(appHome.toString(), pParts).toString();
        }

        /**
         */
        @Override
        public String workspacePath(String... pParts) {
            return Paths.get(homePath(appConfig.getWorkspaceRoot()), pParts).toString();
        }

        /**
         * <pre>
         * This method implements the host app specific interface to shell processes.
         * It is ONLY called from inside a JS-Script.
         * 
         * The pScriptOutputConsumer is a JS script call back method 
         * to directly forward shell output back to the calling script.
         * 
         * The callCtx also provides a outputConsumer
         * to forward the shell output to the java caller of the script.
         * 
         * The method itself returns output ONLY - if NO pScriptOutputConsumer is defined.
         * </pre>
         */
        @Override
        public List<String> shellCmd(String pCmdLine, String pWorkingDir, Consumer<String> pScriptOutputConsumer) {

            String[] lCmdParts = JamnPersonalServerApp.Tool.rebuildQuotedWhitespaceStrings(pCmdLine.split(" "));

            List<String> lResult = new ArrayList<>();
            Consumer<String> lResultConsumer = lResult::add;

            osIFace.fnc().shellCmd(lCmdParts, pWorkingDir, false, output -> {
                if (pScriptOutputConsumer != null) {
                    pScriptOutputConsumer.accept(output);
                } else {
                    lResultConsumer.accept(output);
                }

                if (callCtx.getOutputConsumer() != null) {
                    callCtx.getOutputConsumer().accept(output);
                }
            });
            return lResult;
        }

        /**
         * Create Cli Commands for JavaScripts from JavaScript - see: js-auto-load.js.
         */
        @Override
        public void createJSCliCommand(String pName, String pSource, String pArgs, String pText) {
            if (cli != null) {
                if (javaScript.sourceExists(pSource)) {
                    cli.newCommandBuilder()
                            .name(pName)
                            .descr(name -> cli.newDefaultDescr(name, pArgs, pText))
                            .function(ctx -> runScript(pSource, ctx.getArgsArray()))
                            .build();
                } else {
                    throw new UncheckedJavaScriptHostException(
                            String.format("JS HostApp - cli command script NOT FOUND [%s] [%s]", pName,
                                    javaScript.getSourcePath(pSource)));
                }
            }
        }

    }

    /*******************************************************************************
     * Internals
     *******************************************************************************/

    /**
     */
    protected String runScript(String pFileName, String... pArgs) {
        Object lResult = javaScript.run(pFileName, pArgs);
        return lResult != null ? lResult.toString() : "";
    }

    /*******************************************************************************
     *******************************************************************************/
    /**
     */
    public static class UncheckedJavaScriptHostException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedJavaScriptHostException(String pMsg) {
            super(pMsg);
        }

        public UncheckedJavaScriptHostException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

}
