// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.federation.FederationSearcher;
import com.yahoo.search.federation.sourceref.SearchChainResolver;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.util.Collections;

/**
* @author Tony Vaagenes
*/
class FederationTester {

    SearchChainResolver.Builder builder = new SearchChainResolver.Builder();
    SearchChainRegistry registry = new SearchChainRegistry();

    Execution execution;

    void addSearchChain(String id, Searcher... searchers) {
        addSearchChain(id, federationOptions(), searchers);
    }

    void addSearchChain(String id, FederationOptions federationOptions, Searcher... searchers) {
        ComponentId searchChainId = ComponentId.fromString(id);

        builder.addSearchChain(searchChainId, federationOptions, Collections.<String>emptyList());

        Chain<Searcher> chain = new Chain<>(searchChainId, searchers);
        registry.register(chain);
    }

    public void addOptionalSearchChain(String id, Searcher... searchers) {
        addSearchChain(id, federationOptions().setOptional(true), searchers);
    }

    private FederationOptions federationOptions() {
        int preventTimeout = 24 * 60 * 60 * 1000;
        return new FederationOptions().setUseByDefault(true).setTimeoutInMilliseconds(preventTimeout);
    }

    FederationSearcher buildFederationSearcher() {
        return new FederationSearcher(ComponentId.fromString("federation"), builder.build());
    }

    public Result search() {
        return search(new Query());
    }

    public Result search(Query query) {
        execution = createExecution();
        return execution.search(query);
    }

    public Result searchAndFill() {
        Result result = search();
        fill(result);
        return result;
    }

    private Execution createExecution() {
        registry.freeze();
        return new Execution(new Chain<Searcher>(buildFederationSearcher()), Execution.Context.createContextStub(registry, null));
    }

    public void fill(Result result) {
        execution.fill(result, "default");
    }
}
