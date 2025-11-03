/* Authored by iqbserve.de */
package org.isa.ipc;

import org.isa.ipc.JamnServer.HttpHeader.FieldValue;
import org.isa.ipc.JamnServer.HttpHeader.Status;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.Config;
import org.isa.ipc.JamnServer.ExprString;
import org.isa.ipc.JamnServer.ExprString.ValueProvider;
import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.ipc.JamnServer.RequestMessage;
import org.isa.ipc.JamnServer.ResponseMessage;

/**
 * <pre>
 * The class realizes a simple Web Content Provider.
 * Which is essentially a rudimentary web server 
 * with the ability to create dynamic content (see SampleWebContentApp).
 * </pre>
 */
public class JamnWebContentProvider implements JamnServer.ContentProvider {

    protected static final String LS = System.lineSeparator();
    protected static Logger LOG = Logger.getLogger(JamnWebContentProvider.class.getName());
    // development.mode - disables e.g. caching if true
    protected static boolean DvlpMode = true;

    protected JsonToolWrapper jsonTool;

    protected Config config = new Config();
    protected String webroot;

    // customizable file helper functions
    protected FileHelper fileHelper = new FileHelper();
    protected Predicate<WebFile> cacheableChecker = file -> true;

    /**
     * <pre>
     * The content provider supports two interfaces to customize content.
     * A fileProvider and a fileEnricher. 
     * 
     * The fileProvider provides the actual byte content of what is called a WebFile.
     * This can be real files from an underlying filesystem (default)
     * or anything else a user wants to be associated with a resource name.
     * </pre>
     */
    protected FileProvider fileProvider = (WebFile pFile) -> pFile
            .setData(Files.readAllBytes(Paths.get(pFile.filePath)));

    /**
     * <pre>
     * A fileEnricher is used by the fileProvider
     * to modify the file/resource content before provision.
     * By default files can be marked as templates with placeholders like ${name} that become resolved.
     * </pre>
     */
    protected FileEnricher fileEnricher = (WebFile pFile) -> {
        //see DefaultFileEnricher
    };

    /**
     * A rudimentary file cache. 
     */
    protected FileCache fileCache = new FileCache() {
        private Map<String, WebFile> cacheMap = Collections.synchronizedMap(new HashMap<>());

        @Override
        public synchronized void put(String pKey, WebFile pFile) {
            cacheMap.put(pKey, pFile);
        }

        @Override
        public boolean contains(String pKey) {
            return cacheMap.containsKey(pKey);
        }

        @Override
        public WebFile get(String pKey) {
            return cacheMap.get(pKey);
        }
    };

    protected JamnWebContentProvider() {
    }

    public JamnWebContentProvider(String pRoot) {
        this();
        webroot = pRoot;
    }

    /**
     */
    public JamnWebContentProvider setJsonTool(JsonToolWrapper pTool) {
        jsonTool = pTool;
        return this;
    }

    /**
     */
    public JamnWebContentProvider setConfig(Config pConfig) {
        config = pConfig;
        return this;
    }

    /**
     */
    public JamnWebContentProvider setFileEnricher(FileEnricher pFileEnricher) {
        this.fileEnricher = pFileEnricher;
        return this;
    }

    /**
     */
    public JamnWebContentProvider setFileCache(FileCache fileCache) {
        this.fileCache = fileCache;
        return this;
    }

    /**
     */
    public JamnWebContentProvider setCacheableChecker(Predicate<WebFile> cacheableChecker) {
        this.cacheableChecker = cacheableChecker;
        return this;
    }

    /**
    */
    public JamnWebContentProvider setFileProvider(FileProvider fileProvider) {
        this.fileProvider = fileProvider;
        return this;
    }

