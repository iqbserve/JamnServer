/* Authored by iqbserve.de */
package org.isa.jps;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.isa.ipc.JamnServer;
import org.isa.ipc.JamnServer.RequestMessage;
import org.isa.ipc.JamnServer.UncheckedJsonException;
import org.isa.ipc.JamnWebContentProvider;
import org.isa.ipc.JamnWebContentProvider.DefaultFileEnricher;
import org.isa.ipc.JamnWebContentProvider.FileHelper;
import org.isa.ipc.JamnWebServiceProvider;
import org.isa.ipc.JamnWebServiceProvider.WebServiceDefinitionException;
import org.isa.ipc.JamnWebSocketProvider;
import org.isa.ipc.JamnWebSocketProvider.WsoMessageProcessor;
import org.isa.jps.comp.ChildProcessManager;
import org.isa.jps.comp.ChildProcessor;
import org.isa.jps.comp.CommandLineInterface;
import org.isa.jps.comp.CLICommandInitializer;
import org.isa.jps.comp.DefaultFileEnricherValueProvider;
import org.isa.jps.comp.DefaultJavaScriptHostAppAdapter;
import org.isa.jps.comp.DefaultMessagePreprocessor;
import org.isa.jps.comp.DefaultWebServices;
import org.isa.jps.comp.DefaultWebSocketMessageProcessor;
import org.isa.jps.comp.ExtensionHandler;
import org.isa.jps.comp.OperatingSystemInterface;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * The JamnPersonalServerApp is an example of how to
 * assemble a jamn server with providers 
 * and define an environment of folders and configurations
 * that can serve as a startup/example for individual use cases.
 * 
 * The goal is an individual, open tool with the ability
 * to create local, network and browser enabled functions and services
 * in java and javascript all in one place.
 * </pre>
 */
public class JamnPersonalServerApp {

    public static final String AppName = "Jamn Personal Server";

    // a public helper tool for common functions
    public static final CommonHelper Tool = new CommonHelper();

    /**
     * a simple singleton construct for this app
     */
    protected static JamnPersonalServerApp Instance;

    public static synchronized JamnPersonalServerApp getInstance() {
        if (Instance == null) {
            Instance = new JamnPersonalServerApp();
        }
        return Instance;
    }

    protected static synchronized void closeInstance() {
        Instance = null;
    }

    /**
     * internal constants
     */
    protected static final String LS = System.lineSeparator();
    protected static final String LF = "\n";

    // internal ids
    protected static final String CONTENT_PROVIDER_ID = "ContentProvider";
    protected static final String SERVICE_PROVIDER_ID = "ServiceProvider";
    protected static final String WEBSOCKET_PROVIDER_ID = "WebSocketProvider";

    // files
    protected static final String PROPERTIES_NAME = "jps.properties";
    protected static final String LOGGING_PROPERTIES_NAME = "jps.logging.properties";
    protected static final String BUILD_INFO_PROPERTIES_NAME = "build.info.properties";
    protected static final String EXTENSION_AUTOLOAD_FILE = "extentions-auto-load.json";
    protected static final String DEV_LIBS_PATH = "dev.libs.path";

    public static final String JPS_PROFILE = "jps.profile";
    public static final String APP_PROFILE = "app";
    public static final String CHILD_PROFILE = "child";
    public static final String JPS_CHILD_ID = "jps.child.id";
    public static final String JPS_PARENT_URL = "jps.parent.url";
    public static final String WEBSERVICE_URL_ROOT = "webservice.url.root";

    // just a logging text snippet
    protected static final String INIT_LOGPRFX = "JPS init -";

    /**
     * internal static variables
     */
    protected static Logger LOG = Logger.getLogger(JamnPersonalServerApp.class.getName());
    protected static Path AppHome = Paths.get(System.getProperty("user.dir"));

    protected Charset standardEncoding = StandardCharsets.UTF_8;

    private JamnPersonalServerApp() {
    }

    /**
     * member variables
     */
    // JamnServer
    protected Config config;
    protected JamnServer server;
    protected JamnServer.JsonToolWrapper jsonTool;
    protected JamnWebServiceProvider webServiceProvider;
    protected JamnWebSocketProvider webSocketProvider;

    // JavaScript Provider
    protected JavaScriptProvider javaScript;
    // Extensions
    protected ExtensionHandler extensionHandler;

    // internal components
    protected OperatingSystemInterface osIFace;
    protected CommandLineInterface jpsCli;
    protected ChildProcessManager childManager;
    protected ChildProcessor childProcessor;

    protected Path configFolder;
    protected Collection<ServerAppNotificationListener> notificationListener = Collections.synchronizedCollection(new ArrayList<>());

