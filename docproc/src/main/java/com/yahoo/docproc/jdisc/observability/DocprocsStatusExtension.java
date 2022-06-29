// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.handler.observability.ApplicationStatusHandler;
import com.yahoo.docproc.Call;
import com.yahoo.docproc.impl.DocprocService;
import com.yahoo.docproc.jdisc.DocumentProcessingHandler;
import com.yahoo.jdisc.handler.RequestHandler;

import java.util.Iterator;
import java.util.Map;


/**
 * @author bjorncs
 */
public class DocprocsStatusExtension implements ApplicationStatusHandler.Extension {

    @Override
    public Map<String, ? extends JsonNode> produceExtraFields(ApplicationStatusHandler statusHandler) {
        return Map.of("docprocChains", renderDocprocChains(statusHandler));
    }

    private static JsonNode renderDocprocChains(ApplicationStatusHandler statusHandler) {
        ObjectNode ret = statusHandler.jsonMapper().createObjectNode();
        for (RequestHandler h : statusHandler.requestHandlers()) {
            if (h instanceof DocumentProcessingHandler) {
                ComponentRegistry<DocprocService> registry = ((DocumentProcessingHandler) h).getDocprocServiceRegistry();
                for (DocprocService service : registry.allComponents()) {
                    ret.set(service.getId().stringValue(), renderCalls(statusHandler, service.getCallStack().iterator()));
                }
            }
        }
        return ret;
    }

    private static JsonNode renderCalls(ApplicationStatusHandler statusHandler, Iterator<Call> components) {
        ArrayNode ret = statusHandler.jsonMapper().createArrayNode();
        while (components.hasNext()) {
            Call c = components.next();
            JsonNode jc = ApplicationStatusHandler.renderComponent(c.getDocumentProcessor(), c.getDocumentProcessor().getId());
            ret.add(jc);
        }
        return ret;
    }

}
