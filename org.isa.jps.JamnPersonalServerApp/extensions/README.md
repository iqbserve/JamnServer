### Jamn Personal Server - Extensions
JPS is intended as an instantly and ad hoc extensible toolbox - which on the one hand can be extended and programmed by scripts and on the other hand provides Network capability, platform Shell, Web and Java programs seamlessly.

For this purpose, JPS offers a programming interface by

* JavaScript - for User-Functions and WebGUI
* Java extensions - for User-Functions

Just as JavaScript extensions are simply included as JavaScript source files.

Java extensions are included as simple JSON description files.<br>In both cases, the file name is the unique ID for the extension.

Extension programs can be called from the JPS command line or from the WebGUI command view.<br>In addition, Java extensions can provide any methods as web services. JavaScript or shell programs can then also be called via this route.

Example of a minimum Java extension file e.g. [Sample Command Extension](https://github.com/integrating-architecture/JamnServer/blob/master/sample/org.isa.jps.SampleCommand/src/main/java/org/isa/jps/SampleCommand.java)

```json
{
    "binPath": "./bin/org.isa.jps.SampleCommand-0.0.1-SNAPSHOT.jar",
    "className": "org.isa.jps.SampleCommand"
}
```

Table of all Java "extension-file.json" attributes:

|attribute|sample value|comment|
|---|---|---|
|"binPath"|"./bin/Extension.jar"|mandatory - path to the java binaries|
|"className"|"org.xy.Class"|mandatory|
|||optional attributes|
|"runMethod"|"execute"| |
|"devPath"|"/somewhere/myFunction/target/classes"|a bin path for development, can be outcommented with a leading '#'|
|"libs"|["./bin/MyExtensionsBaseLib.jar"]|an array of jar lib pathes added to the extension classloader|
|"scope"|"app"|controls the classloader scope - app=application classloader, ""=only standard java SE platform|


### The obligatory "Hello world" program
By default there are No dependencies to the jamn server platform or any other external code needed.

```java
import java.util.Map;
import java.util.function.Consumer;

public class SampleCommand {

    private Consumer<String> hostOutput;

    // a constructor with a map parameter to receive host objects
    public SampleCommand(Map<String, Object> pCtx) {
        // get the basic output consumer adapter to the jamn app
        hostOutput = (Consumer<String>) pCtx.get("output");
    }

    /**
     * The command execute/run method
     */
    public String execute(String[] pArgs) {
		
		hostOutput.accept("Hello :-)");
        
		return "How can I help you ?";
    }
}
```


