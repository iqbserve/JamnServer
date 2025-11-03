/* Authored by iqbserve.de */
package org.isa.ipc.sample;

import org.isa.ipc.JamnServer;
import org.isa.ipc.JamnServer.UncheckedJsonException;
import org.isa.ipc.JamnWebServiceProvider;
import org.isa.ipc.sample.web.api.SampleWebApiServices;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <pre>
 * A test app for starting a JamnServer with the sample Service API.
 * </pre>
 */
public class WebServiceProviderSampleApp {

    public static void main(String[] args) throws Exception {

        // create a Jamn server
        JamnServer lServer = new JamnServer();
        // enable all CORS - simplification for using Testfiles in a browser
        lServer.getConfig().setAllowAllCORSEnabled(true);

        // create the WebService provider
        JamnWebServiceProvider lWebServiceProvider = new JamnWebServiceProvider()
                // create a json tool wrapper for the JamnWebServiceProvider
                .setJsonTool(new JamnServer.JsonToolWrapper() {
                    private final ObjectMapper jack = new ObjectMapper()
                            .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
                            .setVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE);

                    @Override
                    public <T> T toObject(String pSrc, Class<T> pType) throws UncheckedJsonException {
                        try {
                            return jack.readValue(pSrc, pType);
                        } catch (JsonProcessingException e) {
                            throw new UncheckedJsonException(UncheckedJsonException.TOOBJ_ERROR, e);
                        }
                    }

                    @Override
                    public String toString(Object pObj) {
                        try {
                            return jack.writeValueAsString(pObj);
                        } catch (JsonProcessingException e) {
                            throw new UncheckedJsonException(UncheckedJsonException.TOJSON_ERROR, e);
                        }
                    }
                })
                // register the Web-API Services
                .registerServices(SampleWebApiServices.class);

        // add the provider to the server
        lServer.addContentProvider("WebServiceProvider", lWebServiceProvider);
        // start server
        lServer.start();
    }
}
