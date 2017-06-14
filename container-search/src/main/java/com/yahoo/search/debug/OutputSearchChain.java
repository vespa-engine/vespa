// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import static com.yahoo.protect.Validator.ensureNotNull;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Value;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;

/**
 * Outputs a human readable representation of a given search chain.
 *
 * @author tonytv
 */
final class OutputSearchChain implements DebugMethodHandler {
      private String getSearchChainName(Request request) {
        final int numParameters = request.parameters().size();

        if (numParameters == 0)
            return SearchHandler.defaultSearchChainName;
        else if (numParameters == 1)
            return request.parameters().get(0).asString();
        else
            throw new RuntimeException("Too many parameters given.");
    }

    private SearchChain getSearchChain(SearchChainRegistry registry, String searchChainName) {
        SearchChain searchChain = registry.getComponent(searchChainName);
        ensureNotNull("There is no search chain named '" + searchChainName + "'", searchChain);
        return searchChain;
    }

    public JrtMethodSignature getSignature() {
        String returnTypes = "" + (char)Value.STRING;
        String parametersTypes = "*"; //optional string
        return new JrtMethodSignature(returnTypes, parametersTypes);
    }

    public void invoke(Request request) {
        try {
            SearchHandler searchHandler = SearcherUtils.getSearchHandler();
            SearchChainRegistry searchChainRegistry = searchHandler.getSearchChainRegistry();
            SearchChain searchChain = getSearchChain(searchChainRegistry,
                    getSearchChainName(request));

            SearchChainTextRepresentation textRepresentation = new SearchChainTextRepresentation(searchChain, searchChainRegistry);
            request.returnValues().add(new StringValue(textRepresentation.toString()));
        } catch (Exception e) {
            request.setError(1000, Exceptions.toMessageString(e));
        }
    }


}

