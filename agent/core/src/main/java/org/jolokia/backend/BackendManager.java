package org.jolokia.backend;

import org.jolokia.ConfigKey;
import org.jolokia.JmxRequest;
import org.jolokia.LogHandler;
import org.jolokia.config.DebugStore;
import org.jolokia.config.Restrictor;
import org.jolokia.config.RestrictorFactory;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.history.HistoryStore;
import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import javax.management.*;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jolokia.ConfigKey.*;

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
 * Backendmanager for dispatching to various backends based on a given
 * {@link org.jolokia.JmxRequest}
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class BackendManager {

    // Dispatches request to local MBeanServer
    private LocalRequestDispatcher localDispatcher;

    // Converter for converting various attribute object types
    // a JSON representation
    private ObjectToJsonConverter objectToJsonConverter;

    // Handling access restrictions
    private Restrictor restrictor;

    // History handler
    private HistoryStore historyStore;

    // Storage for storing debug information
    private DebugStore debugStore;

    // Loghandler for dispatching logs
    private LogHandler logHandler;

    // List of RequestDispatchers to consult
    private List<RequestDispatcher> requestDispatchers;

    // Template for executing JMX related actions, with proper error handling
    private ErrorHandlingTemplate jmxExecTemplate;
    private ErrorHandlingTemplate.Callback execRequestCallback;

    public BackendManager(Map<ConfigKey,String> pConfig, LogHandler pLogHandler) {

        try {
            // Central objects
            StringToObjectConverter stringToObjectConverter = new StringToObjectConverter();
            objectToJsonConverter = new ObjectToJsonConverter(stringToObjectConverter,pConfig);

            // Access restrictor
            restrictor = RestrictorFactory.buildRestrictor(pLogHandler);

            // Log handler for putting out debug
            logHandler = pLogHandler;

            // Create and remember request dispatchers
            requestDispatchers = createRequestDispatchers(DISPATCHER_CLASSES.getValue(pConfig),
                    objectToJsonConverter,stringToObjectConverter,restrictor);
            localDispatcher = new LocalRequestDispatcher(objectToJsonConverter,
                    stringToObjectConverter,
                    restrictor,pConfig.get(ConfigKey.MBEAN_QUALIFIER));
            requestDispatchers.add(localDispatcher);

            // Template for executing the real request, including proper error handling
            jmxExecTemplate = new ErrorHandlingTemplate();
            execRequestCallback = createJmxExecCallback();

            // Backendstore for remembering state
            initStores(pConfig);

            // MBean registration
            localDispatcher.registerJolokiaMBeans(historyStore, debugStore);
        } catch (OperationsException e) {
            error("Error registering internal MBean: " + e, e);
        }
    }


    // Construct configured dispatchers by reflection. Returns always
    // a list, an empty one if no request dispatcher should be created
    private List<RequestDispatcher> createRequestDispatchers(String pClasses,
                                                             ObjectToJsonConverter pObjectToJsonConverter,
                                                             StringToObjectConverter pStringToObjectConverter,
                                                             Restrictor pRestrictor) {
        List<RequestDispatcher> ret = new ArrayList<RequestDispatcher>();
        if (pClasses != null && pClasses.length() > 0) {
            String[] names = pClasses.split("\\s*,\\s*");
            for (String name : names) {
                ret.add(createDispatcher(name, pObjectToJsonConverter, pStringToObjectConverter, pRestrictor));
            }
        }
        return ret;
    }

    // Create a single dispatcher
    private RequestDispatcher createDispatcher(String pDispatcherClass, ObjectToJsonConverter pObjectToJsonConverter, StringToObjectConverter pStringToObjectConverter, Restrictor pRestrictor) {
        try {
            Class clazz = this.getClass().getClassLoader().loadClass(pDispatcherClass);
            Constructor constructor = clazz.getConstructor(ObjectToJsonConverter.class,
                                                           StringToObjectConverter.class,
                                                           Restrictor.class);
            return (RequestDispatcher)
                            constructor.newInstance(pObjectToJsonConverter,
                                                    pStringToObjectConverter,
                                                    pRestrictor);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't load class " + pDispatcherClass + ": " + e,e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + pDispatcherClass + " has invalid constructor: " + e,e);
        } catch (IllegalAccessException e) {
        throw new IllegalArgumentException("Constructor of " + pDispatcherClass + " couldn't be accessed: " + e,e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(pDispatcherClass + " couldn't be instantiated: " + e,e);
        }
    }


    /**
     * Execute multiple requests at once (bulk request). A sophisticated algorithm takes
     * care to collect requests and dispatch them to the appropriate dispatchers.
     *
     * @param jmxRequests
     * @return
     */
    public JSONAware executeRequests(List<JmxRequest> jmxRequests) {
        JSONArray responseList = new JSONArray();
        for (JmxRequest jmxReq : jmxRequests) {
            if (isDebug() && !"debugInfo".equals(jmxReq.getOperation())) {
                logHandler.debug("Request: " + jmxReq.toString());
            }
            // Call handler and retrieve response
            JSONObject resp = executeRequest(jmxReq);
            responseList.add(resp);
        }
        return responseList;
    }




    /**
     * Handle a single JMXRequest. The response status is set to 200 if the request
     * was successful
     *
     * @param pJmxReq request to perform
     * @return the already converted answer.
     */
    public JSONObject executeRequest(final JmxRequest pJmxReq) {
        return jmxExecTemplate.executeRequest(execRequestCallback,pJmxReq);
    }


    // Callback for executing a request. It's statelesse (no closure), so only
    // a singleton object needs to be created (in the constructor)
    private ErrorHandlingTemplate.Callback createJmxExecCallback() {
        return new ErrorHandlingTemplate.Callback() {
            public JSONObject execute(JmxRequest pJmxReq) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
                boolean debug = isDebug() && !"debugInfo".equals(pJmxReq.getOperation());

                long time = 0;
                if (debug) {
                    time = System.currentTimeMillis();
                }
                JSONObject json = callRequestDispatcher(pJmxReq);

                // Update global history store
                historyStore.updateAndAdd(pJmxReq, json);
                if (debug) {
                    debug("Execution time: " + (System.currentTimeMillis() - time) + " ms");
                    debug("Response: " + json);
                }
                // Ok, we did it and set the status if not already set (which can happen for a direkt proxy request)
                if (!json.containsKey("status")) {
                    json.put("status", 200 /* success */);
                }
                return json;
            }
        };
    }


    // call the an appropriate request dispatcher for a single request
    private JSONObject callRequestDispatcher(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        for (RequestDispatcher dispatcher : requestDispatchers) {
            if (dispatcher.canHandle(pJmxReq)) {
                return dispatcher.dispatchRequest(pJmxReq);
            }
        }
        throw new IllegalStateException("Internal error: No dispatcher found for handling " + pJmxReq);
    }


    public JSONObject handleThrowable(Throwable pThrowable) {
        return jmxExecTemplate.handleThrowable(pThrowable);
    }


    // init various application wide stores for handling history and debug output.
    private void initStores(Map<ConfigKey, String> pConfig)
            throws OperationsException {
        int maxEntries = getIntConfigValue(pConfig,HISTORY_MAX_ENTRIES);
        int maxDebugEntries = getIntConfigValue(pConfig,DEBUG_MAX_ENTRIES);

        String doDebug = DEBUG.getValue(pConfig);
        boolean debug = false;
        if (doDebug != null && Boolean.valueOf(doDebug)) {
            debug = true;
        }

        historyStore = new HistoryStore(maxEntries);
        debugStore = new DebugStore(maxDebugEntries,debug);
    }

    private int getIntConfigValue(Map<ConfigKey, String> pConfig, ConfigKey pKey) {
        int maxDebugEntries;
        try {
            maxDebugEntries = Integer.parseInt(pKey.getValue(pConfig));
        } catch (NumberFormatException exp) {
            maxDebugEntries = Integer.parseInt(pKey.getDefaultValue());
        }
        return maxDebugEntries;
    }

    // Remove MBeans again.
    public void destroy() {
        try {
            localDispatcher.unregisterJolokiaMBeans();
        } catch (JMException e) {
            error("Cannot unregister MBean: " + e,e);
        }
    }

    public boolean isRemoteAccessAllowed(String pRemoteHost, String pRemoteAddr) {
        return restrictor.isRemoteAccessAllowed(pRemoteHost,pRemoteAddr);
    }

    public void info(String msg) {
        logHandler.info(msg);
        if (debugStore != null) {
            debugStore.log(msg);
        }
    }

    public void debug(String msg) {
        logHandler.debug(msg);
        if (debugStore != null) {
            debugStore.log(msg);
        }
    }

    public final void error(String message, Throwable t) {
        logHandler.error(message,t);
        if (debugStore != null) {
            debugStore.log(message, t);
        }
    }

    public boolean isDebug() {
        return debugStore != null && debugStore.isDebug();
    }

    /**
     * Set the log handler used for log handling
     *
     * @param pLogHandler log handler to use
     */
    public void setLogHandler(LogHandler pLogHandler) {
        logHandler = pLogHandler;
    }


}
