/* Authored by iqbserve.de */
package org.isa.jps.comp;

import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.CommonHelper;

/**
 * This component realizes a simple REPL console interface.
 */
public class CommandLineInterface {

    protected static final String LS = System.lineSeparator();

    protected static final Logger LOG = Logger.getLogger(CommandLineInterface.class.getName());
    protected static final CommonHelper Tool = JamnPersonalServerApp.Tool;
    protected static final CliCommand UnknownCommand = new CliCommand("",
            args -> String.format("unknown command [%s] - for help type: [? | h | help]", args.get(0)));
    //read input file - command name
    protected static final String RIF = "rif";

    protected Thread worker;
    protected boolean work = false;
    protected Map<String, CliCommand> commands = new HashMap<>();
    protected Function<String, String> commandProcessor;
    protected ConsoleWrapper console;
    protected Path inputFile;
    protected Charset standardEncoding = StandardCharsets.UTF_8;

    protected Predicate<String> defaultHelpChecker = input -> ("?".equals(input) || "h".equals(input)
            || "help".equals(input));

    protected Function<String[], String> descrFormatter = parts -> {
        String lArgs = parts[1].isEmpty() ? "[no args]" : parts[1];
        return new StringBuilder(parts[0]).append(" : ").append(lArgs).append(" - ").append(parts[2]).toString();
    };

    public CommandLineInterface() {
        commandProcessor = createDefaultCommandProcessor();
        console = new ConsoleWrapper(() -> "jps>");
    }

    /**
     */
    public void echo(Object pVal){
        if(console!=null){
            console.printValue(pVal);
        }
    }

    /**
     */
    public CommandLineInterface setInputFile(Path pFile) {
        inputFile = pFile;
        return this;
    }

    /**
     */
    public CommandLineInterface setEncoding(Charset pEncoding) {
        standardEncoding = pEncoding;
        return this;
    }

    /**
     */
    public synchronized void start() {
        if (worker == null) {
            if (console.useSysConsole()) {
                LOG.info(() -> "Start CLI on system console");
            } else {
                LOG.info(
                        () -> "Start CLI on Standard-IO (limited functionality - cause system console is not available)");
            }
            work = true;
            worker = new Thread(() -> run(commandProcessor));
            worker.start();
        }
    }

    /**
     */
    public synchronized void stop() {
        work = false;
    }

    /**
     */
    public String execCmdBlank(String pCmdLine) {
        return commandProcessor.apply(pCmdLine);
    }

    /**
     */
    public String newDefaultDescr(String pName, String pArgs, String pText) {
        return descrFormatter.apply(new String[] { pName, pArgs, pText });
    }

    /**
     */
    public void setDescrFormatter(Function<String[], String> descrFormatter) {
        this.descrFormatter = descrFormatter;
    }

    /**
     */
    public CommandBuilder newCommandBuilder() {
        return new CommandBuilder();
    }

    /*********************************************************
     * internal methods and classes
     *********************************************************/

    protected String getCommandLineFromInputFile() {
        String lCommandLine = "<![CDATA[  **CLI input file error**  ]]>";
        if (!Files.exists(inputFile)) {
            LOG.severe(() -> String.format("CLI Inputfile does NOT exist [%s]", inputFile));
        } else {
            try {
                lCommandLine = new String(Files.readAllBytes(inputFile), standardEncoding);
            } catch (IOException e) {
                LOG.severe(() -> String.format("Error reading CLI Inputfile [%s] [%s]", inputFile, e));
            }
        }
        return lCommandLine;
    }

    /**
     * <pre>
     * Default implementation of a command processor
     * that gets invoked for a command line typed and submitted in the REPL console.
     * </pre>
     */
    protected Function<String, String> createDefaultCommandProcessor() {
        return commandLine -> {
            String name = "";
            String[] args = {};
            String[] token = null;

            commandLine = commandLine == null ? "" : commandLine.trim();
            if (commandLine.equalsIgnoreCase(RIF) || commandLine.startsWith(RIF+" ")) {
                commandLine = getCommandLineFromInputFile();
            }

            token = Tool.parseCommandLine(commandLine);

            if (token.length >= 1) {
                name = token[0];
            }else{
                return "";
            }
            if (token.length >= 2) {
                args = new String[token.length - 1];
                System.arraycopy(token, 1, args, 0, args.length);
            }
            if (defaultHelpChecker.test(name)) {
                return getHelp();
            }
            CliCommand cmd = commands.getOrDefault(name, UnknownCommand);
            if (cmd == UnknownCommand) {
                return cmd.execute(new String[]{name}, console);
            }
            if (args.length > 0 && defaultHelpChecker.test(args[0])) {
                return cmd.getDescr();
            }

            return cmd.execute(args, console);
        };
    }

    /**
     * The REPL implementation
     */
    protected void run(Function<String, String> pCommandProcessor) {
        String lInputLine = "";
        String lResultValue = "";

        while (work) {
            try {
                // R-ead a line from System.in
                lInputLine = console.readNextLine();
                // E-val command line with the pCommandProcessor
                lResultValue = pCommandProcessor.apply(lInputLine);
                // P-rint a possible return to out stream
                if (lResultValue != null && !lResultValue.trim().isEmpty()) {
                    console.printValue(lResultValue);
                }
                // L-oop
            } catch (Exception e) {
                LOG.severe(() -> String.format("CLI ERROR [%s]%s%s", e, LS, Tool.getStackTraceFrom(e)));
            }
        }
    }

