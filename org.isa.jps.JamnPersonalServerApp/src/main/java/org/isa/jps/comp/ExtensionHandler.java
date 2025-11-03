/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer;
import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.CommonHelper;
import org.isa.jps.JamnPersonalServerApp.Config;

/**
 * <pre>
 * Extensions are the Jamn interface to Java based plugable functionality.
 * 
 * An extension is defined by a <apphome>/extensions/<my-extension-name>.json definition file
 * where the filename is the extensions unique id - e.g. sample.Command.json.
 * 
 * An extension is realized by a independent java class.
 * From the Jamn's perspective, there are no hard requirements for the class.
 * Jamn just needs:
 *  - a public constructor, either with or without a Map parameter
 *  - a optional, freely nameable "execution" method with a String array of arguments returning a String
 * 
 * Extensions differ in terms of the visibility of Java objects/classes.
 * In "app" scope mode the extension has direct java access to the underlaying Jamn App and its classes.
 * But NOT to other extensions.
 * In "" blank mode the java scope is restricted to the blank JavaSE platform.
 * Independently of this, an extension can define any of its own imports and dependencies.
 * 
 * The Jamn extension mechanism is NO module system
 * and there is NO class, instance or state handling performed.
 * From the jamn point of view extension calls are stateless function calls
 * that always lead to a new object instantiation.
 * 
 * How ever ... an extension can implement internally any other kind of behavior and technology.
 * </pre>
 */
public class ExtensionHandler {

    protected static final String LS = System.lineSeparator();
    protected static final Logger LOG = Logger.getLogger(ExtensionHandler.class.getName());
    protected static final CommonHelper Tool = JamnPersonalServerApp.Tool;

    // the default constructor argument class
    protected static Class<?> defaultContextClass = Map.class;
    // the default instance run/execution method name
    protected static String defaultRunMethod = "execute";

    protected Charset encoding;
    protected JamnServer.JsonToolWrapper json;
    protected Config config;
    protected Path pathBase;

    protected Map<String, ExtensionCartridge> extensions;

    protected ExtensionHandler() {
    }

    /**
     */
    public ExtensionHandler(Path pPathBase, Config pConfig, JamnServer.JsonToolWrapper pJsonTool) {
        this();
        pathBase = pPathBase;
        config = pConfig;
        json = pJsonTool;
        encoding = Charset.forName(config.getStandardEncoding());
        extensions = new HashMap<>();
    }