    /*********************************************************
     * Public methods
     *********************************************************/
    /**
     */
    public static void main(String[] args) {
        JamnPersonalServerApp.getInstance().start(args);
    }

    /**
     * @throws IOException
     * @throws SecurityException
     * @throws WebServiceDefinitionException
     */
    public JamnPersonalServerApp initialize(String[] pArgs)
            throws SecurityException, IOException, WebServiceDefinitionException {

        initLogging();
        initConfig(pArgs);

        LOG.info(() -> String.format("%s %s App-Home [%s] args [%s]%s", LS, INIT_LOGPRFX,
                AppHome,
                String.join(" ", pArgs), LS));

        if (config.hasAppProfile()) {
            doAppInitialization();
        } else if (config.hasChildProfile()) {
            doChildInitialization();
        } else {
            throw new UncheckedJPSException(String.format(
                    "NO valid App Profile defined. Supported profiles are: [profile= %s || %s]", APP_PROFILE,
                    CHILD_PROFILE));
        }

        onInitializationEnd();

        return this;
    }

    /**
     * Default JamnPersonalServer-App initialization routine
     */
    protected void doAppInitialization()
            throws IOException, WebServiceDefinitionException {

        osIFace = new OperatingSystemInterface(config, null);

        initJsonTool();
        initCli();

        if (config.isServerEnabled()) {
            initServer();
            initContentProvider();
            initWebServiceProvider();
            initWebSocketProvider();
            initContentDispatcher();
        }

        initChildManagement();
        initJavaScript();

        initExtensions();

        CLICommandInitializer.createSystemCommands(this);
    }

    /**
     * <pre>
     * Initialization of a Child-App
     * started from a remote JamnPersonalServerApp ChildProcessManager.
     * 
     * The child connects to the parent using WebSocket.
     * </pre>
     * 
     */
    protected void doChildInitialization() {
        initJsonTool();

        childProcessor = new ChildProcessor(config, jsonTool);
        childProcessor.connect();
    }

    /**
     */
    protected void onInitializationEnd() {
        List<String> lLines = new ArrayList<>();
        lLines.add("");
        lLines.add("<<< Initialization Summary >>>");
        lLines.add("Currently registered WebService endpoints:");
        lLines.add(String.join(LS, getWebServiceProvider().getAllServicePathNames()));
        lLines.add("");
        lLines.add("WebService root: " + config.getWebServiceUrlRoot());
        lLines.add("WebSocket root: " + config.getWebSocketUrlRoot());
        lLines.add("");

        LOG.info(() -> String.join(LS, lLines));
    }

    /**
     */
    protected synchronized void sendAppNotification(AppEvent pEvent) {
        notificationListener.forEach((listener)->listener.onServerAppEvent(pEvent));
    }

    /**
     */
    public JamnPersonalServerApp addNotificationListener(ServerAppNotificationListener pListener) {
        notificationListener.add(pListener);
        return this;
    }

    /**
     * Top level Start method - called from main.
     */
    public JamnPersonalServerApp start(String[] pArgs) {
        try {
            initialize(pArgs);

            // if standard top level App profile
            if (config.hasAppProfile()) {
                if (config.isServerAutostart() && server != null) {
                    server.start();
                }
                if (config.isCliEnabled() && jpsCli != null) {
                    jpsCli.start();
                }
            } else if (config.hasChildProfile() && childProcessor != null) {
                // if Child profile
                childProcessor.start();
            }
            sendAppNotification(new AppEvent(this, "started"));
        } catch (Exception e) {
            close();
            throw new UncheckedJPSException(
                    String.format("%sERROR starting %s: [%s]%s", LS, AppName, e.getMessage(), LS), e);
        }
        return this;
    }

    /**
     */
    public synchronized void close() {
        if (server != null) {
            server.stop();
        }
        if (jpsCli != null) {
            jpsCli.stop();
        }
        // only present in child process
        if (childProcessor != null) {
            childProcessor.stop();
        }
        closeInstance();
    }

    /**
     */
    public boolean isRunning() {
        return (server != null && server.isRunning());
    }

    /**
     */
    public Config getConfig() {
        return config;
    }

    /**
     */
    public JamnServer.JsonToolWrapper getJsonTool() {
        return jsonTool;
    }

    /**
     */
    public JamnServer getServer() {
        return server;
    }

    /**
     */
    public OperatingSystemInterface getOsIFace() {
        return osIFace;
    }

    /**
     */
    public ChildProcessManager getChildProcessManager() {
        return childManager;
    }

    /**
     */
    public CommandLineInterface getCli() {
        return jpsCli;
    }

    /**
     */
    public JavaScriptProvider getJavaScript() {
        return javaScript;
    }

