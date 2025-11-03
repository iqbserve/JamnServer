/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.ipc.JamnWebSocketProvider;
import org.isa.ipc.JamnWebSocketProvider.WsoMessageProcessor;
import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.CommonHelper;
import org.isa.jps.JamnPersonalServerApp.Config;
import org.isa.jps.comp.OperatingSystemInterface.ShellProcess;
import org.isa.jps.comp.OperatingSystemInterface.ShellProcessListener;

/**
 * <pre>
 * The ChildProcessManager provides the ability to create JamnPersonalServerApp instances 
 * as a separate java child process running in a their own JavaVM.
 * 
 * The instance is created with a "profile" parameter
 * that specifies functionality and features of the instance.
 * 
 * The default use case is the "child" profile.
 * </pre>
 */
public class ChildProcessManager implements ShellProcessListener {

    protected static final String CMD_CLOSE = "close";
    protected static final String HANDSHAKE_REQUEST = "handshake.request";
    protected static final String HANDSHAKE_RESPONSE = "handshake.response";

    public static Builder newBuilder() {
        ChildProcessManager lManager = new ChildProcessManager();
        return lManager.new Builder();
    }

    protected static Logger LOG = Logger.getLogger(ChildProcessManager.class.getName());
    protected static final String LS = System.lineSeparator();

    protected static CommonHelper Tool = JamnPersonalServerApp.Tool;
    protected static final String PROFILE = "-" + JamnPersonalServerApp.JPS_PROFILE;
    protected static final String CHILD = JamnPersonalServerApp.CHILD_PROFILE;
    protected static final String CHILD_ID = "-" + JamnPersonalServerApp.JPS_CHILD_ID;
    protected static final String PARENT_URL = "-" + JamnPersonalServerApp.JPS_PARENT_URL;

    protected static String Arg(String pKey, String pVal) {
        return pKey + "=" + pVal;
    }

    protected Map<String, ChildProcess> processMap = Collections.synchronizedMap(new HashMap<>());
    protected Map<String, String> processConnections = Collections.synchronizedMap(new HashMap<>());

    protected OperatingSystemInterface osIFace;
    protected JamnWebSocketProvider wsoProvider;
    protected JsonToolWrapper json;

    protected String appHome;
    protected Config config;
    protected String workspaceRoot;
    protected ChildProcessDef defaultChildDef;
    protected ChildMessageReceiver childMsgReceiver;
    protected String parentUrl;
    protected String wsApiPath;

    protected ChildProcessManager() {
    }

    /**
     * @throws IOException
     */
    public void initialize() throws IOException {
        // default "/childapi"
        wsApiPath = config.getChildWebSocketUrlRoot();
        workspaceRoot = Tool.ensureSubDir(config.getWorkspaceRoot(), Paths.get(appHome)).toString();

        defaultChildDef = new ChildProcessDef()
                .setClassPath(Paths.get(appHome, "libs").toString() + File.separator + "*")
                .addOption("-Xms64m").addOption("-Xmx256m")
                .addDebugOption(config.getJVMDebugOption())
                .setMainClass(JamnPersonalServerApp.class.getName())
                .setWorkDir(workspaceRoot)
                .setDebug(config.isChildProcessDebugEnabled());

        parentUrl = "ws://localhost:" + config.getPort() + wsApiPath;

        childMsgReceiver = new ChildMessageReceiver();
        wsoProvider.addConnectionPath(wsApiPath);
        wsoProvider.addMessageProcessor(childMsgReceiver, wsApiPath);
    }

    /**
     */
    public String createProcess() {

        ChildProcess lChild = new ChildProcess(UUID.randomUUID().toString());
        ChildProcessDef lDef = new ChildProcessDef(defaultChildDef);

        // since child processes run in their own VM they do NOT have an IDE classpath
        // if present in config
        // - it is possible to set an alternative class path for development
        // e.g. something like -Ddev.libs.path =
        // c:\..\..\org.isa.jps.JamnPersonalServerApp\dist\jps.home\libs\*
        if (!config.getDevLibsPath().isEmpty()) {
            lDef.setClassPath(config.getDevLibsPath());
        }

        String[] lArgs = new String[] {
                Arg(PROFILE, CHILD),
                Arg(CHILD_ID, lChild.getId()),
                Arg(PARENT_URL, parentUrl)
        };
        lChild.setProcess(osIFace.new ShellProcess(lChild.getId())
                .setWorkingDir(lDef.getWorkDir())
                .setCommand(lDef.getCommandLineFor(lChild, lArgs))
                .setListener(this));

        processMap.put(lChild.getId(), lChild);

        new Thread(() -> lChild.getProcess().start()).start();

        return lChild.getId();
    }