    /**
     */
    public JamnWebContentProvider setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
        return this;
    }

    /**
     */
    public FileHelper getFileHelper() {
        return fileHelper;
    }

    /**
     * JamnServer.ContentProvider Interface method.
     */
    @Override
    public void handleContentProcessing(RequestMessage pRequest, ResponseMessage pResponse) {

        // be gently by default
        pResponse.setStatus(Status.SC_200_OK);

        WebFile lContent;
        try {
            if (pRequest.isMethod("GET")) {
                lContent = getFileContent(pRequest.getPath(), pResponse);

                if (!lContent.isEmpty()) {
                    pResponse.writeToContent(lContent.getData());
                } else {
                    pResponse.setStatus(Status.SC_204_NO_CONTENT);
                }
            } else {
                LOG.warning(() -> String.format("WebContentProvider Warning: Unsupported HTTP Method [%s]",
                        pRequest.getMethod()));
            }
        } catch (WebContentException ce) {
            LOG.fine(() -> String.format("WebContentProvider Error: [%s]%s%s", ce.getMessage(), LS,
                    getStackTraceFrom(ce)));
            pResponse.setStatus(ce.getHttpStatus());
        } catch (Exception e) {
            LOG.severe(
                    String.format("WebContentProvider internal Error GET [%s]%s%s%s%s", pRequest.getPath(), LS, e, LS,
                            getStackTraceFrom(e)));
            pResponse.setStatus(Status.SC_500_INTERNAL_ERROR);
        }
    }

    /**
     */
    protected WebFile getFileContent(String pRequestPath, ResponseMessage pResponse)
            throws WebContentException {
        String lDecodedPath = fileHelper.decodeRequestPath(pRequestPath);

        // the decoded path gets the unique id/requestPath of the requested file
        WebFile lWebFile = new WebFile(lDecodedPath);

        if (!DvlpMode && fileCache.contains(lWebFile.getId())) {
            lWebFile = fileCache.get(lWebFile.getId());
            pResponse.setContentType(lWebFile.getContentType());
            return lWebFile;
        }

        lWebFile.filePath = getFilePathFor(fileHelper.doPathMapping(lDecodedPath));

        try {
            // by default html is assumed
            pResponse.setContentType(FieldValue.TEXT_HTML);

            if (fileHelper.isStyleSheet(lDecodedPath)) {
                pResponse.setContentType(FieldValue.TEXT_CSS);
            } else if (fileHelper.isJavaScript(lDecodedPath)) {
                pResponse.setContentType(FieldValue.TEXT_JS);
            } else if (fileHelper.isImage(lDecodedPath)) {
                pResponse.setContentType(fileHelper.getImageTypeFrom(lWebFile.filePath));
                lWebFile.setTextFormat(false);
            }

            lWebFile.setContentType(pResponse.getContentType());
            fileProvider.readAllFileBytes(lWebFile);
            fileEnricher.enrich(lWebFile);
            if(cacheableChecker.test(lWebFile)){
                fileCache.put(lWebFile.requestPath, lWebFile);
            }

        } catch (Exception e) {
            throw new WebContentException(Status.SC_404_NOT_FOUND,
                    String.format("Could NOT read file data [%s]", lWebFile.filePath), e);
        }

        return lWebFile;
    }

    /**
     */
    protected String getFilePathFor(String pRequestPath) {
        return new StringBuilder(webroot).append(pRequestPath).toString();
    }

    /*********************************************************
     * Provider classes and interfaces.
     *********************************************************/

    /**
     * A file provider delivers the concrete file content as a byte array. This
     * might be a filesystem file, or a database record or what ever.
     */
    public static interface FileProvider {
        void readAllFileBytes(WebFile pWebFile) throws IOException;
    }

    /**
     * A file enricher is used to pre process loaded web files to dynamically inject
     * values, text or code.
     */
    public static interface FileEnricher {
        void enrich(WebFile pFile);
    }

    /**
     * File cache abstraction.
     */
    public static interface FileCache {
        void put(String pKey, WebFile pFile);

        boolean contains(String pKey);

        WebFile get(String pKey);
    }

    /**
     * <pre>
     * A set of customizable public File helper methods
     * which can be overwritten individually
     * </pre>
     */
    public static class FileHelper {

        /**
         */
        public String doPathMapping(String pPath) {
            if (pPath.equals("/") || pPath.equals("/index") || pPath.equals("/index.html")
                    || pPath.equals("/index.htm")) {
                return "/index.html";
            }
            return pPath;
        }

        /**
         */
        public String getImageTypeFrom(String pPath) {
            String lType = "";
            if (pPath.endsWith("/favicon.ico")) {
                lType = FieldValue.IMAGE_X_ICON;
            }else if(pPath.endsWith(".svg")){
                lType = FieldValue.IMAGE_SVG_XML;
            }else{
                lType = FieldValue.IMAGE + pPath.substring(pPath.lastIndexOf(".") + 1, pPath.length());
            }
            return lType;
        }

        /**
         */
        public String decodeRequestPath(String pPath) {
            return pPath;
        }

        /**
         */
        public boolean isStyleSheet(String pPath) {
            return pPath.endsWith(".css");
        }

        /**
         */
        public boolean isJavaScript(String pPath) {
            return pPath.endsWith(".js") || pPath.endsWith(".mjs");
        }

        /**
         */
        public boolean isImage(String pPath) {
            return pPath.endsWith(".png") || pPath.endsWith(".jpg")
                    || pPath.endsWith(".gif")
                    || pPath.endsWith(".ico")
                    || pPath.endsWith(".svg");
        }
    }

    /**
     */
    public static class WebFile {
        protected String requestPath = "";
        protected String filePath = "";
        protected String contentType = "";
        protected byte[] data = new byte[0];
        protected boolean isTextFormat = true;

        public WebFile(String pPath) {
            requestPath = pPath;
        }

        public String getId() {
            return requestPath;
        }

        public boolean isEmpty() {
            return data.length == 0;
        }

        public String toString() {
            return requestPath;
        }

        public String getRequestPath() {
            return requestPath;
        }

        public void setRequestPath(String requestPath) {
            this.requestPath = requestPath;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        public boolean isTextFormat() {
            return isTextFormat;
        }

        public void setTextFormat(boolean isTextFormat) {
            this.isTextFormat = isTextFormat;
        }
    }

    /**
     * Internal Exceptions thrown by this WebContentProvider
     */
    private static class WebContentException extends Exception {
        private static final long serialVersionUID = 1L;
        protected final String httpStatus;

        public WebContentException(String pHttpStatus, String pMsg, Throwable pCause) {
            super(pMsg, pCause);
            httpStatus = pHttpStatus;
        }

        public String getHttpStatus() {
            return httpStatus;
        }
    }

    /**
     * <pre>
     * The FileEnricher is the interface used by a FileProvider to preprocess requested files.
     * 
     * This Default enricher first looks for a TemplateMarker at the head/top of the file.
     * If such a marker is present an ExprString is used that calls a ValueProvider
     * for all expressions like ${valuekey}.
     * </pre>
     */
    public static class DefaultFileEnricher implements FileEnricher {

        // the marker is expected as a file type comment e.g. like
        // <!--jamn.web.template-->, /*jamn.web.template*/
        // where the comment characters are NOT searched or parsed
        protected static String TemplateMarker = "jamn.web.template";
        protected static int MarkLen = TemplateMarker.length() + 10;// marklen + a surcharge for comment chars
        protected static Charset Encoding = StandardCharsets.UTF_8;

        // the ValueProvider for expressions like "${valuekey}"
        protected ValueProvider valueProvider = null;

        protected DefaultFileEnricher() {
        }

        public DefaultFileEnricher(ValueProvider pProvider) {
            valueProvider = pProvider;
        }

        @Override
        public void enrich(WebFile pFile) {
            String lContent = "";
            // only process if file has text format and a TEMPLATE_MARKER
            if (pFile.isTextFormat() && hasTemplateMarker(pFile)) {
                lContent = new String(pFile.getData(), Encoding);
                lContent = ExprString.applyValues(lContent, valueProvider, pFile);
                pFile.setData(lContent.getBytes(Encoding));
            }
        }

        /**
         * Read the first MarkLen bytes of a file and ckeck for the template marker.
         */
        protected boolean hasTemplateMarker(WebFile pFile) {
            String lHead = "";
            byte[] lBuffer = new byte[MarkLen];
            if (pFile.getData() != null && pFile.getData().length > MarkLen) {
                System.arraycopy(pFile.getData(), 0, lBuffer, 0, MarkLen);
                lHead = new String(lBuffer, Encoding);
            }
            return lHead.contains(TemplateMarker);
        }
    }


    /*********************************************************
     *********************************************************/

    /*********************************************************
     * Internal static helper methods.
     *********************************************************/
    /**
     */
    protected static String getStackTraceFrom(Throwable t) {
        return JamnServer.getStackTraceFrom(t);
    }
}