    /**
     */
    public ExtensionHandler getExtensionHandler() {
        return extensionHandler;
    }

    /**
     */
    public JamnWebServiceProvider getWebServiceProvider() {
        return webServiceProvider;
    }

    /**
     * Public-Interface to register extension WebServices
     * 
     * @throws WebServiceDefinitionException
     */
    public void registerWebServices(Object pServices) throws WebServiceDefinitionException {
        webServiceProvider.registerServices(pServices);
    }

    /**
     */
    public void addWebSocketMessageProcessor(WsoMessageProcessor pProcessor) {
        if (webSocketProvider != null) {
            webSocketProvider.addMessageProcessor(pProcessor);
        }
    }

    /**
     */
    public Path getHomePath(String... pSubPathParts) {
        return Paths.get(AppHome.toString(), pSubPathParts);
    }

    /**
     */
    public String loadOrCreateConfigFile(String pFileName, String pDefaultContent) throws IOException {
        String lContent = pDefaultContent;
        Path lFile = Paths.get(configFolder.toString(), pFileName);
        if (!Files.exists(lFile)) {
            Files.writeString(lFile, pDefaultContent, standardEncoding, StandardOpenOption.CREATE);
            LOG.info(() -> String.format("App Config file created [%s]", lFile));
        }
        lContent = new String(Files.readAllBytes(lFile));
        return lContent;
    }

    /*********************************************************
     * Internal methods
     *********************************************************/

    /**
     */
    protected void initProgramArgs(String[] pArgs) {
        Map<String, String> lArgs = Tool.defaultArgParser.apply(pArgs);
        getConfig().getProperties().putAll(lArgs);
    }

    /**
     */
    protected void initLogging() throws SecurityException, IOException {
        Path lConfigPath = getHomePath(LOGGING_PROPERTIES_NAME);

        if (Files.exists(lConfigPath)) {
            LogManager.getLogManager().readConfiguration(Files.newInputStream(lConfigPath));
            LOG.info(() -> String.format("%s User-Logging config read from [%s]", INIT_LOGPRFX, lConfigPath));

        } else {
            LogManager.getLogManager().readConfiguration(getClass().getResourceAsStream("/" + LOGGING_PROPERTIES_NAME));
            LOG.info(() -> String.format("%s Default-Logging config read [%s]", INIT_LOGPRFX, LOGGING_PROPERTIES_NAME));
        }
    }

    /**
     */
    protected void initConfig(String[] pArgs) throws IOException {
        boolean lSaveConfig = false;
        String lDefaultConfig = "";
        Map<String, String> lArgs = Tool.defaultArgParser.apply(pArgs);
        Path lConfigPath = getHomePath(PROPERTIES_NAME);

        // load config
        if (Files.exists(lConfigPath)) {
            config = new Config(Files.newInputStream(lConfigPath));
            LOG.info(() -> String.format("%s User-App-Config file read [%s]", INIT_LOGPRFX, lConfigPath));
        } else {
            lDefaultConfig = String.join(LS, Config.DEFAULT_CONFIG, JamnServer.Config.DEFAULT_CONFIG);
            config = new Config(Tool.getAsInputStream(lDefaultConfig));
            lSaveConfig = true;
        }

        // load build infos
        InputStream lIn = getClass().getResourceAsStream("/" + BUILD_INFO_PROPERTIES_NAME);
        if (lIn != null) {
            config.getBuildProperties().load(lIn);
        }

        // search -D options
        config.searchDynamicOption(JPS_PROFILE, APP_PROFILE);
        config.searchDynamicOption(DEV_LIBS_PATH, "");

        // at last merge program args
        config.getProperties().putAll(lArgs);

        standardEncoding = Charset.forName(config.getStandardEncoding());
        CommonHelper.setEncoding(standardEncoding);

        if (lSaveConfig && config.hasAppProfile()) {
            Files.writeString(lConfigPath, lDefaultConfig, standardEncoding, StandardOpenOption.CREATE);
            LOG.info(() -> String.format("%s Default-App-Config loaded and saved to [%s]", INIT_LOGPRFX, lConfigPath));
        }

        configFolder = Tool.ensureSubDir(config.getConfigRoot(), AppHome);
    }

    /**
     * @throws IOException
     */
    protected void initChildManagement() throws IOException {
        if (osIFace != null && webSocketProvider != null) {
            childManager = ChildProcessManager.newBuilder()
                    .setWebSocketProvider(webSocketProvider)
                    .setOperatingSystemInterface(osIFace)
                    .setJsonTool(jsonTool)
                    .setAppHome(AppHome)
                    .setConfig(config)
                    .build();
            childManager.initialize();

            CLICommandInitializer.createProcessCommands(childManager);
        }
    }

