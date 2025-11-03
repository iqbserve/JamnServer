/* Authored by iqbserve.de */

package org.isa.ipc;

import org.isa.ipc.JamnServer.HttpHeader.FieldValue;
import org.isa.ipc.JamnServer.HttpHeader.Status;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.isa.ipc.JamnServer.JsonToolWrapper;
import org.isa.ipc.JamnServer.RequestMessage;
import org.isa.ipc.JamnServer.ResponseMessage;

/**
 * <pre>
 * The class realizes a simple Web Service Provider.
 *
 * Usage:
 *  ...
 *  
 *  // create a Jamn server
 *  JamnServer lServer = new JamnServer();
 * 
 *  // create the WebService provider
 *  JamnWebServiceProvider lWebServiceProvider = new JamnWebServiceProvider()
 *      // register the Web-API Services
 *      .registerServices(SampleWebApiServices.class);
 *
 *  // add the actual jamn-content-provider to the server
 *  lServer.addContentProvider("SampleServiceProvider", lWebServiceProvider);
 *  
 *  ...
 *
 *
 * By default the data IO is based on JSON which is provided by the "com.fasterxml.jackson" library.
 * But you can simply fallback to pure String IO or plug in any other JSON or String based format converter.
 *
 * lWebServiceProvider.setJsonTool(new JamnServer.JsonToolWrapper() {
 * 		private final MyConverterClass MyConverter = new MyConverterClass();
 * 			Override
 * 			public <T> T toObject(String pSrc, Class<T> pType) throws IOException {
 * 				return MyConverter.getAsObject(pSrc, pType);
 * 			}
 * 			Override
 * 			public String toString(Object pObj) throws IOException {
 * 				return MyConverter.getAsString(pObj);
 * 			}
 * 		});
 * </pre>
 */
public class JamnWebServiceProvider implements JamnServer.ContentProvider {

    protected static final String LS = System.lineSeparator();
    protected static Logger LOG = Logger.getLogger(JamnWebServiceProvider.class.getName());

    protected JsonToolWrapper jsonTool;
    protected String urlRoot = "";

    /**
     * A map holding all registered services.
     */
    protected Map<String, ServiceObject> serviceRegistry = new HashMap<>();

    /**
     */
    public JamnWebServiceProvider setJsonTool(JsonToolWrapper pTool) {
        jsonTool = pTool;
        return this;
    }

    /**
     */
    public JamnWebServiceProvider setUrlRoot(String pUrlRoot) {
        urlRoot = pUrlRoot;
        return this;
    }

    /**
     * <pre>
     * The public interface method to register and install Services implemented in pServices.
     * Where pServices may be a class or an instance of a class.
     * </pre>
     */
    public JamnWebServiceProvider registerServices(Object pServices)
            throws WebServiceDefinitionException {
        ServiceObject lServiceObj = null;
        WebService lServiceAnno = null;
        Object lInstance = null;
        Class<?> lServiceClass = null;
        Class<?> lRequestClass = null;
        Class<?> lReponseClass = null;

        if (pServices instanceof Class) {
            lServiceClass = (Class<?>) pServices;
            try {
                lInstance = lServiceClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new WebServiceDefinitionException(
                        String.format("Instantiation of WebService failed [%s]", lServiceClass), e);
            }
        } else {
            lInstance = pServices;
            lServiceClass = lInstance.getClass();
        }

        Method[] lMethodes = lServiceClass.getDeclaredMethods();
        for (Method serviceMethod : lMethodes) {
            if (serviceMethod.isAnnotationPresent(WebService.class)) {
                lServiceAnno = serviceMethod.getDeclaredAnnotation(WebService.class);
                checkServiceAnnotation(lServiceAnno, serviceMethod);

                lRequestClass = getServiceRequestClassFrom(serviceMethod);
                lReponseClass = getServiceResponseClassFrom(serviceMethod);

                lServiceObj = new ServiceObject(lServiceAnno, lInstance, lRequestClass, lReponseClass, serviceMethod,
                        () -> jsonTool);

                if (!urlRoot.isEmpty()) {
                    lServiceObj.path = new StringBuilder(urlRoot).append(lServiceObj.path).toString();
                }

                if (!serviceRegistry.containsKey(lServiceObj.path)) {
                    serviceRegistry.put(lServiceObj.path, lServiceObj);
                    final String info = String.format("WebService installed [%s] at [%s]",
                            lServiceObj.getName(),
                            lServiceObj.path);
                    LOG.fine(info);
                } else {
                    throw new WebServiceDefinitionException(
                            String.format("WebService Path of [%s] already defined for [%s]", lServiceObj.getName(),
                                    serviceRegistry.get(lServiceObj.path).getName()));
                }
            }
        }
        return this;
    }