    /**
     */
    protected void addCommand(CliCommand pCmd) {
        commands.put(pCmd.getName(), pCmd);
    }

    /**
     */
    protected String getHelp() {
        StringBuilder lBuilder = new StringBuilder(LS)
            .append(this.newDefaultDescr(RIF, "[no args]", String.format("Read command from input file: [%s]", inputFile))).append(LS)
            .append(commands.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().getDescr())
                .collect(Collectors.joining(LS))
            )
            .append(LS);

        return lBuilder.toString();
    }

    /**
     */
    public static class CommandCallContext {
        private List<String> args;
        private ConsoleWrapper console;
        private Map<String, String> argMap;

        private CommandCallContext() {
        }

        private CommandCallContext(String[] pArgs, ConsoleWrapper pConsole) {
            args = new ArrayList<>(Arrays.asList(pArgs));
            console = pConsole;
        }

        public String get(int pIdx) {
            if (pIdx < args.size()) {
                return args.get(pIdx);
            }
            return "";
        }

        public boolean hasArg(String pKey) {
            return args.contains(pKey);
        }

        public boolean hasArg(int pIdx, String pKey) {
            return get(pIdx).equals(pKey);
        }

        public boolean hasFlag(String pKey, Object pVal) {
            parseArgsToMap();
            if (argMap.containsKey(pKey) && pVal != null) {
                return argMap.get(pKey).trim().equalsIgnoreCase(pVal.toString().trim());
            }
            return false;
        }

        public List<String> getArgsList() {
            return args;
        }

        public String[] getArgsArray() {
            return args.toArray(new String[args.size()]);
        }

        public Map<String, String> parseArgsToMap() {
            return parseArgsToMap(Tool.defaultArgParser);
        }

        public boolean getConfirmation(String pMessage) {
            return getConfirmation(pMessage, "y"::equals);
        }

        public boolean getConfirmation(String pMessage, Predicate<String> pPredicate) {
            String lRet = console.readConfirmationLine(pMessage);
            return pPredicate.test(lRet);
        }

        protected Map<String, String> parseArgsToMap(Function<String[], Map<String, String>> pParser) {
            if (argMap == null) {
                argMap = pParser.apply(args.toArray(new String[] {}));
            }
            return argMap;
        }
    }

    /**
     */
    private static class CliCommand {
        private String name = "";
        private String descr = "";
        private Function<CommandCallContext, String> function = ctx -> "";

        private CliCommand() {
        }

        private CliCommand(String pKey, Function<CommandCallContext, String> pFunction) {
            name = pKey;
            function = pFunction;
        }

        public String execute(String[] pArgs, ConsoleWrapper pConsole) {
            return function.apply(new CommandCallContext(pArgs, pConsole));
        }

        public String getName() {
            return name;
        }

        public String getDescr() {
            return descr;
        }
    }

    public class CommandBuilder {
        private CliCommand cmd = new CliCommand();

        private CommandBuilder() {
        }

        public CommandBuilder name(String pName) {
            cmd.name = pName;
            return this;
        }

        public CommandBuilder descr(Function<String, String> pFnc) {
            cmd.descr = pFnc.apply(cmd.name);
            return this;
        }

        public CommandBuilder function(Function<CommandCallContext, String> pFunction) {
            cmd.function = pFunction;
            return this;
        }

        public void build() {
            addCommand(cmd);
        }
    }

    /**
     */
    public static class ConsoleWrapper {
        private Supplier<String> prompt;
        private PrintStream outStream;
        private Console sysConsole;
        private Scanner scanner;

        private ConsoleWrapper() {
        }

        private ConsoleWrapper(Supplier<String> pPrompt) {
            this();
            prompt = pPrompt;
            sysConsole = System.console();
            if (sysConsole == null) {
                outStream = System.out;
                scanner = new Scanner(System.in);
            }
        }

        /**
         */
        public boolean useSysConsole() {
            return sysConsole != null;
        }

        /**
         */
        public String readNextLine() {
            String lInput = "";
            if (useSysConsole()) {
                printPrompt();
                lInput = sysConsole.readLine();
            } else {
                printPrompt();
                lInput = scanner.nextLine();
            }
            if (lInput != null) {
                lInput = lInput.trim();
            }
            return lInput;
        }

        /**
         */
        public String readConfirmationLine(String pMsg) {
            String lInput = "";
            if (useSysConsole()) {
                sysConsole.printf(pMsg);
                lInput = sysConsole.readLine();
            } else {
                outStream.print(pMsg);
                lInput = scanner.nextLine();
            }
            if (lInput != null) {
                lInput = lInput.trim();
            }
            return lInput;
        }

        /**
         */
        protected void printValue(Object pValue) {
            if (pValue != null) {
                if (useSysConsole()) {
                    sysConsole.printf("%s%s", pValue, LS);
                } else {
                    outStream.println(pValue);
                }
            }
        }

        /**
         */
        protected void printPrompt() {
            if (useSysConsole()) {
                sysConsole.printf(prompt.get());
            } else {
                outStream.print(prompt.get());
            }
        }

    }

}