    /**
     */
    protected void initCli() {
        jpsCli = new CommandLineInterface()
                .setEncoding(standardEncoding)
                .setInputFile(Paths.get(AppHome.toString(), config.getCliInputFileName()));

        CLICommandInitializer.initializeWith(jpsCli);
        CLICommandInitializer.createCliCommands(osIFace);

        if (!config.isCliEnabled()) {
            LOG.info(() -> String.format("%s CLI is Disabled. To enable set config [%s] property [cli.enabled=true]",
                    INIT_LOGPRFX, PROPERTIES_NAME));
        }
    }

    /**
     */
    protected void initJavaScript() throws IOException {
        if (config.isJavaScriptEnabled()) {

            Path lScriptPath = Tool.ensureSubDir(config.getScriptRoot(), AppHome);

            javaScript = new JavaScriptProvider(lScriptPath, config.getProperties());
            javaScript.setHostAppAdapter(new DefaultJavaScriptHostAppAdapter(javaScript, this));

            javaScript.initialize();

            String lAutoLoadScript = getConfig().getJsAutoLoadScript();
            if (javaScript.sourceExists(lAutoLoadScript)) {
                javaScript.run(lAutoLoadScript);
            }

            CLICommandInitializer.createJavaScriptCliCommands(javaScript);

            LOG.info(() -> String.format("%s JavaScript Provider initialized [%s]", INIT_LOGPRFX, lScriptPath));
        } else {
            LOG.info(() -> String.format(
                    "%s JavaScript is Disabled. To enable ensure libraries and set config [%s] property [javascript.enabled=true]",
                    INIT_LOGPRFX, PROPERTIES_NAME));
        }
    }

    /**
     */
    protected void initExtensions() throws IOException {
        if (config.isExtensionsEnabled()) {
            // ensure the extensions root folder
            Path lRootPath = Tool.ensureSubDir(config.getExtensionRoot(), AppHome);
            Tool.ensureSubDir(config.getExtensionBin(), lRootPath);
            Tool.ensureSubDir(config.getExtensionData(), lRootPath);

            extensionHandler = new ExtensionHandler(lRootPath, config, jsonTool);
            CLICommandInitializer.createExtensionCliCommands(extensionHandler);

            Path lAutoloadFile = Paths.get(lRootPath.toString(), config.getExtensionsAutoloadFileName());
            if (!Files.exists(lAutoloadFile)) {
                Files.writeString(lAutoloadFile, "[]", standardEncoding, StandardOpenOption.CREATE);

                LOG.info(() -> String.format("%s default EMPTY App Extensions autoload file created [%s]", INIT_LOGPRFX,
                        lAutoloadFile));
                return;
            }

            List<String> lErrors = new ArrayList<>();
            String lAutoloadSrc = new String(Files.readAllBytes(lAutoloadFile));
            List<String> lAutoLoadList = Arrays.asList(jsonTool.toObject(lAutoloadSrc, String[].class));
            for (String name : lAutoLoadList) {
                try {
                    extensionHandler.loadExtension(name, null);
                } catch (Exception e) {
                    lErrors.add(e.toString());
                }
            }
            if (!lErrors.isEmpty()) {
                String lMsg = String.format("Extention initialization error(s):%s%s", LS, String.join(LS, lErrors));
                throw new UncheckedJPSException(lMsg);
            }

        } else {
            LOG.info(() -> String.format(
                    "%s Extensions are Disabled. To enable set config [%s] property [extensions.enabled=true]",
                    INIT_LOGPRFX, PROPERTIES_NAME));
        }
    }

    /**
     * json isGetter visibility disabled
     */
    protected void initJsonTool() {
        jsonTool = new JamnServer.JsonToolWrapper() {
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

            @Override
            public String prettify(String pJsonInput) {
                try {
                    Object lJsonObj = jack.readValue(pJsonInput, Object.class);
                    return jack.writerWithDefaultPrettyPrinter().writeValueAsString(lJsonObj);
                } catch (Exception e) {
                    throw new UncheckedJsonException(UncheckedJsonException.PRETTIFY_ERROR, e);
                }
            }
        };
        LOG.info(() -> String.format("%s json tool installed [%s]", INIT_LOGPRFX, ObjectMapper.class.getName()));
    }

    /**
     */
    protected void initServer() throws IOException {
        server = new JamnServer(config.getProperties());

        server.setMessagePreprocessor(new DefaultMessagePreprocessor(config, jsonTool));

        CLICommandInitializer.createServerCliCommands(server);
    }

