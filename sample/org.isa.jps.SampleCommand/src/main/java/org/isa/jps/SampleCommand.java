/* Authored by iqbserve.de */
package org.isa.jps;

import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

/**
 * <pre>
 * A sample "command" extension.
 * 
 * Installation json file e.g.: "sample.Command.json" - in <jps-home>/extensions
 * {
 *   "binPath": "./bin/org.isa.jps.SampleCommand-0.0.1-SNAPSHOT.jar",
 *   "className" : "org.isa.jps.SampleCommand"
 * }
 * 
 * CLI call: runext sample.Command
 * 
 * </pre>
 */
public class SampleCommand {

    // the command name and the host output
    private String name; // the name is name of the json def file - NOT the class name
    private Consumer<String> hostOutput;

    // constructor with the standard host context map
    @SuppressWarnings("unchecked")
    public SampleCommand(Map<String, Object> pCtx) {
        name = (String) pCtx.getOrDefault("name", "unknown");
        hostOutput = (Consumer<String>) pCtx.get("output");
    }

    /**
     * The command execution method
     * doing some demo stuff
     */
    public String execute(String[] pArgs) {

        for (String arg : pArgs) {
            if (arg.equalsIgnoreCase("-h")) {
                echo(String.join("\n", "\nCommand Help:",
                        "Sample command extension that echos given arguments if any."));
                return null;
            }
        }

        echo(String.format("Start: runext [%s] [%s]", name, new Date()));

        if (pArgs.length > 0) {
            return "Echo args:" + "\n " + String.join("\n ", pArgs);
        } else {
            echo("<no args>");
        }

        return null;
    }

    // a wrapper method for the host output consumer
    private void echo(Object pVal) {
        hostOutput.accept(pVal.toString());
    }

}