    /**
     * ShellProcess listener interface
     */
    @Override
    public void onShellClosed(String pId) {
        ChildProcess lChild = processMap.get(pId);
        if (lChild != null) {
            lChild.setClosed();
            LOG.info(() -> String.format("Child Process closed [%s]%s%s", pId, LS,
                    String.join(LS, lChild.getProcess().getOutput())));
        }
    }

    /**
     */
    public void closeProcess(String pId) {
        if ("all".equals(pId)) {
            processMap.values().forEach(process -> sendCommand(process.getId(), CMD_CLOSE));
        } else {
            sendCommand(pId, CMD_CLOSE);
        }
    }

    /**
     */
    public String sendCommand(String pId, String pCommand) {
        CPComData lComData = new CPComData(pId).setCommand(pCommand);
        String lJsonMsg = json.toString(lComData);
        String lResult = "";
        ChildProcess lChild = processMap.get(pId);
        if (lChild != null) {
            wsoProvider.sendMessageTo(lChild.getConnectionId(), lJsonMsg.getBytes());
        }
        return lResult;
    }

    /**
     */
    public List<String> getProcessList() {
        return processMap.entrySet()
                .stream()
                .map(e -> e.getKey() + " - open=" + e.getValue().isOpen())
                .collect(Collectors.toList());
    }

    /**
     * <pre>
     * Example client message receiver that is called for every message
     * sent by a child to its connected server.
     * </pre>
     */
    protected class ChildMessageReceiver implements WsoMessageProcessor {

        @Override
        public byte[] onMessage(String pConnectionId, byte[] pMessage) {
            byte[] lReturnMessage = new byte[0];

            CPComData lComData = json.toObject(new String(pMessage), CPComData.class);

            if (lComData.hasInfo(HANDSHAKE_REQUEST)) {
                String lChildId = lComData.getChildId();
                ChildProcess lChild = processMap.get(lChildId);
                lChild.setConnectionId(pConnectionId);
                lChild.setOpen();
                processConnections.put(pConnectionId, lChildId);

                lComData.setInfo(HANDSHAKE_RESPONSE);
                lReturnMessage = json.toString(lComData).getBytes();
                LOG.info(() -> String.format("Child Process connection handshake done [%s] [%s]", lChildId,
                        pConnectionId));
            }

            return lReturnMessage;
        }
    }

    /**
     */
    public static class ChildProcessDef {
        protected boolean debug = false;
        protected String processId;
        protected String javaHome;
        protected String javaExe;
        protected String classPath;
        protected List<String> options = new ArrayList<>();
        protected List<String> debugOptions = new ArrayList<>();
        protected String mainClass;
        protected String workDir;

        public ChildProcessDef() {
            javaHome = System.getProperty("java.home");
            javaExe = Paths.get(javaHome, "bin", "java.exe").toString();
        }

        public ChildProcessDef(ChildProcessDef pOrg) {
            this.debug = pOrg.debug;
            this.javaHome = pOrg.javaHome;
            this.javaExe = pOrg.javaExe;
            this.classPath = pOrg.classPath;
            this.options.addAll(pOrg.options);
            this.debugOptions.addAll(pOrg.debugOptions);
            this.mainClass = pOrg.mainClass;
            this.workDir = pOrg.workDir;
        }

        /**
         */
        public String[] getCommandLineFor(ChildProcess pChild, String... pArgs) {
            processId = pChild.getId();
            List<String> lCmdParts = new ArrayList<>();

            lCmdParts.add(javaExe);
            lCmdParts.addAll(options);
            if (debug) {
                lCmdParts.addAll(debugOptions);
            }
            lCmdParts.add("-cp");
            lCmdParts.add(classPath);
            lCmdParts.add(mainClass);
            if (pArgs != null && pArgs.length > 0) {
                lCmdParts.addAll(Arrays.asList(pArgs));
            }
            return lCmdParts.toArray(new String[] {});
        }