    /**
     */
    public void loadExtension(String pName, Map<String, Object> pCtx)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        ExtensionCartridge lExt = getExtensionCat(pName);
        lExt.newInstance(pCtx);
    }

    /**
     */
    public String runWith(Consumer<String> pOutput, String pExtensionName, String... pArgs) {
        ExtensionCallContext lCtx = new ExtensionCallContext(pOutput);
        return run(lCtx, pExtensionName, pArgs);
    }

    /**
     */
    public String run(ExtensionCallContext pCtx, String pExtensionName, String... pArgs) {
        String lResult = "";
        ExtensionCartridge lExt = getExtensionCat(pExtensionName);

        try {
            lResult = executeExtension(lExt, pArgs, pCtx);
        } catch (Exception e) {
            throw new UncheckedExtensionException(
                    String.format("Extension execution failed [%s]", pExtensionName), e);
        }
        pCtx.result = lResult != null ? lResult : "";
        return pCtx.getResult();
    }

    /**
     */
    protected ExtensionCartridge getExtensionCat(String pName) {
        if (!extensions.containsKey(pName)) {
            ExtensionDef lDef = readDefinition(pName);
            ExtensionCartridge lCat = new ExtensionCartridge(pName, lDef);
            initExtension(lCat);
            extensions.put(pName, lCat);
        }
        return extensions.get(pName);
    }

    /**
     */
    protected void initExtension(ExtensionCartridge pCat) {
        ExtensionDef lDef = pCat.def;
        Path lFile = null;
        List<URL> lUrls = new ArrayList<>();
        List<String> lErrors = new ArrayList<>();

        try {
            lFile = resolvePath(lDef.hasDevPath() ? lDef.getDevPath() : lDef.getBinPath(), lDef);
            if (lDef.hasDevPath()) {
                String lPath = lFile.toString();
                LOG.info(() -> String.format("HINT use extension development path for: [%s]%s devPath=[%s]", lDef, LS,
                        lPath));
            }
            Tool.createFileURL(lFile, lUrls, lDef, lErrors);

            for (String lib : lDef.getLibs()) {
                lFile = resolvePath(lib, lDef);
                Tool.createFileURL(lFile, lUrls, lDef, lErrors);
            }

            if (!lErrors.isEmpty()) {
                throw new UncheckedExtensionException(
                        String.format("Extension binary init failed: %s%s", LS, String.join(LS, lErrors)));
            } else {
                ClassLoader lRootLoader = lDef.hasAppScope() ? Thread.currentThread().getContextClassLoader()
                        : ClassLoader.getPlatformClassLoader();
                pCat.loader = new URLClassLoader(lUrls.toArray(new URL[lUrls.size()]), lRootLoader);

                pCat.clazz = pCat.loader.loadClass(lDef.getClassName());
                pCat.initConstructor(defaultContextClass);
                pCat.initRunMethod(defaultRunMethod);
            }

        } catch (UncheckedExtensionException e) {
            pCat.close();
            throw e;
        } catch (Exception e) {
            pCat.close();
            throw new UncheckedExtensionException(
                    String.format("Extension basic initialization failed [%s]%s%s", pCat.def, LS,
                            Tool.getStackTraceFrom(e)),
                    e);
        }
    }

    /**
     */
    protected ExtensionDef readDefinition(String pName) {
        String lJson;
        Path lDefFile = null;
        ExtensionDef lDef = null;

        try {
            lDefFile = Paths.get(pathBase.toString(), pName + ".json");
            if (Files.exists(lDefFile)) {
                lJson = new String(Files.readAllBytes(lDefFile), encoding);
                lDef = json.toObject(lJson, ExtensionDef.class);
            } else {
                throw new UncheckedExtensionException(
                        String.format("NO extension definition-file found [%s]", lDefFile));
            }
        } catch (IOException e) {
            throw new UncheckedExtensionException(
                    String.format("Error reading extension definition-file [%s]", lDefFile), e);
        }
        return lDef;
    }

    /**
     */
    protected Path resolvePath(String pPath, ExtensionDef pDef) {
        String lPathString = pPath.trim();
        Path lPath = lPathString.startsWith(".") ? Paths.get(pathBase.toString(), lPathString.substring(1))
                : Paths.get(lPathString);
        if (!Files.exists(lPath)) {
            throw new UncheckedExtensionException(
                    String.format("Unknown extension file path [%s] [%s]", lPath, pDef));
        }
        return lPath;
    }

    /**
     */
    protected String executeExtension(ExtensionCartridge pExt, String[] pArgs, ExtensionCallContext pCtx)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        Map<String, Object> contextData = null;
        if (pExt.useContext) {
            contextData = new HashMap<>();
            contextData.put("output", pCtx.getOutputConsumer());
        }
        Object lInstance = pExt.newInstance(contextData);
        return (String) pExt.runMethod.invoke(lInstance, (Object) pArgs);
    }

    /*******************************************************************************
    *******************************************************************************/
    /**
     */
    protected static class ExtensionCartridge {
        protected String name;
        protected ExtensionDef def;
        protected URLClassLoader loader;
        protected Class<?> clazz;
        protected Constructor<?> constructor = null;
        protected boolean useContext = false;

        protected Method runMethod = null;

        protected ExtensionCartridge(String pName, ExtensionDef pDef) {
            name = pName;
            def = pDef;
        }

        protected void close() {
            try {
                if (loader != null) {
                    loader.close();
                }
            } catch (Exception e) {
                throw new UncheckedExtensionException(
                        String.format("Closing Extension classloader failed [%s]", def), e);
            } finally {
                loader = null;
            }
        }

        /**
         */
        protected void initConstructor(Class<?> pContextClass) throws NoSuchMethodException, SecurityException {
            useContext = false;
            try {
                constructor = clazz.getConstructor(pContextClass);
                useContext = true;
            } catch (NoSuchMethodException e) {
                constructor = clazz.getConstructor();
            }
        }

        /**
         */
        protected void initRunMethod(String pDefaultName) throws NoSuchMethodException, SecurityException {
            if (def.hasRunMethod()) {
                runMethod = clazz.getMethod(def.getRunMethod(), String[].class);
            } else {
                try {
                    runMethod = clazz.getMethod(pDefaultName, String[].class);
                } catch (NoSuchMethodException e) {
                    LOG.warning(() -> String.format("NO run method found/defined for extension [%s]", def));
                }
            }
        }

        /**
         */
        protected Object newInstance(Map<String, Object> pCtx)
                throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
                InstantiationException {
            Object lInstance = null;

            if (useContext) {
                if (pCtx == null) {
                    pCtx = new HashMap<>();
                }
                pCtx.put("name", this.name);
                lInstance = constructor.newInstance(pCtx);
            } else {
                lInstance = constructor.newInstance();
            }
            return lInstance;
        }
    }

    /**
     */
    public static class ExtensionDef {
        protected String binPath = "";
        protected String devPath = "";
        protected List<String> libs = new ArrayList<>();
        protected String className = "";
        protected String runMethod = "";
        protected String scope = "";

        public ExtensionDef() {
        }

        public ExtensionDef(String pBinPath, String pClassName, List<String> pLibs) {
            this();
            binPath = pBinPath.trim();
            className = pClassName.trim();
            libs.addAll(pLibs);
        }

        public String getBinPath() {
            return binPath;
        }

        public String getDevPath() {
            return devPath;
        }

        public boolean hasDevPath() {
            String lPath = devPath.trim();
            // simulate a commenting out
            return (!lPath.isEmpty() && !lPath.startsWith("#"));
        }

        public boolean hasAppScope() {
            return scope.trim().equalsIgnoreCase("app");
        }

        public boolean hasRunMethod() {
            return !runMethod.trim().isEmpty();
        }

        public String getClassName() {
            return className;
        }

        public List<String> getLibs() {
            return libs;
        }

        public String getRunMethod() {
            return runMethod;
        }

        public String getScope() {
            return scope;
        }

        @Override
        public String toString() {
            return String.format("AppExtensionDef [%s : %s, method=%s, scope=%s]", className, binPath, runMethod,
                    scope);
        }

        // json excluded
        public boolean isEmpty() {
            return (binPath.isEmpty() || className.isEmpty());
        }
    }

    /**
     */
    public static class ExtensionCallContext {

        // the output consumer is used to forward java shell process output
        private Consumer<String> outputConsumer = null;
        private String result = null;

        public ExtensionCallContext() {
        }

        public ExtensionCallContext(Consumer<String> outputConsumer) {
            this.outputConsumer = outputConsumer;
        }

        public Consumer<String> getOutputConsumer() {
            return outputConsumer;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }

    /**
     */
    public static class UncheckedExtensionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedExtensionException(String pMsg) {
            super(pMsg);
        }

        public UncheckedExtensionException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

    /*******************************************************************************
    *******************************************************************************/

}
