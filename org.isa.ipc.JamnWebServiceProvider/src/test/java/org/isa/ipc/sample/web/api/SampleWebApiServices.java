/*Authored by iqbserve.de*/

package org.isa.ipc.sample.web.api;

import static org.isa.ipc.JamnServer.HttpHeader.FieldValue.APPLICATION_JSON;
import static org.isa.ipc.JamnServer.HttpHeader.FieldValue.TEXT_PLAIN;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.isa.ipc.JamnWebServiceProvider.WebService;

/**
 * <pre>
 * A minimal WebService example.
 * Since this JamnWebService-Provider implementation does NOT strictly follow the REST paradigm
 * the get and post methods are used like:
 * - GET = NO parameter but accessible just by url even from browser 
 * - POST = parameter supported but accessible only from a "fetch" like function
 * 
 * NOTE: 
 * For simplicity, the used data structures have public fields and NO getter/setter methods.
 * </pre>
 */
public class SampleWebApiServices {

    public static final String PATHBASE_API = "/api";
    public static final String PATHBASE_API_SERVER = "/api/server";

    /***************************************************************************************
     * WebService - About
     **************************************************************************************/
    /**
     * <pre>
     * WebService - About
     * NO arguments but return type - accessible via get + post
     * http://localhost:8099/api/about
     * </pre>
     */
    @WebService(path = PATHBASE_API + "/about", methods = { "GET", "POST" }, contentType = APPLICATION_JSON)
    public AboutResponse sendAboutInfos() {
        AboutResponse lResponse = new AboutResponse();

        lResponse.name = "JamnServer Web Service API";
        lResponse.version = "0.0.1";
        lResponse.descr = "Just Another Micro Node Server Web API";

        return lResponse;
    }

    /**
     */
    public static class AboutResponse {
        public String name = "";
        public String version = "";
        public String descr = "";
    }

    /***************************************************************************************
     * WebService - Echo
     **************************************************************************************/
    /**
     * <pre>
     * WebService - Echo
     * with native String argument and return type - accessible only via post
     * http://localhost:8099/api/echo
     * </pre>
     */
    @WebService(path = PATHBASE_API + "/echo", methods = { "POST" }, contentType = TEXT_PLAIN)
    public String sendEcho(String pRequest) {
        return "ECHO: " + pRequest;
    }

    /***************************************************************************************
     * WebService - Get detailed Server Info
     **************************************************************************************/
    /**
     * <pre>
     * WebService - Get detailed Server Info
     * with argument and return type - accessible only via post  
     * Request example: {"subjects":["name","version","provider"]}
     * http://localhost:8099/api/server/get-details
     * </pre>
     */
    @WebService(path = PATHBASE_API_SERVER + "/get-details", methods = { "POST" }, contentType = APPLICATION_JSON)
    public DetailsResponse sendDetailsFor(DetailsRequest pRequest) {
        DetailsResponse lResponse = new DetailsResponse();

        for (String detail : pRequest.getSubjects()) {
            if ("name".equalsIgnoreCase(detail)) {
                lResponse.putSubject(detail, "JamnServer");
            } else if ("version".equalsIgnoreCase(detail)) {
                lResponse.putSubject(detail, "0.0.1");
            } else if ("provider".equalsIgnoreCase(detail)) {
                lResponse.putSubject(detail, "JamnWebServiceProvider");
            }
        }

        return lResponse;
    }

    /**
     */
    public static class DetailsRequest {
        public List<String> subjects = new ArrayList<>();

        List<String> getSubjects() {
            return subjects;
        }
    }

    /**
     */
    public static class DetailsResponse {
        public Map<String, String> details = new LinkedHashMap<>();

        public void putSubject(String pSubject, String pValue) {
            details.put(pSubject, pValue);
        }
    }

    /***************************************************************************************
     **************************************************************************************/
}
