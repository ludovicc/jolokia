package org.jolokia.backend;

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

import org.jolokia.JmxRequest;
import org.jolokia.config.Config;
import org.jolokia.config.DebugStore;
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.handler.*;
import org.jolokia.history.HistoryStore;
import org.json.simple.JSONObject;

import javax.management.*;
import java.io.IOException;
import java.util.List;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestDispatcher implements RequestDispatcher {

    // Handler for finding and merging the various MBeanHandler
    private MBeanServerHandler mBeanServerHandler;

    private RequestHandlerManager requestHandlerManager;

    // An (optional) qualifier for registering MBeans.
    private String qualifier;
    private ObjectToJsonConverter objectToJsonConverter;

    /**
     * Create a new local dispatcher which accesses local MBeans.
     *
     * @param pObjectToJsonConverter a serializer to JSON
     * @param pStringToObjectConverter a de-serializer for arguments
     * @param pRestrictor restrictor which checks the access for various operations
     * @param pQualifier optional qualifier for registering own MBean to allow for multiple J4P instances in the VM
     */
    public LocalRequestDispatcher(ObjectToJsonConverter pObjectToJsonConverter,
                                  StringToObjectConverter pStringToObjectConverter,
                                  Restrictor pRestrictor, String pQualifier) throws MalformedObjectNameException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        requestHandlerManager = new RequestHandlerManager(pObjectToJsonConverter,pStringToObjectConverter, pRestrictor);
        // Get all MBean servers we can find. This is done by a dedicated
        // handler object
        mBeanServerHandler = new MBeanServerHandler(pQualifier);
        qualifier = pQualifier;
        objectToJsonConverter = pObjectToJsonConverter;
    }

    // Can handle any request
    public boolean canHandle(JmxRequest pJmxRequest) {
        return true;
    }

    // The local dispatcher supports bulk requests, of course.
    public boolean supportsBulkRequests() {
        return true;
    }

    public JSONObject dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxReq.getType());
        Object retValue = mBeanServerHandler.dispatchRequest(handler, pJmxReq);
        return objectToJsonConverter.convertToJson(retValue, pJmxReq, handler.useReturnValueWithPath());
    }

    public List<JSONObject> dispatchRequests(List<JmxRequest> pJmxRequests) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        // TODO: Perform dispatching to request, but also error handling
        return null;
    }

    public void unregisterJolokiaMBeans() throws JMException {
        mBeanServerHandler.unregisterMBeans();
    }

    public void registerJolokiaMBeans(HistoryStore pHistoryStore, DebugStore pDebugStore) throws OperationsException {
        mBeanServerHandler.registerMBean(mBeanServerHandler, mBeanServerHandler.getObjectName());

        Config config = new Config(pHistoryStore,pDebugStore,qualifier);
        mBeanServerHandler.registerMBean(config, config.getObjectName());
    }
}
