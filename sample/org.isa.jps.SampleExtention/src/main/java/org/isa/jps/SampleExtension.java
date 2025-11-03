/* Authored by iqbserve.de */
package org.isa.jps;

import static org.isa.ipc.JamnServer.HttpHeader.FieldValue.APPLICATION_JSON;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.ipc.JamnWebServiceProvider.WebService;
import org.isa.ipc.JamnWebServiceProvider.WebServiceDefinitionException;
import org.isa.jps.comp.DocumentStoreAdapter;
import org.isa.jps.comp.DocumentStoreAdapter.DocumentStore;
import org.isa.jps.comp.DocumentStoreAdapter.PersistentDocument;

/**
 * <pre>
 * Jamn sample extension using app access to provide functionality as web services.
 * The sample provides db connection informations.
 * 
 * Installation json file e.g.: "sample.Extension.json" - in <jps-home>/extensions
 * {
 *   "binPath": "./bin/org.isa.jps.SampleExtension-0.0.1-SNAPSHOT.jar",
 *   "className" : "org.isa.jps.SampleExtension",
 *   "scope" : "app"
 * }
 * 
 * </pre>
 */
public class SampleExtension {

    // WebApi service end point names
    protected static final String WSP_get_dbconnections = "/service/get-db-connections";
    protected static final String WSP_save_dbconnections = "/service/save-db-connections";
    protected static final String WSP_delete_dbconnections = "/service/delete-db-connections";

    protected static Logger LOG = Logger.getLogger(SampleExtension.class.getName());
    protected static final String LS = System.lineSeparator();

    protected static DocumentStore DocStore = DocumentStoreAdapter.getExtensionStore();

    protected Charset standardEncoding;
    protected JsonToolWrapper jsonTool;
    protected Map<String, DbConnectionDef> connectionMap;
    protected String name;
    protected String dataDocName;

    protected Map<String, Object> ctx;

    /**
     * <pre>
     * Public default constructor with app context map.
     * </pre>
     */
    public SampleExtension(Map<String, Object> pCtx) throws WebServiceDefinitionException, IOException {
        ctx = pCtx;
        name = (String) ctx.getOrDefault("name", "unknown");
        dataDocName = name + ".db-connections";
        initialize();
    }

