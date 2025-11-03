/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.isa.ipc.JamnServer;
import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.ipc.JamnWebServiceProvider;
import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.CommonHelper;
import org.isa.jps.JamnPersonalServerApp.UncheckedJPSException;
import org.isa.jps.JavaScriptProvider;
import org.isa.jps.JavaScriptProvider.JsValue;

/**
 * <pre>
 * A class with static methods to create common cli commands.
 * </pre>
 */
public class CLICommandInitializer {

    private static final String LS = System.lineSeparator();
    private static final Logger LOG = Logger.getLogger(CLICommandInitializer.class.getName());

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final Function<Properties, Map<String, String>> propsToMap = props -> new HashMap<>((Map) props);

    protected static final CommonHelper Tool = new CommonHelper();

    private static CommandLineInterface cli;
    private static Consumer<String> cliOutput = val -> cli.echo(val);

    private CLICommandInitializer() {
    }

    /**
     */
    public static void initializeWith(CommandLineInterface pCli) {
        cli = pCli;
    }

    /**
     */
    public static void createCliCommands(OperatingSystemInterface pOsIFace) {
        cli.newCommandBuilder()
                .name("cls")
                .descr(name -> cli.newDefaultDescr(name, "", "Cli command to clear the console"))
                .function(ctx -> {
                    String lCmd = pOsIFace.isOnUnix() ? "clear" : "cls";
                    pOsIFace.fnc().shellCmd(new String[] { lCmd }, null, true, null);
                    return "";
                })
                .build();
    }

    /**
    */
    public static void createServerCliCommands(JamnServer pServer) {
        cli.newCommandBuilder()
                .name("server")
                .descr(name -> cli.newDefaultDescr(name, "[start, stop]", "Start/stop the jamn server"))
                .function(ctx -> {
                    if (ctx.hasArg("start")) {
                        pServer.start();
                    } else if (ctx.hasArg("stop")) {
                        pServer.stop();
                    }
                    return "";
                })
                .build();
    }

    /**
     */
    public static void createSystemCommands(JamnPersonalServerApp pApp) {
        createSystemListCommands(pApp);

        cli.newCommandBuilder()
                .name("system")
                .descr(name -> cli.newDefaultDescr(name, "[shutdown] [-kill]",
                        "Shutdown the whole application, -kill forces a hard system exit"))
                .function(ctx -> {
                    String lResult = "";

                    if (ctx.hasArg("shutdown")) {
                        if (ctx.getConfirmation("Do you really want to shutdown (y/n) ?")) {
                            if (ctx.hasArg("-kill")) {
                                LOG.info("Going to KILL application");
                                System.exit(1);
                            } else {
                                LOG.info("Going to shutdown application");
                                pApp.close();
                                System.exit(0);
                            }
                        } else {
                            return "nothing done";
                        }
                    }

                    return lResult;
                })
                .build();
    }

    /**
     */
    private static void createSystemListCommands(JamnPersonalServerApp pApp) {
        cli.newCommandBuilder()
                .name("list")
                .descr(name -> cli.newDefaultDescr(name, "[config, properties, webservices]",
                        "Info command to list internal informations"))
                .function(ctx -> {
                    String lResult = "";

                    if (ctx.hasArg("config")) {
                        lResult = propsToMap.apply(pApp.getConfig().getProperties())
                                .entrySet()
                                .stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(Collectors.joining(LS));
                    } else if (ctx.hasArg("properties")) {
                        lResult = propsToMap.apply(System.getProperties())
                                .entrySet()
                                .stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(Collectors.joining(LS));
                    } else if (ctx.hasArg("webservices")) {
                        lResult = new StringBuffer(
                                "All currently registered WebService endpoints:").append(LS)
                                .append(String.join(LS, pApp.getWebServiceProvider().getAllServicePathNames()))
                                .toString();
                    }

                    return lResult;
                })
                .build();

    }

    public static void createProcessCommands(ChildProcessManager pManager) {
        cli.newCommandBuilder()
                .name("process")
                .descr(name -> cli.newDefaultDescr(name, "[create, close, list, send]", "Child process interactions"))
                .function(ctx -> {
                    String lResult = "";
                    if (ctx.hasArg(0, "create")) {
                        lResult = pManager.createProcess();
                    } else if (ctx.hasArg(0, "close")) {
                        pManager.closeProcess(ctx.get(1));
                    } else if (ctx.hasArg(0, "list")) {
                        lResult = String.join(LS, pManager.getProcessList());
                    } else if (ctx.hasArg(0, "send")) {
                        lResult = pManager.sendCommand(ctx.get(1), ctx.get(2));
                    }
                    return lResult;
                })
                .build();
    }

    /**
     */
    public static void createJavaScriptCliCommands(JavaScriptProvider pProvider) {
        if (JamnPersonalServerApp.getInstance().getConfig().isJavaScriptEnabled()) {
            JavaScriptProvider lProvider = JamnPersonalServerApp.getInstance().getJavaScript();
            cli.newCommandBuilder()
                    .name("runjs")
                    .descr(name -> cli.newDefaultDescr(name, "[<script filename> <args ...>]", "Run a JS script"))
                    .function(ctx -> {
                        JsValue lResult = null;
                        if (!ctx.get(0).isEmpty()) {
                            String scriptName = ctx.getArgsList().remove(0);
                            lResult = pProvider.runWith(cliOutput, scriptName, ctx.getArgsArray());
                        }

                        if (lResult != null && !lResult.isEmpty()) {
                            return Tool.formatCommandReturn(lResult);
                        }
                        return "";
                    })
                    .build();
                    
            cli.newCommandBuilder()
                    .name("jsconfig")
                    .descr(name -> cli.newDefaultDescr(name, "[suspend <true/false>]", "Set JS properties"))
                    .function(ctx -> {
                        String lResult = "";
                        if (ctx.hasArg(0, "suspend")) {
                            lResult = lProvider.getEngineOptions().setInspectSuspend(ctx.get(1));
                        }
                        return lResult;
                    })
                    .build();
        }
    }

    /**
     */
    public static void createExtensionCliCommands(ExtensionHandler pProvider) {
        cli.newCommandBuilder()
                .name("runext")
                .descr(name -> cli.newDefaultDescr(name, "[<extension filename> <args ...>]",
                        "Run a Java based extension"))
                .function(ctx -> {
                    String lResult = "";
                    if (!ctx.get(0).isEmpty()) {
                        String extName = ctx.getArgsList().remove(0);
                        lResult = pProvider.runWith(cliOutput, extName, ctx.getArgsArray());
                    }
                    if (lResult != null && !lResult.isEmpty()) {
                        return Tool.formatCommandReturn(lResult);
                    }
                    return "";
                })
                .build();
    }

    /**
    */
    public static void createWebServiceProviderCliCommands(JamnWebServiceProvider pProvider, JsonToolWrapper pJson) {
        cli.newCommandBuilder()
                .name("runws")
                .descr(name -> cli.newDefaultDescr(name,
                        "[<endpoint name> <request body> pretty=<true|false>]", "Call WebService internal"))
                .function(ctx -> {
                    if (!ctx.get(0).isEmpty()) {
                        try {
                            String lResult = pProvider.doDirectCall(ctx.get(0), ctx.get(1));
                            if (ctx.hasFlag("pretty", true)) {
                                lResult = pJson.prettify(lResult);
                            }

                            if (lResult != null && !lResult.isEmpty()) {
                                return Tool.formatCommandReturn(lResult);
                            }
                        } catch (Exception e) {
                            throw new UncheckedJPSException("CLI Error calling webservice", e);
                        }
                    }
                    return "";
                })
                .build();
    }

}