    /**
     */
    protected void initContentProvider() throws IOException {
        String lRootPath;

        if (config.getWebFileRoot().startsWith("/")) {
            // assume that a configured absolute path exists.
            lRootPath = config.getWebFileRoot();
        } else {
            // ensure a relative web file root folder
            lRootPath = Tool.ensureSubDir(config.getWebFileRoot(), AppHome).toString();
        }

        // create the provider with a webroot
        JamnWebContentProvider lWebContentProvider = new JamnWebContentProvider(lRootPath)
                .setConfig(server.getConfig())
                // extend a FileHelper method
                .setFileHelper(new FileHelper() {
                    @Override
                    public String doPathMapping(String pPath) {
                        String lPath = super.doPathMapping(pPath);
                        return lPath.equals("/index.html") ? config.getWebAppMainPage() : lPath;
                    }
                })
                // create and set a file enricher with a provider for template values
                .setFileEnricher(new DefaultFileEnricher(
                        new DefaultFileEnricherValueProvider(AppHome, config)
                                // set the service url root as injectable value for web content
                                // e.g. javascript modules - see webapi.mjs
                                .addValue(WEBSERVICE_URL_ROOT, config.getWebServiceUrlRoot())));

        // add the provider to server
        server.addContentProvider(CONTENT_PROVIDER_ID, lWebContentProvider);
        LOG.info(() -> String.format("%s content provider installed [%s] on [%s]", INIT_LOGPRFX,
                JamnWebContentProvider.class.getSimpleName(), lRootPath));

    }

    /**
     * @throws WebServiceDefinitionException
     */
    protected void initWebServiceProvider() throws WebServiceDefinitionException {
        if (this.server != null && config.isWebServiceEnabled()) {

            // create the WebService provider
            webServiceProvider = new JamnWebServiceProvider()
                    .setJsonTool(jsonTool)
                    .setUrlRoot(config.getWebServiceUrlRoot());

            server.addContentProvider(SERVICE_PROVIDER_ID, webServiceProvider);

            LOG.info(() -> String.format("%s web service provider installed [%s]", INIT_LOGPRFX,
                    JamnWebServiceProvider.class.getSimpleName()));

            // install app default web services
            registerWebServices(new DefaultWebServices(osIFace));

            CLICommandInitializer.createWebServiceProviderCliCommands(webServiceProvider, getJsonTool());
        }
    }

    /**
     */
    protected void initWebSocketProvider() {
        if (this.server != null && config.isWebSocketEnabled()) {
            // create the WebSocketProvider
            webSocketProvider = new JamnWebSocketProvider()
                    .addConnectionPath(config.getWebSocketUrlRoot())
                    .setMaxUpStreamPayloadSize(config.getWebSocketMaxUpstreamSize());

            webSocketProvider.addMessageProcessor(
                    new DefaultWebSocketMessageProcessor(getConfig(), getJsonTool(), webSocketProvider));

            server.addContentProvider(WEBSOCKET_PROVIDER_ID, webSocketProvider);

            LOG.info(() -> String.format("%s web socket provider installed [%s] at [%s]", INIT_LOGPRFX,
                    JamnWebSocketProvider.class.getSimpleName(),
                    "ws://<host>:" + server.getConfig().getPort() + config.getWebSocketUrlRoot()));

        }
    }

    /**
     */
    protected void initContentDispatcher() {
        Predicate<String> isServiceRequest = webServiceProvider != null ? webServiceProvider::isServicePath
                : path -> false;

        server.setContentProviderDispatcher((RequestMessage pRequest) -> {
            String lPath = pRequest.getPath();
            if (isServiceRequest.test(lPath)) {
                return SERVICE_PROVIDER_ID;
            }
            return CONTENT_PROVIDER_ID;
        });
    }

    /*********************************************************
     * App classes and interfaces
     *********************************************************/

    /**
     * <pre>
     * </pre>
     */
    public static interface ServerAppNotificationListener {
        /**
         */
        public void onServerAppEvent(AppEvent pEvent);
    }

    public static class AppEvent {
        protected JamnPersonalServerApp app;
        protected String msg = "";

        public AppEvent(JamnPersonalServerApp pApp, String pMsg){
            app = pApp;
            msg = pMsg;
        }
        public JamnPersonalServerApp app(){
            return app;
        }
        public String msg(){
            return msg;
        }
    }

    /**
     */
    public static class Config {
        // CONFIG DEFAULT folder names under the home directory
        private static final String TRUE = "true";
        private static final String FALSE = "false";
        private static final String WEB_FILE_ROOT = "http";
        private static final String SCRIPT_ROOT = "scripts";
        private static final String DATA_ROOT = "data";
        private static final String CONFIG_ROOT = "config";
        private static final String EXTENSION_ROOT = "extensions";
        private static final String EXTENSION_BIN = "bin";
        private static final String EXTENSION_DATA = "data";
        private static final String WORKSPACE_ROOT = "workspace";

