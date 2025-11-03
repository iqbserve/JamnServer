/* Authored by iqbserve.de */

package org.isa.jps.comp;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.isa.jps.JamnPersonalServerApp;
import org.isa.jps.JamnPersonalServerApp.CommonHelper;

/**
 * <pre>
 * </pre>
 */
public class DocumentStoreAdapter {

    protected static Logger LOG = Logger.getLogger(DocumentStoreAdapter.class.getName());

    protected static final String SYSTEM_STORE = "system.store";
    protected static final String EXTENSION_STORE = "extension.store";

    protected static Map<String, DocumentStore> stores = Collections.synchronizedMap(new HashMap<>());
    protected static final JamnPersonalServerApp App = JamnPersonalServerApp.getInstance();
    protected static final CommonHelper Tool = JamnPersonalServerApp.Tool;

    /**
     */
    public static synchronized DocumentStore getSystemStore() {
        return getStore(SYSTEM_STORE);
    }

    /**
     */
    public static synchronized DocumentStore getExtensionStore() {
        return getStore(EXTENSION_STORE);
    }

    /**
     */
    protected static synchronized DocumentStore getStore(String pName) {
        if (!stores.containsKey(pName)) {
            createStore(pName);
        }
        return stores.get(pName);
    }

    /**
     */
    protected static synchronized DocumentStore createStore(String pName) {
        DocumentStore lStore = null;
        Path lRootPath = null;
        Charset encoding = Charset.forName(App.getConfig().getStandardEncoding());
        try {
            if (SYSTEM_STORE.equalsIgnoreCase(pName)) {
                lRootPath = Tool.ensureSubDir(App.getConfig().getDataRoot(), App.getHomePath());
                lStore = new DefaultDocumentStore(SYSTEM_STORE, lRootPath, encoding);
            } else if (EXTENSION_STORE.equalsIgnoreCase(pName) && App.getConfig().isExtensionsEnabled()) {
                lRootPath = Tool.ensureSubDir(App.getConfig().getExtensionRoot(), App.getHomePath());
                lRootPath = Tool.ensureSubDir(App.getConfig().getExtensionData(), lRootPath);
                lStore = new DefaultDocumentStore(EXTENSION_STORE, lRootPath, encoding);
            }
            stores.put(lStore.getName(), lStore);
        } catch (Exception e) {
            throw new UncheckedDocumentStoreException("Creating of Document Store failed", e);
        }
        return lStore;
    }

    /**
     */
    private DocumentStoreAdapter() {
    }

    /**
     */
    public static interface DocumentStore {

        public String getName();

        /**
         */
        public PersistentDocument find(String pDocName) throws IOException;

        /**
         */
        public void save(PersistentDocument pDoc)throws IOException;

    }

    /**
     */
    public static class PersistentDocument {
        protected String name = "";
        protected String content = "";

        public PersistentDocument(String pName, String pData) {
            this.name = pName;
            this.content = pData;
        }

        public PersistentDocument(String pName) {
            this.name = pName;
        }

        public String name() {
            return name;
        }

        /**
         */
        public String getContent() {
            return content;
        }

        /**
         */
        public void setContent(String pData) {
            content = pData;
        }

    }

    /**
     */
    public static class DocStoreQuery {
        protected String name = "";

        public DocStoreQuery(String pDocName){
            name = pDocName;

        }
        public String getDocName(){
            return name;
        }
    }

    /**
     */
    protected static class DefaultDocumentStore implements DocumentStore {
        protected static final String DATA_FILE_EXT = ".store.data";
        protected String name;
        protected Properties config = new Properties();
        protected Path dataRoot;

        protected Charset encoding = StandardCharsets.UTF_8;

        /**
         */
        protected DefaultDocumentStore(String pName, Path pRootPath, Charset pEncoding) {
            name = pName;
            dataRoot = pRootPath;
            encoding = pEncoding;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public PersistentDocument find(String pDocName) throws IOException{
            DocStoreQuery lQuery = new DocStoreQuery(pDocName);
            PersistentDocument lResult = null;
            lResult = readDocument(lQuery);
            return lResult;
        }

        @Override
        public void save(PersistentDocument pDoc) throws IOException{
            saveDocument(pDoc);
        }

        /**
         */
        protected PersistentDocument readDocument(DocStoreQuery pQuery) throws IOException{
            Path dataFile = getDataFilePathFor(pQuery.getDocName());
            ensureDataContainer(dataFile);
            String data = new String(Files.readAllBytes(dataFile), encoding);
            return new PersistentDocument(pQuery.getDocName(), data);
        }

        /**
         */
        protected void saveDocument(PersistentDocument pDoc) throws IOException{
            Path dataFile = getDataFilePathFor(pDoc.name());
            Files.writeString(dataFile, pDoc.getContent(), encoding, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        /**
         */
        protected Path getDataFilePathFor(String pName) {
            return Paths.get(dataRoot.toString(), String.join("", pName, DATA_FILE_EXT));
        }

        /**
         */
        protected void ensureDataContainer(Path pDataFile) throws IOException{
            if (!Files.exists(pDataFile)) {
                Files.writeString(pDataFile, "", encoding, StandardOpenOption.CREATE);
                LOG.info(() -> String.format("Datastore file created [%s]", pDataFile));
            }
        }
    }

    /**
     */
    public static class DocumentStoreException extends IOException {
        private static final long serialVersionUID = 1L;

        public DocumentStoreException(String pMsg) {
            super(pMsg);
        }

        public DocumentStoreException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

    /**
     */
    public static class UncheckedDocumentStoreException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedDocumentStoreException(String pMsg) {
            super(pMsg);
        }

        public UncheckedDocumentStoreException(String pMsg, Exception pCause) {
            super(pMsg, pCause);
        }
    }

}