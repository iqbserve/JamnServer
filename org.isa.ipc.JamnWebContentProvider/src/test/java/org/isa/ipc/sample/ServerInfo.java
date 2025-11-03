/* Authored by iqbserve.de */
package org.isa.ipc.sample;

import java.util.HashMap;
import java.util.Map;

/**
 * <pre>
 * A sample data structure
 * to inject into the javascript app module as ServerInfo.
 * </pre>
 */
public class ServerInfo {

    protected String name = "";
    protected String version = "";
    protected String description = "";

    protected Map<String, String> links = new HashMap<>();

    public Map<String, String> getLinks() {
        return links;
    }

    public ServerInfo addLink(String pKey, String pValue) {
        links.put(pKey, pValue);
        return this;
    }

    public String getName() {
        return name;
    }

    public ServerInfo setName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public ServerInfo setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ServerInfo setDescription(String description) {
        this.description = description;
        return this;
    }
}