        public boolean isDebug() {
            return debug;
        }

        public ChildProcessDef setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public ChildProcessDef setJavaHome(String javaHome) {
            this.javaHome = javaHome;
            return this;
        }

        public ChildProcessDef setJavaExe(String javaExe) {
            this.javaExe = javaExe;
            return this;
        }

        public ChildProcessDef setClassPath(String classPath) {
            this.classPath = classPath;
            return this;
        }

        public ChildProcessDef addOption(String option) {
            this.options.add(option);
            return this;
        }

        public ChildProcessDef addDebugOption(String option) {
            this.debugOptions.add(option);
            return this;
        }

        public ChildProcessDef setMainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public ChildProcessDef setWorkDir(String workDir) {
            this.workDir = workDir;
            return this;
        }

        public String getJavaHome() {
            return javaHome;
        }

        public String getJavaExe() {
            return javaExe;
        }

        public String getClassPath() {
            return classPath;
        }

        public List<String> getOptions() {
            return options;
        }

        public String getMainClass() {
            return mainClass;
        }

        public String getWorkDir() {
            return workDir;
        }
    }

    /**
     */
    protected class ChildProcess {
        protected boolean open = false;
        protected String connectionId = "";
        protected String id = "";
        protected ShellProcess process;

        protected ChildProcess(String pId) {
            id = pId;
        }

        public String getId() {
            return id;
        }

        public String getConnectionId() {
            return connectionId;
        }

        public void setConnectionId(String connectionId) {
            this.connectionId = connectionId;
        }

        public boolean isOpen() {
            return open;
        }

        public void setOpen() {
            this.open = true;
        }

        public void setClosed() {
            this.open = false;
        }

        public ShellProcess getProcess() {
            return process;
        }

        public void setProcess(ShellProcess process) {
            this.process = process;
        }
    }

    /**
     */
    public class Builder {
        protected Builder() {
        }

        public Builder setWebSocketProvider(JamnWebSocketProvider pVal) {
            wsoProvider = pVal;
            return this;
        }

        public Builder setOperatingSystemInterface(OperatingSystemInterface pVal) {
            osIFace = pVal;
            return this;
        }

        public Builder setJsonTool(JsonToolWrapper pJsonTool) {
            json = pJsonTool;
            return this;
        }

        public Builder setAppHome(Path pVal) {
            appHome = pVal.toString();
            return this;
        }

        public Builder setConfig(Config pVal) {
            config = pVal;
            return this;
        }

        public ChildProcessManager build() {
            return ChildProcessManager.this;
        }
    }

    /**
     * A unified message data structure for the parent<->child communication.
     */
    public static class CPComData {
        protected String childId = "";
        protected String info = "";
        protected String type = "";
        protected String command = "";

        public CPComData() {
        }

        public CPComData(String pChildId) {
            childId = pChildId;
        }

        public boolean hasInfo(String pInfo) {
            return info.equals(pInfo);
        }

        public boolean hasInfo() {
            return !info.isEmpty();
        }

        public boolean isCmd() {
            return !command.isEmpty();
        }

        public boolean isCmd(String pCmd) {
            return command.equals(pCmd);
        }

        public String getChildId() {
            return childId;
        }

        public String getInfo() {
            return info;
        }

        public CPComData setInfo(String info) {
            this.info = info;
            return this;
        }

        public String getType() {
            return type;
        }

        public String getCommand() {
            return command;
        }

        public CPComData setChildId(String childId) {
            this.childId = childId;
            return this;
        }

        public CPComData setType(String type) {
            this.type = type;
            return this;
        }

        public CPComData setCommand(String command) {
            this.command = command;
            return this;
        }
    }

    /**
     */
    public static class UncheckedProcessManagerException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UncheckedProcessManagerException(String pMsg) {
            super(pMsg);
        }

        UncheckedProcessManagerException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

}