        protected static final String DEFAULT_CONFIG = String.join(LF,
                "##",
                "## " + AppName + " Config Properties",
                "##", "",
                "#JPS Profile", JPS_PROFILE + "=" + APP_PROFILE, "",
                "#WebContentProvider files root folder", "web.file.root=" + WEB_FILE_ROOT, "",
                "#Web File-Enricher root folder", "web.file.enricher.root=http/jsmod/html-components", "",
                "#JPS extensions root folder name", "jps.extension.root=" + EXTENSION_ROOT, "",
                "#JPS extensions bin folder name", "jps.extension.bin=" + EXTENSION_BIN, "",
                "#JPS extensions data folder name", "jps.extension.data=" + EXTENSION_DATA, "",
                "#JPS workspace root folder", "jps.workspace.root=" + WORKSPACE_ROOT, "",
                "#JPS data root folder", "jps.data.root=" + DATA_ROOT, "",
                "#JPS config root folder", "jps.config.root=" + CONFIG_ROOT, "",
                "#JPS Extensions auto load file name", "jps.extensions.autoload.file=" + EXTENSION_AUTOLOAD_FILE, "",
                "#WebApp main Page", "webapp.main.page=/workbench.html", "",
                "#Extensions enabled", "extensions.enabled=false", "",
                "#JavaScriptProvider script root folder", "script.root=" + SCRIPT_ROOT, "",
                "#JavaScript auto-load script", "js.auto.load.script=js-auto-load.js", "",
                "#CLI input file", "cli.input.file=jps.cli.input.txt", "",
                "#CLI enabled", "cli.enabled=true", "",
                "#JavaScript enabled", "javascript.enabled=false", "",
                "#JavaScript debug enabled", "javascript.debug.enabled=false", "",
                "#Server enabled", "server.enabled=true", "",
                "#WebService enabled", "webservice.enabled=true", "",
                "#WebSocket enabled", "websocket.enabled=true", "",
                "#WebSocket url root", "websocket.url.root=/wsoapi", "",
                "#WebSocket max upstream size", "websocket.max.upstream.size=65000", "",
                "#WebService url root", "webservice.url.root=/webapi", "",
                "#Child WebSocket url root", "child.websocket.url.root=/childapi", "",
                "#JVM debug option",
                "jvm.debug.option=-agentlib:jdwp=transport=dt_socket,address=localhost:9009,server=y,suspend=y", "",
                "#Server autostart", "server.autostart=true", "",
                "#Child process debug", "child.process.debug.enabled=false", "",
                "#Standard encoding", "standard.encoding=UTF-8", "",
                "#Windows shell encoding", "win.shell.encoding=Cp850", "",
                "#Unix shell encoding", "unix.shell.encoding=ISO8859_1", "");

        protected Properties props = new Properties();
        protected Properties buildProps = new Properties();

        private Config() {
        }

        private Config(InputStream pPropsIn) throws IOException {
            props.load(pPropsIn);
        }

        public void searchDynamicOption(String pKey, String pDefault) {
            // overwriting: if -D present -> use it or leave current
            props.put(pKey, System.getProperty(pKey, props.getProperty(pKey, pDefault)));
        }

        public Properties getBuildProperties() {
            return buildProps;
        }

        public String getProfile() {
            return props.getProperty(JPS_PROFILE, APP_PROFILE);
        }

        public String getJPSChildId() {
            return props.getProperty(JPS_CHILD_ID, "");
        }

        public String getJPSParentUrl() {
            return props.getProperty(JPS_PARENT_URL, "");
        }

        public String getDevLibsPath() {
            return props.getProperty(DEV_LIBS_PATH, "");
        }

        public String getWebFileRoot() {
            return props.getProperty("web.file.root", WEB_FILE_ROOT);
        }

        public String getWebFileEnricherRoot() {
            return props.getProperty("web.file.enricher.root", "http/jsmod/html-components");
        }

        public String getDataRoot() {
            return props.getProperty("jps.data.root", DATA_ROOT);
        }

        public String getConfigRoot() {
            return props.getProperty("jps.configs.root", CONFIG_ROOT);
        }

        public String getExtensionRoot() {
            return props.getProperty("jps.extension.root", EXTENSION_ROOT);
        }

        public String getExtensionBin() {
            return props.getProperty("jps.extension.bin", EXTENSION_BIN);
        }

        public String getExtensionData() {
            return props.getProperty("jps.extension.data", EXTENSION_DATA);
        }

        public String getWorkspaceRoot() {
            return props.getProperty("jps.workspace.root", WORKSPACE_ROOT);
        }

        public String getExtensionsAutoloadFileName() {
            return props.getProperty("jps.extensions.autoload.file", EXTENSION_AUTOLOAD_FILE);
        }

