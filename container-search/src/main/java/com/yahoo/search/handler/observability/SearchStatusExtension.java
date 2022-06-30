// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.container.handler.observability.ApplicationStatusHandler;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.SearchChainRegistry;

import java.util.Map;

/**
 * @author bjorncs
 */
public class SearchStatusExtension implements ApplicationStatusHandler.Extension {

    @Override
    public Map<String, ? extends JsonNode> produceExtraFields(ApplicationStatusHandler statusHandler) {
        return Map.of("searchChains", renderSearchChains(statusHandler));
    }

    private static JsonNode renderSearchChains(ApplicationStatusHandler statusHandler) {
        for (RequestHandler h : statusHandler.requestHandlers()) {
            if (h instanceof SearchHandler) {
                SearchChainRegistry scReg = ((SearchHandler) h).getSearchChainRegistry();
                return ApplicationStatusHandler.renderChains(scReg);
            }
        }
        return statusHandler.jsonMapper().createObjectNode();
    }

}