    /**
     */
    public boolean isServicePath(String pPath) {
        return serviceRegistry.containsKey(pPath);
    }

    /**
     */
    public List<String> getAllServicePathNames() {
        List<String> lNames = new ArrayList<>(serviceRegistry.keySet());
        Collections.sort(lNames);
        return lNames;
    }

    /**
     * Calling a WebService internally from java bypassing the http layer.
     */
    public String doDirectCall(String pPath, String pRequestBody) throws WebServiceException {
        ServiceObject lService = null;
        if (serviceRegistry.containsKey(pPath)) {
            try {
                lService = serviceRegistry.get(pPath);
                Object lResult = lService.callWith(pRequestBody);
                if (lResult instanceof String result) {
                    return result;
                }
            } catch (Exception e) {
                throw new WebServiceException("WebService direct call failure", e);
            }
        } else {
            throw new WebServiceException(String.format("WebService direct call to unknown [%s]", pPath));
        }
        return "";
    }

    /*********************************************************
     * The public Annotation Interfaces to annotate methods as WebServices.
     *********************************************************/
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface WebService {
        public String path() default "/";

        public String[] methods() default { "GET, POST" };

        public String contentType() default FieldValue.APPLICATION_JSON;
    }

    /*********************************************************
     * Internal static helper methods.
     *********************************************************/
    /**
     */
    protected static String getStackTraceFrom(Throwable t) {
        return JamnServer.getStackTraceFrom(t);
    }

    /**
     */
    protected static String getServiceMethodName(Method pMeth) {
        return pMeth.getDeclaringClass().getSimpleName() + " - " + pMeth.getName();
    }

    /**
     */
    protected static void checkServiceAnnotation(WebService pServiceAnno, Method pMeth)
            throws WebServiceDefinitionException {
        if (pServiceAnno.path().isEmpty()) {
            throw new WebServiceDefinitionException(
                    String.format("No WebService path attribute found for [%s]", getServiceMethodName(pMeth)));
        }
        if (pServiceAnno.methods().length == 0) {
            throw new WebServiceDefinitionException(
                    String.format("No WebService methods attribute found for [%s]", getServiceMethodName(pMeth)));
        }
    }

    /**
     */
    protected static Class<?> getServiceRequestClassFrom(Method pMeth) throws WebServiceDefinitionException {
        Class<?>[] lClasses = pMeth.getParameterTypes();
        if (lClasses.length == 1) {
            return lClasses[0];
        } else if (lClasses.length > 1) {
            throw new WebServiceDefinitionException(
                    String.format("WebService method must declare 0 or 1 parameter [%s]", getServiceMethodName(pMeth)));
        }
        return null;
    }

    /**
     */
    protected static Class<?> getServiceResponseClassFrom(Method pMeth) {
        return pMeth.getReturnType();
    }

    /*********************************************************
     * The internal classes for loading and providing WebService objects.
     *********************************************************/
    /**
     * The internal class that holds a Service Instance.
     */
    protected static class ServiceObject {
        protected Object instance = null;
        protected String path = "";
        protected String contentType = "";
        protected Map<String, String> httpMethods = new HashMap<>(4);
        protected Class<?> requestClass = null;
        protected Class<?> responseClass = null;
        protected Method serviceMethod = null;

        protected Supplier<JsonToolWrapper> json;

        protected ServiceObject(WebService pServiceAnno, Object pInstance, Class<?> pRequestClass,
                Class<?> pResponseClass, Method pServiceMethod, Supplier<JsonToolWrapper> pJson) {
            json = pJson;
            instance = pInstance;
            path = pServiceAnno.path().trim();
            contentType = pServiceAnno.contentType().trim();
            requestClass = pRequestClass;
            responseClass = pResponseClass;
            serviceMethod = pServiceMethod;

            for (String meth : pServiceAnno.methods()) {
                httpMethods.put(meth.toUpperCase(), meth.toUpperCase());
            }
        }

        /**
         */
        @Override
        public String toString() {
            return getName();
        }

        /**
         */
        public String getName() {
            return getServiceMethodName(serviceMethod);
        }

        /**
         */
        public Class<?> getServiceClass() {
            return instance.getClass();
        }

        /**
         */
        public boolean isMethodSupported(String pMethod) {
            return httpMethods.containsKey(pMethod.toUpperCase());
        }

        /**
         */
        public boolean isContentTypeSupported(String pContentType) {
            return (contentType.equalsIgnoreCase(pContentType) || pContentType.isEmpty());
        }

        /**
         */
        public String getContentType() {
            return contentType;
        }

        /**
         */
        public boolean hasParameter() {
            return (requestClass != null);
        }

        /**
         * @throws InvocationTargetException
         * @throws IllegalArgumentException
         * @throws IllegalAccessException
         */
        protected Object callWith(String pRequestData)
                throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            Object lRet = null;
            Object lParam = null;