        public String getCliInputFileName() {
            return props.getProperty("cli.input.file", "jps.cli.input.txt");
        }

        public String getWebAppMainPage() {
            return props.getProperty("webapp.main.page", "/workbench.html");
        }

        public boolean isExtensionsEnabled() {
            return Boolean.parseBoolean(props.getProperty("extensions.enabled", TRUE));
        }

        public String getScriptRoot() {
            return props.getProperty("script.root", SCRIPT_ROOT);
        }

        public String getJsAutoLoadScript() {
            return props.getProperty("js.auto.load.script", "js-auto-load.js");
        }

        public int getPort() {
            return Integer.valueOf(props.getProperty("port", "8099"));
        }

        public String getStandardEncoding() {
            return props.getProperty("standard.encoding", "UTF-8");
        }

        public String getWinShellEncoding() {
            return props.getProperty("win.shell.encoding", "Cp850");
        }

        public String getUnixShellEncoding() {
            return props.getProperty("unix.shell.encoding", "ISO8859_1");
        }

        public String getJVMDebugOption() {
            return props.getProperty("jvm.debug.option", "");
        }

        public String getWebSocketUrlRoot() {
            return props.getProperty("websocket.url.root", "/wsoapi");
        }

        public long getWebSocketMaxUpstreamSize() {
            return Long.valueOf(props.getProperty("websocket.max.upstream.size", "65000"));
        }

        public String getWebServiceUrlRoot() {
            return props.getProperty(WEBSERVICE_URL_ROOT, "/webapi");
        }

        public String getChildWebSocketUrlRoot() {
            return props.getProperty("child.websocket.url.root", "/childapi");
        }

        public boolean hasAppProfile() {
            return props.getProperty(JPS_PROFILE, APP_PROFILE).equals(APP_PROFILE);
        }

        public boolean hasChildProfile() {
            return props.getProperty(JPS_PROFILE, "").equals(CHILD_PROFILE);
        }

        public boolean isCliEnabled() {
            return Boolean.parseBoolean(props.getProperty("cli.enabled", FALSE));
        }

        public boolean isChildProcessDebugEnabled() {
            return Boolean.parseBoolean(props.getProperty("child.process.debug.enabled", FALSE));
        }

        public boolean isJavaScriptEnabled() {
            return Boolean.parseBoolean(props.getProperty("javascript.enabled", FALSE));
        }

        public boolean isJavaScriptDebugEnabled() {
            return Boolean.parseBoolean(props.getProperty("javascript.debug.enabled", FALSE));
        }

        public boolean isServerEnabled() {
            return Boolean.parseBoolean(props.getProperty("server.enabled", TRUE));
        }

        public boolean isWebServiceEnabled() {
            return Boolean.parseBoolean(props.getProperty("webservice.enabled", TRUE));
        }

        public boolean isWebSocketEnabled() {
            return Boolean.parseBoolean(props.getProperty("websocket.enabled", TRUE));
        }

        public boolean isServerAutostart() {
            return Boolean.parseBoolean(props.getProperty("server.autostart", TRUE));
        }

        public Properties getProperties() {
            return props;
        }
    }

    /**
     */
    public static class UncheckedJPSException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedJPSException(String pMsg) {
            super(pMsg);
        }

        public UncheckedJPSException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

    /*********************************************************
     * Common Helper class
     *********************************************************/
    /**
     */
    public static class CommonHelper {
        public static final Pattern RegexNewLine = Pattern.compile("\\r?\\n|\\r");
        public static final Pattern RegexWhiteSpaces = Pattern.compile("\\s+");

        public static final String CDATA_START = "<![CDATA[";
        public static final String CDATA_END = "]]>";

        protected static Charset StandardEncoding = StandardCharsets.UTF_8;

        protected static void setEncoding(Charset pEncoding) {
            StandardEncoding = pEncoding;
        }

        /**
         */
        protected static Function<Object, String> commandReturnValueFormatter = value -> {
            StringBuilder lBuilder = new StringBuilder()
                    .append(LS)
                    .append("<return_value>").append(LS)
                    .append(value.toString()).append(LS)
                    .append("</return_value>").append(LS);
            return lBuilder.toString();
        };

        public static void setCommandReturnValueFormatter(Function<Object, String> formatter) {
            commandReturnValueFormatter = formatter;
        }

        public String formatCommandReturn(Object pVal) {
            return commandReturnValueFormatter.apply(pVal);
        }