    /**
     */
    protected void initialize() throws IOException, WebServiceDefinitionException {
        JamnPersonalServerApp lApp = JamnPersonalServerApp.getInstance();
        standardEncoding = Charset.forName(lApp.getConfig().getStandardEncoding());
        jsonTool = lApp.getJsonTool();
        loadConnections();

        // register the webservice interface
        lApp.registerWebServices(new DbConnectionWebServiceApi());

        // register a commad to show all known db connections on the cli
        lApp.getCli().newCommandBuilder()
                .name("dbconnections")
                .descr(cmdName -> lApp.getCli().newDefaultDescr(cmdName, "", "Show all defined db connections"))
                .function(cmdCtx -> connectionMap.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + " = " + e.getValue().getUrl())
                        .collect(Collectors.joining(LS)))
                .build();
    }

    /**
     */
    protected void loadConnections() throws IOException {
        PersistentDocument lDoc = DocStore.find(dataDocName);

        if (lDoc.getContent().isEmpty()) {
            lDoc.setContent(jsonTool.toString(createDemoData()));
            DocStore.save(lDoc);
        }

        connectionMap = new LinkedHashMap<>();
        DbConnectionDef[] lDefList = jsonTool.toObject(lDoc.getContent(), DbConnectionDef[].class);
        for (DbConnectionDef def : lDefList) {
            connectionMap.put(def.getName(), def);
        }
    }

    /**
     */
    protected void saveConnections(List<DbConnectionDef> pChanges) throws IOException {
        PersistentDocument lDoc = new PersistentDocument(dataDocName);
        Map<String, DbConnectionDef> lConnections = new LinkedHashMap<>(connectionMap);

        for (DbConnectionDef def : pChanges) {
            lConnections.put(def.getName(), def);
        }
        lDoc.setContent(jsonTool.toString(lConnections.values()));
        DocStore.save(lDoc);
        connectionMap = lConnections;
    }

    /**
     */
    protected void deleteConnections(List<DbConnectionDef> pChanges) throws IOException {
        PersistentDocument lDoc = new PersistentDocument(dataDocName);
        Map<String, DbConnectionDef> lConnections = new LinkedHashMap<>(connectionMap);

        for (DbConnectionDef def : pChanges) {
            lConnections.remove(def.getName());
        }
        lDoc.setContent(jsonTool.toString(lConnections.values()));
        DocStore.save(lDoc);
        connectionMap = lConnections;
    }

    /**
     */
    protected List<DbConnectionDef> createDemoData() {
        List<DbConnectionDef> lDefList = new ArrayList<>();
        lDefList.add(new DbConnectionDef()
                .setName("Oracle Test-Server")
                .setType("oracle")
                .setUrl("jdbc:oracle:thin:@TS-ORA:1521/XEPDB1")
                .setUser("admin"));
        lDefList.add(new DbConnectionDef()
                .setName("MySQL Development-Server")
                .setType("mysql")
                .setUrl("jdbc:mysql://DS-MSQL:3306/DVLPDB3")
                .setUser("devel"));

        return lDefList;
    }

    /**
     * Encapsulate the webservices in a separate api class
     */
    protected class DbConnectionWebServiceApi {
        protected static final String StatusOk = "ok";
        protected static final String StatusError = "error";

        protected DbConnectionWebServiceApi() {
            // keep instantiation internal
        }

        @WebService(path = WSP_get_dbconnections, methods = { "POST" }, contentType = APPLICATION_JSON)
        public DbConnectionResponse getDbConnections() {
            DbConnectionResponse lResponse = new DbConnectionResponse();
            lResponse.addAllConnections(connectionMap);
            return lResponse;
        }

        @WebService(path = WSP_save_dbconnections, methods = { "POST" }, contentType = APPLICATION_JSON)
        public DbConnectionResponse saveDbConnections(DbConnectionRequest pRequest) {
            DbConnectionResponse lResponse = new DbConnectionResponse().setStatusOk();
            try {
                saveConnections(pRequest.getConnections());
            } catch (Exception e) {
                lResponse.setStatusError(String.format("Failed to save DB Connections [%s]", e));
            }
            return lResponse;
        }

        @WebService(path = WSP_delete_dbconnections, methods = { "POST" }, contentType = APPLICATION_JSON)
        public DbConnectionResponse deleteDbConnections(DbConnectionRequest pRequest) {
            DbConnectionResponse lResponse = new DbConnectionResponse().setStatusOk();
            try {
                deleteConnections(pRequest.getConnections());
            } catch (Exception e) {
                lResponse.setStatusError(String.format("Failed to delete DB Connections [%s]", e));
            }
            return lResponse;
        }

        /**
         * the webservice dtos
         */
        /**
         */
        public static class DbConnectionRequest {
            private List<DbConnectionDef> connections = new ArrayList<>();

            public List<DbConnectionDef> getConnections() {
                return connections;
            }
        }

        /**
         */
        public static class DbConnectionResponse {
            private List<DbConnectionDef> connections = new ArrayList<>();
            private String status = "";
            @SuppressWarnings("unused")
            private String error = "";

            private DbConnectionResponse setStatus(String status) {
                this.status = status;
                return this;
            }

            public DbConnectionResponse setStatusOk() {
                return setStatus(StatusOk);
            }

            public DbConnectionResponse setStatusError(String pErrorMsg) {
                error = pErrorMsg;
                return setStatus(StatusError);
            }

            public boolean isStatus(String pVal) {
                return status.equals(pVal);
            }

            public DbConnectionResponse addConnection(DbConnectionDef pDef) {
                connections.add(pDef);
                return this;
            }

            public DbConnectionResponse addAllConnections(Map<String, DbConnectionDef> pConnections) {
                connections.addAll(pConnections.values());
                return this;
            }
        }
    }

    /**
     * a db connection data object
     */
    public static class DbConnectionDef {
        protected String name;
        protected String type;
        protected String url;
        protected String user;
        protected String owner;

        public String getName() {
            return name;
        }

        public DbConnectionDef setName(String name) {
            this.name = name;
            return this;
        }

        public String getType() {
            return type;
        }

        public DbConnectionDef setType(String type) {
            this.type = type;
            return this;
        }

        public String getUrl() {
            return url;
        }

        public DbConnectionDef setUrl(String url) {
            this.url = url;
            return this;
        }

        public String getUser() {
            return user;
        }

        public DbConnectionDef setUser(String user) {
            this.user = user;
            return this;
        }

        public String getOwner() {
            return owner;
        }

        public DbConnectionDef setOwner(String owner) {
            this.owner = owner;
            return this;
        }
    }

}
