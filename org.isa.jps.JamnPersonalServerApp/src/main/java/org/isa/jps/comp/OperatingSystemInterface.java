/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.Config;

/**
 * <pre>
 * The class provides a rudimentary abstraction for operating system specifics.
 * The main Interface is "OSFunctions" accessible via the fnc() method.
 * 
 * The main functionality is a "ShellProcess" wrapper and a "shellCmd" method.
 * </pre>
 */
public class OperatingSystemInterface {

    protected static final String LS = System.lineSeparator();
    protected static boolean Windows = true;
    protected static boolean Unix = false;
    static {
        String lName = System.getProperty("os.name").toLowerCase();
        Windows = lName.contains("win");
        Unix = (lName.contains("nix") || lName.contains("nux") || lName.contains("aix"));
    }

    protected OSFunctions osFunctions;
    protected OSIFaceSecurityController securityCtrl = new OSIFaceSecurityController() {

    };

    protected Charset shellEncoding = StandardCharsets.UTF_8;
    protected Config config;

    /**
     */
    public OperatingSystemInterface(Config pConfig, OSIFaceSecurityController pSecCtrl) {
        config = pConfig;
        if (pSecCtrl != null) {
            securityCtrl = pSecCtrl;
        }

        if (Windows) {
            shellEncoding = Charset.forName(config.getWinShellEncoding());
            osFunctions = new WinowsFunctions();
        } else if (Unix) {
            shellEncoding = Charset.forName(config.getUnixShellEncoding());
            osFunctions = new UnixFunctions();
        }
    }

    /**
     */
    public boolean isOnWindows() {
        return Windows;
    }

    /**
     */
    public boolean isOnUnix() {
        return Unix;
    }

    /**
     */
    public OSFunctions fnc() {
        return osFunctions;
    }

    /**
     * The function interface
     */
    public static interface OSFunctions {

        /**
         */
        public List<String> shellCmd(String[] pCmdParts, String pWorkingDir, boolean pInherit,
                Consumer<String> pOutputConsumer);

    }

    /**
     */
    public static interface OSIFaceSecurityController {
        default boolean isPathAccessAlowed(String pPath) {
            return true;
        }
    }

    /**
     */
    protected abstract class AbstractOSFunctions implements OSFunctions {

        protected AbstractOSFunctions() {
        }

        @Override
        public List<String> shellCmd(String[] pCmdParts, String pWorkingDir, boolean pInherit,
                Consumer<String> pOutputConsumer) {
            ShellProcess lSh = new ShellProcess()
                    .setCommand(pCmdParts)
                    .setWorkingDir(pWorkingDir)
                    .setInherit(pInherit)
                    .setOutputConsumer(pOutputConsumer);
            lSh.start();
            return lSh.getOutput();
        }
    }

    /**
     */
    public static interface ShellProcessListener {
        /**
         */
        void onShellClosed(String pId);
    }

    /**
     */
    public class ShellProcess {
        protected String id = "";
        protected ShellProcessListener listener = id -> {
        };
        protected Consumer<String> outputConsumer = null;
        protected List<String> command;
        protected String workingDir;
        protected boolean inherit = false;

        protected Process process = null;
        protected List<String> outPut = new ArrayList<>();

        protected ShellProcess() {
        }

        public ShellProcess(String pId) {
            id = pId;
        }

        /**
         */
        public ShellProcess setCommand(String[] pCmdParts) {
            command = new ArrayList<>();

            if (Windows) {
                command.add(0, "cmd");
                command.add(1, "/c");
            }
            command.addAll(Arrays.asList(pCmdParts));
            return this;
        }

        /**
         */
        public ShellProcess setWorkingDir(String pWorkingDir) {
            workingDir = resolveWorkingDir(pWorkingDir);
            return this;
        }

        /**
         */
        public ShellProcess setListener(ShellProcessListener pListener) {
            this.listener = pListener;
            return this;
        }

        /**
         */
        public ShellProcess setOutputConsumer(Consumer<String> pConsumer) {
            this.outputConsumer = pConsumer;
            return this;
        }

        /**
         */
        public ShellProcess setInherit(boolean pInherit) {
            inherit = pInherit;
            return this;
        }

        /**
         */
        public Process getProcess() {
            return process;
        }

        /**
         */
        public List<String> getOutput() {
            return new ArrayList<>(this.outPut);
        }

        /**
         */
        public String getId() {
            return id;
        }

        /**
         */
        public void start() {
            String line = "";
            ProcessBuilder builder = null;

            try {

                builder = new ProcessBuilder();
                builder.command(command);
                builder.redirectErrorStream(true);
                if (inherit) {
                    builder.inheritIO();
                }

                if (workingDir != null && !workingDir.isEmpty()) {
                    Path lPath = Paths.get(workingDir);
                    builder.directory(lPath.toFile());
                }
                process = builder.start();

                try (BufferedReader stdInput = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), shellEncoding));) {

                    while ((line = stdInput.readLine()) != null) {
                        if (outputConsumer != null) {
                            outputConsumer.accept(line);
                        } else {
                            outPut.add(line);
                        }
                    }
                }

                process.waitFor();

            } catch (InterruptedException | IOException e) {
                throw new UncheckedOSIFaceException(
                        String.format("ERROR executing ShellProcess [%s]%s[%s]", String.join(" ", command), LS,
                                e.getMessage()),
                        e);
            } finally {
                close();
            }
        }

        /**
         */
        public void close() {
            if (process != null) {
                process.destroy();
                process.destroyForcibly();
            }
            if (listener != null) {
                listener.onShellClosed(id);
            }
        }
    }

    /**
     */
    protected String resolveWorkingDir(String pPath) {
        if (pPath == null || pPath.isEmpty()) {
            pPath = JamnPersonalServerApp.getInstance().getHomePath().toString();
        }

        if (!securityCtrl.isPathAccessAlowed(pPath)) {
            throw new UncheckedOSIFaceException(String.format("Path Access denied [%s]", pPath));
        }
        return pPath;
    }

    /**
     */
    protected class WinowsFunctions extends AbstractOSFunctions {
        protected WinowsFunctions() {
            super();
        }
    }

    /**
     */
    protected class UnixFunctions extends AbstractOSFunctions {
        protected UnixFunctions() {
            super();
        }
    }

    /**
     */
    public static class UncheckedOSIFaceException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedOSIFaceException(String pMsg) {
            super(pMsg);
        }

        public UncheckedOSIFaceException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

}