        /**
         * Arg Format: [-]<name>=<value>
         */
        public final Function<String[], Map<String, String>> defaultArgParser = (String[] args) -> {
            Map<String, String> lArgMap = new LinkedHashMap<>();
            String lMark = "-";
            String lDelim = "=";
            boolean lStrict = false;

            String[] lKeyValue;
            for (String arg : args) {
                if (!lStrict || arg.startsWith(lMark)) {
                    lKeyValue = arg.split(lDelim);
                    if (lKeyValue.length > 0) {
                        if (lKeyValue[0].startsWith(lMark)) {
                            lKeyValue[0] = lKeyValue[0].substring(1);
                        }
                        if (lKeyValue.length > 1) {
                            lArgMap.put(lKeyValue[0], lKeyValue[1]);
                        } else {
                            lArgMap.put(lKeyValue[0], lKeyValue[0]);
                        }
                    }
                }
            }
            return lArgMap;
        };

        /**
         */
        public String getStackTraceFrom(Throwable t) {
            return JamnServer.getStackTraceFrom(t);
        }

        /**
         */
        public Path ensureSubDir(String pName, Path pRoot) throws IOException {
            Path lPath = Paths.get(pRoot.toString(), pName);
            if (!Files.exists(lPath)) {
                Files.createDirectories(lPath);
                LOG.info(() -> String.format("Directories created [%s]", lPath));
            }
            return lPath;
        }

        /**
         */
        public String readToString(InputStream lInStream) {
            return new BufferedReader(new InputStreamReader(lInStream)).lines().collect(Collectors.joining(LS));
        }

        /**
         */
        public InputStream getAsInputStream(String pText) {
            return new ByteArrayInputStream(pText.getBytes(StandardEncoding));
        }

        /**
         */
        public void createFileURL(Path pFile, List<URL> pUrls, Object pInfo, List<String> pErrors)
                throws MalformedURLException {

            if (Files.exists(pFile)) {
                pUrls.add(pFile.toUri().toURL());
            } else {
                pErrors.add(String.format("File does NOT exist [%s] [%s]", pFile, pInfo));
            }
        }

        /**
         */
        public String[] rebuildQuotedWhitespaceStrings(String[] pToken) {
            List<String> newToken = new ArrayList<>();
            StringBuilder lBuffer = new StringBuilder();
            String tok = "";
            boolean inQuote = false;

            for (int i = 0; i < pToken.length; i++) {
                tok = pToken[i];

                if (tok.trim().startsWith("\"") && tok.trim().endsWith("\"")) {
                    newToken.add(tok);
                } else {
                    if (!inQuote && tok.contains("\"")) {
                        inQuote = true;
                        lBuffer = new StringBuilder(tok);
                        continue;
                    }
                    if (inQuote && tok.contains("\"")) {
                        inQuote = false;
                        lBuffer.append(" ").append(tok);
                        newToken.add(lBuffer.toString());
                    } else if (inQuote) {
                        lBuffer.append(" ").append(tok);
                    } else {
                        newToken.add(tok);
                    }
                }
            }

            if (inQuote) {
                throw new UncheckedJPSException("Missing start/end quote in command line string");
            }

            return newToken.toArray(new String[newToken.size()]);
        }

        /**
         */
        public String[] parseCommandLine(String pText) {
            pText = RegexWhiteSpaces.matcher(pText).replaceAll(" ");
            pText = RegexNewLine.matcher(pText).replaceAll("").trim();

            List<String> args = new ArrayList<>();
            String startMark = CDATA_START;
            String endMark = CDATA_END;

            int startOffset = startMark.length();
            int endOffset = endMark.length();
            String block;
            int srcStart = 0;
            int start = pText.indexOf(startMark);
            int end;
            String[] token;

            while (start > -1) {
                if (start > srcStart) {
                    block = pText.substring(srcStart, start);
                    token = block.trim().split(" ");
                    args.addAll(Arrays.asList(rebuildQuotedWhitespaceStrings(token)));
                }
                end = pText.indexOf(endMark, start);
                if (end > -1) {
                    block = pText.substring(start + startOffset, end);
                    args.add(block);
                } else {
                    throw new UncheckedJPSException("Missing block end mark [" + endMark + "] for [" + startMark + "]");
                }
                srcStart = end + endOffset;
                start = pText.indexOf(startMark, srcStart);
            }

            if (srcStart < pText.length()) {
                block = pText.substring(srcStart);
                token = block.trim().split(" ");
                args.addAll(Arrays.asList(rebuildQuotedWhitespaceStrings(token)));
            }

            return args.toArray(new String[args.size()]);
        }

        /**
         */
        public String[] parseCommandLine(String pText, UnaryOperator<String> pTokenCleaner) {
            String[] lToken = parseCommandLine(pText);
            for (int i = 0; i < lToken.length; i++) {
                lToken[i] = pTokenCleaner.apply(lToken[i]);
            }
            return lToken;
        }

    }
}
