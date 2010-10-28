package org.jolokia.http;

import org.jolokia.*;
import org.jolokia.backend.BackendManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.management.*;
import java.io.*;
import java.util.List;
import java.util.Map;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * Request handler with no dependency on the servlet API so that it can be used in
 * several different environments (like for the Sun JDK 6 {@link com.sun.net.httpserver.HttpServer}.
 *
 * @author roland
 * @since Mar 3, 2010
 */
public class HttpRequestHandler {

    // handler for contacting the MBean server(s)
    private BackendManager backendManager;

    // Logging abstraction
    private LogHandler logHandler;

    /**
     * Request handler for parsing HTTP request and dispatching to the appropriate
     * request handler (with help of the backend manager)
     *
     * @param pBackendManager backend manager to user
     * @param pLogHandler log handler to where to put out logging
     */
    public HttpRequestHandler(BackendManager pBackendManager, LogHandler pLogHandler) {
        backendManager = pBackendManager;
        logHandler = pLogHandler;
    }

    /**
     * Handle a GET request
     *
     * @param pUri URI leading to this request
     * @param pPathInfo path of the request
     * @param pParameterMap parameters of the GET request  @return the response
     * @return JSON answer
     */
    public JSONAware handleGetRequest(String pUri, String pPathInfo, Map<String, String[]> pParameterMap) {
        JmxRequest jmxReq =
                JmxRequestFactory.createGetRequest(pPathInfo,pParameterMap);

        if (backendManager.isDebug() && !"debugInfo".equals(jmxReq.getOperation())) {
            logHandler.debug("URI: " + pUri);
            logHandler.debug("Path-Info: " + pPathInfo);
            logHandler.debug("Request: " + jmxReq.toString());
        }

        // Call handler and retrieve return value
        return backendManager.executeRequest(jmxReq);
    }

    /**
     * Handle the input stream as given by a POST request
     *
     * @param pUri URI leading to this request
     * @param pInputStream input stream of the post request
     * @param pEncoding optional encoding for the stream. If null, the default encoding is used
     * @return the JSON object containing the json results for one or more {@link JmxRequest} contained
     *         within the answer.
     *
     * @throws MalformedObjectNameException if one or more request contain an invalid MBean name
     * @throws IOException if reading from the input stream fails
     */
    public JSONAware handlePostRequest(String pUri,InputStream pInputStream, String pEncoding)
            throws MalformedObjectNameException, IOException {
        if (backendManager.isDebug()) {
            logHandler.debug("URI: " + pUri);
        }

        JSONAware jsonRequest = extractJsonRequest(pInputStream,pEncoding);
        if (jsonRequest instanceof List) {
            List<JmxRequest> jmxRequests = JmxRequestFactory.createPostRequests((List) jsonRequest);
            return backendManager.executeRequests(jmxRequests);
        } else if (jsonRequest instanceof Map) {
            JmxRequest jmxReq = JmxRequestFactory.createPostRequest((Map<String, ?>) jsonRequest);
            // Call handler and retrieve return value
            return backendManager.executeRequest(jmxReq);
        } else {
            throw new IllegalArgumentException("Invalid JSON Request " + jsonRequest.toJSONString());
        }
    }

    private JSONAware extractJsonRequest(InputStream pInputStream, String pEncoding) throws IOException {
        InputStreamReader reader = null;
        try {
            reader =
                    pEncoding != null ?
                            new InputStreamReader(pInputStream, pEncoding) :
                            new InputStreamReader(pInputStream);
            JSONParser parser = new JSONParser();
            return (JSONAware) parser.parse(reader);
        } catch (ParseException exp) {
            throw new IllegalArgumentException("Invalid JSON request " + reader,exp);
        }
    }

    /**
     * Utility method for handling single runtime exceptions and errors.
     *
     * @param pThrowable exception to handle
     * @return its JSON representation
     */
    public JSONObject handleThrowable(Throwable pThrowable) {
        return backendManager.handleThrowable(pThrowable);
    }



    /**
     * Check whether the given host and/or address is allowed to access this agent.
     *
     * @param pHost host to check
     * @param pAddress address to check
     */
    public void checkClientIPAccess(String pHost, String pAddress) {
        if (!backendManager.isRemoteAccessAllowed(pHost,pAddress)) {
            throw new SecurityException("No access from client " + pAddress + " allowed");
        }
    }

    /**
     * Extract the the result code for a JSON answer. If multiple responses are contained,
     * the result code is the highest code found within the list of responses
     *
     * @param pJson response object
     * @return the result code
     */
    public int extractResultCode(JSONAware pJson) {
        if (pJson instanceof List) {
            int maxCode = 0;
            for (JSONAware j : (List<JSONAware>) pJson) {
                int code = extractStatus(j);
                if (code > maxCode) {
                    maxCode = code;
                }
            }
            return maxCode;
        } else {
            return extractStatus(pJson);
        }
    }

    // Extract status from a json object
    private int extractStatus(JSONAware pJson) {
        if (pJson instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) pJson;
            if (!jsonObject.containsKey("status")) {
                throw new IllegalStateException("No status given in response " + pJson);
            }
            return (Integer) jsonObject.get("status");
        } else {
            throw new IllegalStateException("Internal: Not a JSONObject but a " + pJson.getClass() + " " + pJson);
        }
    }

}