            if (getContentType().equalsIgnoreCase(FieldValue.APPLICATION_JSON)) {
                if (hasParameter()) {
                    lParam = json.get().toObject(pRequestData, requestClass);
                    lRet = serviceMethod.invoke(instance, lParam);
                } else {
                    lRet = serviceMethod.invoke(instance);
                }
                lRet = json.get().toString(lRet);
            } else if (getContentType().equalsIgnoreCase(FieldValue.TEXT_PLAIN)) {
                if (hasParameter() && requestClass == String.class) {
                    lRet = serviceMethod.invoke(instance, pRequestData);
                } else {
                    lRet = serviceMethod.invoke(instance);
                }
                if (responseClass == String.class) {
                    // if string defined return directly
                    return lRet;
                }
                // this surrounds a blank string with ""
                lRet = json.get().toString(lRet);
            }

            return lRet;
        }
    }

    /**
     * Exceptions thrown during Service execution.
     */
    protected static class WebServiceException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String httpStatus;

        WebServiceException(String pHttpStatus, String pMsg) {
            super(pMsg);
            httpStatus = pHttpStatus;
        }

        WebServiceException(String pMsg, Throwable pCause) {
            super(pMsg, pCause);
            httpStatus = "direct call";
        }

        WebServiceException(String pMsg) {
            super(pMsg);
            httpStatus = "direct call";
        }

        public String getHttpStatus() {
            return httpStatus;
        }
    }

    /*********************************************************
     * The public classes.
     *********************************************************/
    /**
     * Exceptions thrown during Service initialization/creation.
     */
    public static class WebServiceDefinitionException extends Exception {
        private static final long serialVersionUID = 1L;

        public WebServiceDefinitionException(String pMsg) {
            super(pMsg);
        }

        public WebServiceDefinitionException(String pMsg, Throwable pCause) {
            super(pMsg, pCause);
        }
    }

    /*********************************************************
     * The actual JamnServer.ContentProvider Interface Implementation.
     *********************************************************/
    @Override
    public void handleContentProcessing(RequestMessage pRequest, ResponseMessage pResponse) {

        ServiceObject lService = null;
        Object lResult = null;
        byte[] lData = null;

        try {
            if (pRequest.isMethod("GET") || pRequest.isMethod("POST")) {
                lService = getServiceInstanceFor(pRequest.getPath(), pRequest.getMethod(),
                        pRequest.getContentType());

                lResult = lService.callWith(pRequest.body());

                if (lResult instanceof String result) {
                    lData = result.getBytes();
                    pResponse.setContentType(lService.getContentType());
                    pResponse.writeToContent(lData);
                } else {
                    throw new WebServiceException(Status.SC_500_INTERNAL_ERROR,
                            String.format("Unsupported WebService API Return Type [%s] [%s]", lResult.getClass(),
                                    lService.getName()));
                }
                pResponse.setStatus(Status.SC_200_OK);
            } else if (pRequest.isMethod("OPTIONS")) {
                pResponse.setStatus(Status.SC_204_NO_CONTENT);
            } else {
                pResponse.setStatus(Status.SC_405_METHOD_NOT_ALLOWED);
            }
        } catch (WebServiceException wse) {
            LOG.fine(() -> String.format("WebService API Error: [%s]", wse.getMessage()));
            pResponse.setStatus(wse.getHttpStatus());
        } catch (Exception e) {
            String info = lService != null ? lService.getName() : "";
            info = info + LS + getStackTraceFrom(e);
            LOG.severe(
                    String.format("WebService Request Handling internal/runtime ERROR: %s %s %s", e.toString(), LS,
                            info));
            pResponse.setStatus(Status.SC_500_INTERNAL_ERROR);
        }
    }

    /**
     */
    protected ServiceObject getServiceInstanceFor(String pPath, String pMethod, String pContentType)
            throws WebServiceException {
        ServiceObject lService = null;
        if (serviceRegistry.containsKey(pPath)) {
            lService = serviceRegistry.get(pPath);

            if (!lService.isMethodSupported(pMethod)) {
                throw new WebServiceException(Status.SC_405_METHOD_NOT_ALLOWED,
                        String.format("Unsupported WebService Method [%s] [%s]", pMethod, lService.getName()));
            }
            if (!lService.isContentTypeSupported(pContentType)) {
                throw new WebServiceException(Status.SC_400_BAD_REQUEST, String
                        .format("Unsupported WebService ContentType [%s] [%s]", pContentType, lService.getName()));
            }
        } else {
            throw new WebServiceException(Status.SC_404_NOT_FOUND, String.format("Unsupported WebService Path [%s]", pPath));
        }
        return lService;
    }
}
