package org.jolokia.handler;

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
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.detector.ServerHandle;

import java.util.HashMap;
import java.util.Map;

/**
 * @author roland
 * @since Nov 13, 2009
 */
public class RequestHandlerManager {

    // Map with all json request handlers
    private static final Map<JmxRequest.Type, JsonRequestHandler> REQUEST_HANDLER_MAP =
            new HashMap<JmxRequest.Type, JsonRequestHandler>();

    public RequestHandlerManager(ObjectToJsonConverter pObjectToJsonConverter,
            StringToObjectConverter pStringToObjectConverter,
            ServerHandle pServerHandle, Restrictor pRestrictor) {
        JsonRequestHandler handlers[] = {
                new ReadHandler(pRestrictor),
                new WriteHandler(pRestrictor, pObjectToJsonConverter),
                new ExecHandler(pRestrictor, pStringToObjectConverter),
                new ListHandler(pRestrictor),
                new VersionHandler(pRestrictor, pServerHandle),
                new SearchHandler(pRestrictor)
        };
        for (JsonRequestHandler handler : handlers) {
            REQUEST_HANDLER_MAP.put(handler.getType(),handler);
        }
    }

    public JsonRequestHandler getRequestHandler(JmxRequest.Type pType) {
        JsonRequestHandler handler = REQUEST_HANDLER_MAP.get(pType);
        if (handler == null) {
            throw new UnsupportedOperationException("Unsupported operation '" + pType + "'");
        }
        return handler;
    }


}
